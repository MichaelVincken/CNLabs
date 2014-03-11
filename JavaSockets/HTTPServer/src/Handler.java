import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.Date;
import java.util.LinkedList;
import java.util.regex.*;


public class Handler implements Runnable {
	
	private Socket connectionSocket;
	private static Pattern cmdPattern = Pattern.compile("^(?<command>\\w*) (?<path>.*?) (?<httpVersion>HTTP/\\d\\.\\d)");
	private static Pattern headerPattern = Pattern.compile("^(.+?):[ \\t]*(.*)");
	private static Pattern headerPatternTail = Pattern.compile("^[ \\t]+(.+)");
	final static String WEBSITE_ROOT = "localhost";
	
	public Handler(Socket clientSocket){
		connectionSocket = clientSocket;
	}
	

	@Override
	public void run() {
		try{
			//Initial setup
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader (connectionSocket.getInputStream()));
			DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			
			System.out.println("Received: "); 
			
			//Process first line...
			String clientSentence = inFromClient.readLine(); 
			System.out.println(clientSentence);
			String[] properties = tokenizeProperties(clientSentence);
			
			//Process headers. (Although we're only interested in the Host header for now.)
			LinkedList<String[]> headers = collectHeaders(inFromClient);
			
			//Process body
			LinkedList<String> body = collectBody(inFromClient);
			
			//TODO: accept chunked data requests...
			
			//We now start building the response...
			String response = "";
			
			//HTTP/1.1 -> Process Host: header
			String domain = WEBSITE_ROOT;
			if(properties[2].equals("HTTP/1.1")){
				domain = getHostAddress(headers);
				if(domain == null){
					return400(outToClient,properties[2]);
					return;
				} else {
					//Accepting absolute URLs (small workaround)
					if(properties[1].startsWith("http://")){
						properties[1] = properties[1].split(domain)[1];
					}
				}
			}
			//Constructing local path
			Path filePath = FileSystems.getDefault().getPath(domain, properties[1]);
			
			//TODO:If-Modified-Since: or If-Unmodified-Since: Headers
			
			//Processing switch
			switch(properties[0]){
				case "GET":		if(Files.exists(filePath)){
									//Valid GET response.
									System.out.println("path exists");
									response = properties[2] + " 200 OK\n";
									response += getHead(filePath) + "\n\n";
									byte[] data = processGet(filePath);
									outToClient.writeBytes(response);
									outToClient.write(data);
									outToClient.writeBytes("\n\n");
									return;					
								} else {
									//404 Error
									System.out.println("lolnope");
									return404(outToClient,properties[2]);
									return;
								}
				case "HEAD":	if(Files.exists(filePath)){
									//Valid HEAD response
									response = properties[2] + " 200 OK\n";
									response += getHead(filePath) + "\n\n";
									outToClient.writeBytes(response);
									return;
								} else {
									//404 Error
									return404(outToClient,properties[2]);
									return;
								}
				case "PUT": 	;//TODO: PUT
				case "POST": 	;//TODO: POST
				default: response = properties[2] + " 501 Not Implemented\n\n";
			}
			
		}catch(IOException e){
			//TODO: ?
		}
		
	}
	
	/**
	 * Returns the file head.
	 * 
	 * @pre		File exists.
	 * @param 	filePath
	 * @return	HEAD response, except initial line. This way it can be used for GET & HEAD.
	 * @throws 	IOException
	 * 			It shouldn't, really...
	 */
	@SuppressWarnings("deprecation")
	private String getHead(Path filePath) throws IOException {
		
		Date now = new Date();
		String response = "Date: " + now.toString().substring(0, 3) + " " + now.toGMTString() + "\n";
		response += "Content-Type: " + Files.probeContentType(filePath) + "\n";
		response += "Content-Length: " + Files.size(filePath) + "\n";
		response += "Connection: close";
		
		return response;
	}
	
	/**
	 * Canned 404 response.
	 * 
	 * @param 	out
	 * 			Our mouthpiece.
	 * @param 	version
	 * 			HTTP/x.x
	 * @throws 	IOException
	 * 			If we can't transmit to the client.
	 */
	private void return404(DataOutputStream out, String version) throws IOException{
		String response = version + " 404 File not found\n"
				+ "Content-Type: text/html\n"
				+ "Content-Length: 56\n"
				+ "\n"
				+ "<html><body>\n"
				+ "<h2>404: File not found</h2>\n"
				+ "</body></html>\n\n";
		out.writeBytes(response);
		
	}
	
	/**
	 * Canned 400 response.
	 * 
	 * @param 	out
	 * 			Our mouthpiece.
	 * @param 	version
	 * 			HTTP/x.x
	 * @throws 	IOException
	 * 			If we can't transmit to the client.
	 */
	private void return400(DataOutputStream out, String version) throws IOException{
		String response = "HTTP/1.1 400 Bad Request\n"
				+ "Content-Type: text/html\n"
				+ "Content-Length: 111\n"
				+ "\n"
				+ "<html><body>\n"
				+ "<h2>No Host: header received</h2>\n"
				+ "HTTP 1.1 requests must include the Host: header.\n"
				+ "</body></html>\n\n";
		out.writeBytes(response);
		System.out.println("Finished writing");
	}

	/**
	 * Reads a file from disk.
	 * 
	 * @pre		file exists
	 * @param 	filePath
	 * @return	File contents as a byte array.
	 * @throws 	IOException
	 * 			If reading from disk fails.
	 */
	private byte[] processGet(Path filePath) throws IOException {
		return Files.readAllBytes(filePath);
	}

	/**
	 * Used in HTTP/1.1 compliance. Gets the host domain from the list of headers.
	 * 
	 * @param 	headers
	 * 			Array of headers.
	 * 			- First element contains the name of the header.
	 * 			- Second element contains the data.
	 * @return	The host address supplied in the headers, or null if there was none found.
	 */
	private String getHostAddress(LinkedList<String[]> headers) {
		for(String[] header : headers){
			if(header[0].equals("Host")){
				return header[1].split(":")[0];
			}
		}
		return null;
	}

	/**
	 * Reads the body from an HTTP request.
	 * 
	 * @param 	inFromClient
	 * @return	Body as a list of strings.
	 * @throws IOException
	 */
	private LinkedList<String> collectBody(BufferedReader inFromClient) throws IOException {
		LinkedList<String> body = new LinkedList<String>();
		
		while(inFromClient.ready()){
			String nextLine = inFromClient.readLine();
			System.out.println(nextLine);
			if(nextLine.equals("") || nextLine.equals("\n")) return body;
			body.add(nextLine);
		}
		return body;
	}

	/**
	 * Collects and partially tokenizes the headers, returning them in a Linked List of String arrays.
	 * 
	 * @param 	inFromClient
	 * @return	LinkedList of String array.
	 * 			- First element contains the name of the header.
	 * 			- Second element contains the data.
	 * @throws 	IOException
	 * 			If failing to read from the BufferedReader
	 */
	private LinkedList<String[]> collectHeaders(BufferedReader inFromClient) throws IOException {
		boolean moreHeaders = true;
		LinkedList<String[]> headers = new LinkedList<String[]>();
		
		while(inFromClient.ready() && moreHeaders){
			String header = inFromClient.readLine();
			System.out.println(header);
			Matcher m = headerPattern.matcher(header);
			// Does this match what a header looks like?
			if(m.matches()){
				String[] headerParsed = {m.group(1),m.group(2)};
				headers.add(headerParsed);
			}else{
				// If not, is it a newline continuation of the previous header?
				Matcher m2 = headerPatternTail.matcher(header);
				if(m2.matches()){
					headers.getLast()[1] += " " + header;
				} else {
					// If not, we've reached the end of the headers.
					moreHeaders = false;
				}
			}
		}
		return headers;
	}

	/**
	 * Tokenizes the properties.
	 * 
	 * @param 	request
	 * @return	Array with command, path and HTTP version.
	 */
	private String[] tokenizeProperties(String request) {
		Matcher m = cmdPattern.matcher(request);
		if(!m.matches()){
			//TODO: INCORRECT REQUEST!
			System.out.println("doesn't match!");
		}
		
		String command = m.group("command");
		String path = m.group("path");
		String httpVersion = m.group("httpVersion");
		
		String[] res = {command,path,httpVersion};
		
		return res;
	}

}
