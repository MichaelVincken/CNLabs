
import java.io.*; 
import java.net.*;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.regex.*;

public class HTTPClient { 
	
	private static String httpVersion;
	private static String domain;
	private static int port;
	private static Pattern urlPattern = Pattern.compile("^(?<domain>[a-zA-Z.]*)(?:\\:(?<port>\\d*))?(?<path>/.*)?");
	private static Pattern imgPattern = Pattern.compile("<img.+?src=\"(.+?)\"");
	private static Pattern headerPattern = Pattern.compile("^(.+?):[ \\t]*(.*)");
	private static Socket clientSocket;
	
	public static void main(String argv[]) throws Exception { 
		
		LinkedList<String> images = new LinkedList<String>();
		
		// argv heeft de vorm [command, uri, port, httpVersion]
		if(argv.length != 4){
			throw new IllegalArgumentException("This client takes 4 arguments: COMMAND, URI, PORT, VERSION.");
		}
		
		// Parsing URL
		String[] parsedUrl = parseUrl(argv[1],argv[2]);
		domain = parsedUrl[0];
		httpVersion = argv[3];
		port = Integer.parseInt(argv[2]);
		
		// Initial Set-up
		clientSocket = new Socket(domain, port);
		InputStream serverInputStream = clientSocket.getInputStream();
		DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream()); 
		//BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); 
		
		// Create request
		String request = createRequest(argv[0],argv[3],parsedUrl);
		
		// Sending request
		outToServer.writeBytes(request + "\n\n");
		
		// Polling response
		String modifiedSentence = readNexLine(serverInputStream);
		
		// Process response
		System.out.println("FROM SERVER:"); 
		System.out.println(modifiedSentence);
		
		// Get headers and watch for content length
		int contentLength = -1;
		while(!modifiedSentence.equals("\n") && !modifiedSentence.equals("")){
			modifiedSentence = readNexLine(serverInputStream);
			System.out.println(modifiedSentence);
			if(modifiedSentence.startsWith("Content-Length")){
				String[] headerSplit = modifiedSentence.split(" ");
				String contentLengthStr = headerSplit[headerSplit.length - 1];
				
				char[] nums = {'0','1','2','3','4','5','6','7','8','9','n'};
				test:for(char c : nums){
					if(c == 'n'){
						contentLengthStr = contentLengthStr.substring(0, contentLengthStr.length() -1 );
						break;
					}
					if(c == contentLengthStr.charAt(contentLengthStr.length() - 1)) break test;
				}
				contentLength = Integer.parseInt(contentLengthStr);
			}
		}
		
		while(contentLength > 0){
			//We're receiving content!
			modifiedSentence = readNexLine(serverInputStream);
			System.out.println(modifiedSentence);
			images.addAll(processServerReply(modifiedSentence,outToServer));
			contentLength -= (modifiedSentence.length() + 1);
		}
		
		//Remove junk.

		//inFromServer.close();
		
		// download images
		for(String url : images){
			//HTTP/1.0 means we open a new connection for every transfer...
			if(httpVersion.equals("HTTP/1.0")){
				//close
				serverInputStream.close();
				clientSocket.close();
				outToServer.close();
				//open
				clientSocket = new Socket(domain, port);
				outToServer = new DataOutputStream(clientSocket.getOutputStream()); 
				serverInputStream = clientSocket.getInputStream();
				
				//We'll only place the request now...
				outToServer.writeBytes("GET " + url + " HTTP/1.0\n\n");
			}
			
			String nextLine = readNexLine(serverInputStream);
			while(nextLine.equals("\n") || nextLine.equals("")){
				nextLine = readNexLine(serverInputStream);
			}
			
			if(nextLine.contains("200")){
				System.out.println("File found. Beginning download now...");
				contentLength = 0;
				while(!nextLine.equals("") && !nextLine.equals("\n")){
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
		}
		
		serverInputStream.close();
		clientSocket.close();
		outToServer.close();
		
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
		byte[] buffer = new byte[4096];
		while(contentLength >= 4096){
			inputStream.read(buffer);
			fos.write(buffer);
			contentLength -= 4096;
		}
		// We stop when there's less than 4KB remaining in the content area.
		if(contentLength > 0){
			// Make a new array to carry the last bunch of bytes.
			byte[] rest = new byte[contentLength];
			inputStream.read(rest);
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
	 * @param outToServer 
	 * @return	LinkedList of image URLs
	 * @throws IOException 
	 */
	private static LinkedList<String> processServerReply(String reply, DataOutputStream outToServer) throws IOException {
		LinkedList<String> images = new LinkedList<String>();
		Matcher m = imgPattern.matcher(reply);
		while(m.find()){
			String i = m.group(1);
			if(i.startsWith("./")) i = i.substring(1);
			images.add(i);
			
			if(httpVersion.equals("HTTP/1.1")){

				//We send the requests already!
				outToServer.writeBytes("GET " + i + " " + httpVersion + "\nHost: " + domain + ":" + port + "\n\n");
			}
			
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
			//req += "Connection: close\n";
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
