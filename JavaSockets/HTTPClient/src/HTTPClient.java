
import java.io.*; 
import java.net.*;
import java.util.LinkedList;
import java.util.regex.*;

public class HTTPClient { 
	
	private static Pattern urlPattern = Pattern.compile("^(?<domain>[a-zA-Z.]*)(?:\\:(?<port>\\d*))?(?<path>/.*)?");
	private static Pattern imgPattern = Pattern.compile("<img.+?src=\"(.+?)\"");
	
	public static void main(String argv[]) throws Exception { 
		
		//String[] argv = {"GET", "www.robgendlerastropics.com/index.htm", "80", "HTTP/1.0"};
		
		LinkedList<String> images = new LinkedList<String>();
		
		// argv heeft de vorm [command, uri, port, httpVersion]
		if(argv.length != 4){
			throw new IllegalArgumentException("This client takes 4 arguments: "
											+ "(i.) A HTTP command {HEAD, GET, PUT, or POST} "
											+ "(ii.) A URI and (iii.) a	port. "
											+ "(iv.) The HTTP version (1.0 or 1.1.).");
		}
		
		String[] parsedUrl = parseUrl(argv[1],argv[2]);
		String domain = parsedUrl[0];
		int port = Integer.parseInt(argv[2]);
		System.out.println("gaan verbinden met... " + domain + port);
		Socket clientSocket = new Socket(domain, port);
		DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream()); 
		BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); 
		
		System.out.println(argv[0]);
		String request = createRequest(argv[0],argv[3],parsedUrl);
		
		System.out.println("sending..."); 
		
		outToServer.writeBytes(request + '\n');
		String modifiedSentence = inFromServer.readLine();
		//processServerReply(modifiedSentence);
		System.out.println("FROM SERVER:"); 
		System.out.println(modifiedSentence); 
		while(inFromServer.ready()){
			modifiedSentence = inFromServer.readLine();
			System.out.println(modifiedSentence);
			images.addAll(processServerReply(modifiedSentence));
		}
		
		// TODO download images
		
		clientSocket.close();
		
	}
	
	/**
	 * only extracts the images atm
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
	 * TODO: I think. will have to check specs in more detail.
	 * 
	 * @param command
	 * @param httpVersion
	 * @param urlTokenized
	 * @return
	 */
	private static String createRequest(String command, String httpVersion, String[] urlTokenized) {
		
		String req = command + " " + urlTokenized[2] + " " + httpVersion + "\n";
		if(httpVersion.equals("HTTP/1.1")){
			req += "Host: " + urlTokenized[0] + "\n";
		}
		
		if(command.equals("PUT") || command.equals("POST")){
			System.out.println("What data do you want to include? Enter a string below:");
			String sentence = getUserInput();
			System.out.println(sentence);
			req += "Content-Length: " + sentence.length() + "\n\n" + sentence + "\n";
		}
		//TODO: dit is om te debuggen.
		System.out.println("current query:");
		System.out.println(req);
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
	
	/**
	 * Takes one line of user input and returns it.
	 */
	public static String getUserInput(){
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		String res = "";
		try {
			res = inFromUser.readLine();
			inFromUser.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return res;
	}
	
} 
