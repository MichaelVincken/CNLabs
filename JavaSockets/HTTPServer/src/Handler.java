import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.regex.*;


public class Handler implements Runnable {

	private Socket connectionSocket;
	private Calendar birthdate;
	private String httpVersion;
	private boolean lastRequest = true;
	private ConcurrencyController controller;
	private static Pattern cmdPattern = Pattern.compile("^(?<command>\\w*) (?<path>.*?) (?<httpVersion>HTTP/\\d\\.\\d)");
	private static Pattern headerPattern = Pattern.compile("^(.+?):[ \\t]*(.*)");
	private static Pattern headerPatternTail = Pattern.compile("^[ \\t]+(.+)");
	final static String WEBSITE_ROOT = "localhost";
	final static String LOG_FILENAME = "put-post-log.txt";
	public Handler(Socket clientSocket, ConcurrencyController cont, Calendar justNow){
		connectionSocket = clientSocket;
		birthdate = justNow;
		controller = cont;
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
			httpVersion = properties[2];

			//Process headers. (Although we're only interested in the Host header for now.)
			LinkedList<String[]> headers = collectHeaders(inFromClient);

			//Process body
			LinkedList<String> body = collectBody(inFromClient);

			//We now start building the response...
			String response = "";

			//HTTP/1.1 -> Process Host: header
			String domain = WEBSITE_ROOT;
			if(properties[2].equals("HTTP/1.1")){
				domain = getHostAddress(headers);
				lastRequest = isConnectionClose(headers);
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
			//Replacing spaces in path and fixing rel paths
			properties[1] = properties[1].replaceAll("%20", " ");
			if(properties[1].startsWith("./")){
				properties[1] = properties[1].substring(1);
			}

			//Constructing local path and log
			Path filePath = FileSystems.getDefault().getPath(domain, properties[1]);

			//Processing switch
			switch(properties[0]){
				case "GET":		if(Files.exists(filePath)){
									//Valid GET response.
									response = properties[2] + " 200 OK\n";
									response += getHead(filePath) + "\n\n";
									byte[] data = processGet(filePath);
									waitYourTurn();
									outToClient.writeBytes(response);
									outToClient.write(data);
									outToClient.writeBytes("\n\n");
									if(httpVersion.equals("HTTP/1.1") && lastRequest) connectionSocket.close();
									return;					
								} else {
									//404 Error
									return404(outToClient,properties[2]);
									return;
								}
				case "HEAD":	if(Files.exists(filePath)){
									//Valid HEAD response
									response = properties[2] + " 200 OK\n";
									response += getHead(filePath) + "\n\n";
									waitYourTurn();
									outToClient.writeBytes(response);
									if(httpVersion.equals("HTTP/1.1") && lastRequest) connectionSocket.close();
									return;
								} else {
									//404 Error
									return404(outToClient,properties[2]);
									return;
								}
				case "PUT": 	Files.write(filePath, body, Charset.defaultCharset());
								if(writePutPostLog("PUT",filePath.toString(),domain+"/"+LOG_FILENAME,properties[2],body)){
									//Valid PUT response
									response = properties[2] + " 200 Data written\n";
									response += getHead(filePath) + "\n\n";
									waitYourTurn();
									outToClient.writeBytes(response);
									if(httpVersion.equals("HTTP/1.1") && lastRequest) connectionSocket.close();
									return;
								} else {
									//500 Error
									return500(outToClient,properties[2]);
								}

				case "POST": 	if(writePutPostLog("POST",filePath.toString(),domain+"/"+LOG_FILENAME,properties[2],body)){
									//Valid POST response
									response = properties[2] + " 200 Data posted\n";
									response += getHead(filePath) + "\n\n";
									waitYourTurn();
									outToClient.writeBytes(response);
									if(httpVersion.equals("HTTP/1.1") && lastRequest) connectionSocket.close();
									return;
								}else{
									//500 error
									return500(outToClient,properties[2]);
								}
				default: response = properties[2] + " 501 Not Implemented\n\n";
			}

		}catch(IOException e){
			//TODO: ?
		}catch(IllegalMonitorStateException e){
			//
		}

	}

	/**
	 * Check periodically with the concurrency controller whether we can send our data.
	 * If not, puts the thread to sleep for 100ms. Returns true when we get the green light.
	 * 
	 * @return	true
	 * 			when the concurrency controller gives us the green light.
	 */
	private boolean waitYourTurn(){
		// No concurrency or connection persistence in HTTP/1.0
		if(httpVersion.equals("HTTP/1.0")) return true;

		// Otherwise we check if it's our turn.
		while(!controller.isNext(birthdate)){
			// If not, sleep for a spell.
			try {
				birthdate.wait(100);
			} catch (InterruptedException e) {
				//
			}
		}
		return true;
	}

	/**
	 * Writes the received PUT or POST request to the log in the correct directory,
	 * depending on the Host Name: header. Returns  true if succeeded, false if
	 * met with an IOException.
	 *
	 * @return	boolean:
	 * 			true if succeed
	 * 			false if write error
	 */
	private boolean writePutPostLog(String command, String directed, String path, String version, LinkedList<String> body){

		try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(path, true)))) {
		    out.println("Received request: " + command + ", directed to " + directed + ", over " + version);
		    out.print("Body: ");
			for(String line : body){
		    	out.println(line);
		    }
		    out.println();
		    return true;
		}catch (IOException e) {
		    return false;
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
		response += "Content-Length: " + Files.size(filePath);
		if(httpVersion.equals("HTTP/1.1)")) response += "Connection: close\n";

		return response;
	}

	/**
	 * Canned 500 response.
	 * 
	 * @param 	out
	 * 			Our mouthpiece.
	 * @param 	version
	 * 			HTTP/x.x
	 * @throws 	IOException
	 * 			If we can't transmit to the client.
	 */
	private void return500(DataOutputStream out, String version) throws IOException{
		String response = version + " 500 Server error\n"
				+ "Content-Type: text/html\n"
				+ "Content-Length: 52\n";
				if(version.equals("HTTP/1.1)")) response += "Connection: close\n";
		response+= "\n"
				+ "<html><body><h2>500: Server Error</h2></body></html>";
		waitYourTurn();
		out.writeBytes(response);
		controller.release();

		if(version.equals("HTTP/1.1") && lastRequest) connectionSocket.close();
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
				+ "Content-Length: 56\n";
				if(version.equals("HTTP/1.1)")) response += "Connection: close\n";
		response+= "\n"
				+ "<html><body>\n"
				+ "<h2>404: File not found</h2>\n"
				+ "</body></html>\n\n";
		waitYourTurn();
		out.writeBytes(response);
		controller.release();

		if(version.equals("HTTP/1.1") && lastRequest) connectionSocket.close();
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
				+ "Content-Length: 111\n";
		if(version.equals("HTTP/1.1)")) response += "Connection: close\n";
		response+= "\n"
				+ "<html><body>\n"
				+ "<h2>No Host: header received</h2>\n"
				+ "HTTP 1.1 requests must include the Host: header.\n"
				+ "</body></html>\n\n";
		waitYourTurn();
		out.writeBytes(response);
		controller.release();

		if(version.equals("HTTP/1.1") && lastRequest) connectionSocket.close();
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
	 * Used in HTTP/1.1 compliance. Checks if the headers include a Connection: close header.
	 * 
	 * @param 	headers
	 * 			Array of headers.
	 * 			- First element contains the name of the header.
	 * 			- Second element contains the data.
	 * @return	true if the headers include a Connection: close header, false otherwise.
	 */
	private boolean isConnectionClose(LinkedList<String[]> headers) {
		for(String[] header : headers){
			if(header[0].equals("Connection") && header[1].equals("close")){
				return true;
			}
		}
		return false;
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