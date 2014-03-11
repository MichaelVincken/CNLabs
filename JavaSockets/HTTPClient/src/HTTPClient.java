
import java.io.*; 
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.regex.*;

public class HTTPClient { 
	
	private static Pattern urlPattern = Pattern.compile("^(?<domain>[a-zA-Z.]*)(?:\\:(?<port>\\d*))?(?<path>/.*)?");
	private static Pattern imgPattern = Pattern.compile("<img.+?src=\"(.+?)\"");
	private static Socket clientSocket;
	
	public static void main(String argv[]) throws Exception { 
		
		LinkedList<String> images = new LinkedList<String>();
		
		// argv heeft de vorm [command, uri, port, httpVersion]
		if(argv.length != 4){
			throw new IllegalArgumentException("This client takes 4 arguments: COMMAND, URI, PORT, VERSION.");
		}
		
		// Parsing URL
		String[] parsedUrl = parseUrl(argv[1],argv[2]);
		String domain = parsedUrl[0];
		int port = Integer.parseInt(argv[2]);
		
		// Initial Set-up
		clientSocket = new Socket(domain, port);
		DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream()); 
		BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); 
		
		// Create request
		String request = createRequest(argv[0],argv[3],parsedUrl);
		
		// Sending request
		outToServer.writeBytes(request + "\n\n");
		
		// Polling response
		String modifiedSentence = inFromServer.readLine();
		
		System.out.println("processing for request");
		
		// Process response
		System.out.println("FROM SERVER:"); 
		System.out.println(modifiedSentence); 
		while(inFromServer.ready()){
			modifiedSentence = inFromServer.readLine();
			System.out.println(modifiedSentence);
			images.addAll(processServerReply(modifiedSentence));
		}

		inFromServer.close();
		
		// download images
		for(String url : images){
			clientSocket.close();
			outToServer.close();
			
			clientSocket = new Socket(domain, port);
			outToServer = new DataOutputStream(clientSocket.getOutputStream()); 
			InputStream serverInputStream = clientSocket.getInputStream();
			
			outToServer.writeBytes("GET " + url + " HTTP/1.0\n\n");
			
			String nextLine = readNexLine(serverInputStream);
			
			if(nextLine.contains("200")){
				System.out.println("File found. Beginning download now...");
				int contentLength = 0;
				while(!nextLine.equals("")){
					nextLine = readNexLine(serverInputStream);
					if(nextLine.startsWith("Content-Length:")){
						String[] temp = nextLine.split(" ");
						contentLength = Integer.parseInt(temp[temp.length-1]);
					}
				}
				downloadimage(url, contentLength, serverInputStream);
			}else{
				System.out.println("Something went wrong. Message received:\n");
				System.out.println(nextLine);
			}
			
			serverInputStream.close();
		}
		outToServer.close();
		clientSocket.close();
		
	}

	private static String readNexLine(InputStream serverInputStream) throws IOException {
		char nextChar = (char) serverInputStream.read();
		String res = "";
		while(nextChar != '\n'){
			res += nextChar;
			nextChar = (char) serverInputStream.read();
		}
		return res;
	}

	private static void downloadimage(String url, int contentLength, InputStream inputStream) throws IOException {
		// We have to check if the file exists.
		// If it does, delete it. (Dirty but simple way to clear it.)
		// Then create a new file.
		String[] urlSplit = url.split("/");
		url = urlSplit[urlSplit.length - 1 ];
		
		File f = new File(url);
		if(f.exists()){
			f.delete();
			File newF = new File(url);
			newF.createNewFile();
		} else {
			f.createNewFile();
		}
		System.out.println("wehaveprettyfile");
		FileOutputStream fos = new FileOutputStream(url, true);
		// We buffer the image in batches of 4KB. Less than 1KB would be a waste, but
		// we don't want to go higher than the CPU's L1 cache. 4KB is a pretty safe
		// middle ground.
		System.out.println("wehaveOS");
		byte[] buffer = new byte[4096];
		System.out.println("wehavebuffer and CL " + contentLength);
		while(contentLength >= 4096){
			System.out.println("filling buffer");
			inputStream.read(buffer);
			System.out.println("writing buffer");
			fos.write(buffer);
			contentLength -= 4096;
		}
		System.out.println("wefinishwhile");
		// We stop when there's less than 4KB remaining in the content area.
		if(contentLength > 0){
			// Make a new array to carry the last bunch of bytes.
			byte[] rest = new byte[contentLength];
			System.out.println("wefillrest");
			inputStream.read(rest);
			System.out.println("wewriterest");
			fos.write(rest);
		}
		// Tidy up and release resources.
		fos.close();
		
	}

	/**
	 * Takes a line of HTML and returns a linked list of URLs to the embedded images.
	 * 
	 * @param	reply
	 * 			Line of HTML
	 * @return	LinkedList of image URLs
	 */
	private static LinkedList<String> processServerReply(String reply) {
		LinkedList<String> images = new LinkedList<String>();
		Matcher m = imgPattern.matcher(reply);
		while(m.find()){
			String i = m.group(1);
			if(i.startsWith("./")) i = i.substring(1);
			images.add(i);
		}
		return images;
		
	}

	/**
	 * Builds a request. Minimal HTTP/1.0 and HTTP/1.1 implementation.
	 * TODO: Does not yet support pipelining.
	 * 
	 * @param command
	 * @param httpVersion
	 * @param urlTokenized
	 * @return
	 * @throws IOException 
	 */
	private static String createRequest(String command, String httpVersion, String[] urlTokenized) throws IOException {
		
		String req = command + " " + urlTokenized[2] + " " + httpVersion + "\n";
		if(httpVersion.equals("HTTP/1.1")){
			req += "Host: " + urlTokenized[0] + ":" + urlTokenized[1] + "\n";
			//TODO: Disables persistent connections for now...
			req += "Connection: close\n";
		}
		
		if(command.equals("PUT") || command.equals("POST")){
			BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
			
			System.out.println("What's the content type of the data you want to include?");
			System.out.println("e.g. \"text/plain\", \"text/html\" or \"application/x-www-form-urlencoded\"");
			// Currently ignored in server. Also not checked for validity.
			String mime = inFromUser.readLine();
			System.out.println("Enter a string to include in the body:");
			String sentence = inFromUser.readLine();
			inFromUser.close();
			System.out.println(sentence);
			req += "Content-Type: " + mime + "\n";
			req += "Content-Length: " + sentence.length() + "\n\n" + sentence;
		}
		return req;
	}

	/**
	 * Splits a string URL into tokens: domain, port and path.
	 */
	public static String[] parseUrl(String urlString, String extPort){
		Matcher m = urlPattern.matcher(urlString);
		if(!m.matches()){
			throw new IllegalArgumentException("Not a valid URL");
		}
		
		String domain = m.group("domain");
		
		String port = m.group("port"); //null, if there is no port in the url. Default case.
		if(port == null){
			port = extPort;
		} else if(!port.equals(extPort)){ //If there IS a port in the url, e.g. google.com:[portnumber], needs to be equal to the one supplied in the arguments.
			throw new IllegalArgumentException("Two DIFFERENT ports supplied in the argument. One inherent to the uri, one as a separate argument.");
		}
		
		String path = m.group("path");
		if(path == null) path = "/";
		
		String[] res = {domain,port,path};
		return res;
	}
	
} 
