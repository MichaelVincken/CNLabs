
import java.io.*; 
import java.net.*;
import java.util.LinkedList;
import java.util.regex.*;

public class HTTPClient { 
	
	private static Pattern urlPattern = Pattern.compile("^(?<domain>[a-zA-Z.]*)(?:\\:(?<port>\\d*))?(?<path>/.*)?");
	private static Pattern imgPattern = Pattern.compile("<img.+?src=\"(.+?)\"");
	
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
		Socket clientSocket = new Socket(domain, port);
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
		
		// TODO download images
		
		// Finish up
		clientSocket.close();
		
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
			images.add(m.group(1));
			System.out.println(m.group(1));
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
