import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader (connectionSocket.getInputStream()));
			DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			
			System.out.println("Received: "); 
			
			//process first line ...
			String clientSentence = inFromClient.readLine(); 
			System.out.println(clientSentence);
			String[] properties = tokenizeProperties(clientSentence);
			
			//process headers. We don't do anything with them right now.
			LinkedList<String[]> headers = collectHeaders(inFromClient);
			
			//process body
			String body = collectBody(inFromClient);
			
			
			// We now start building the response...
			String response = "";
			
			//HTTP/1.1
			String domain = WEBSITE_ROOT;
			if(properties[2].equals("HTTP/1.1")){
				domain = getHostAddress(headers);
				if(domain == null){
					response = "HTTP/1.1 400 Bad Request\n"
							+ "Content-Type: text/html\n"
							+ "Content-Length: 111\n"
							+ "\n"
							+ "<html><body>\n"
							+ "<h2>No Host: header received</h2>"
							+ "HTTP 1.1 requests must include the Host: header."
							+ "</body></html>";
					outToClient.writeBytes(response);
					return;
				}
			}
			Path filePath = FileSystems.getDefault().getPath(domain, properties[1]);
			
			System.out.println("We zoeken in " + filePath.toString());
			
			switch(properties[0]){
				case "GET":		if(Files.exists(filePath)){
									response = properties[2] + " 200 OK\n";
									response += getHead(filePath) + "\n\n";
									byte[] data = processGet(filePath);
									outToClient.writeBytes(response);
									outToClient.write(data);
									outToClient.writeBytes("\n\n");
									return;					
								} else {
									outToClient.writeBytes(properties[2] + " 404 File not found\n\n");
									return;
								}
				case "HEAD":	if(Files.exists(filePath)){
									response = properties[2] + " 200 OK\n";
									response += getHead(filePath) + "\n\n";
									outToClient.writeBytes(response);
									return;
								} else {
									outToClient.writeBytes(properties[2] + " 404 File not found\n\n");
									return;
								}
				case "PUT": ;
				case "POST": ;
				default: response = properties[2] + " 501 Not Implemented";
			}
			
			/*
			System.out.println("Received: ");
			System.out.println("Command: " + properties[0] + "; Path: " + properties[1] + "; Version: " + properties[2]);
			System.out.println("Headers...");
			for(String[] h : headers){
				System.out.println("Name: " + h[0] + "; Data: " + h[1]);
			}
			System.out.println("Body:");
			System.out.println(body);
			System.out.println("endofbody");
			*/
			
			
			String capsSentence = clientSentence.toUpperCase() + '\n';
			outToClient.writeBytes(capsSentence);
		}catch(IOException e){
			//
		}
		
	}
	
	@SuppressWarnings("deprecation")
	private String getHead(Path filePath) throws IOException {
		
		Date now = new Date();
		String response = "Date: " + now.toString().substring(0, 3) + " " + now.toGMTString() + "\n";
		response += "Content-Type: " + Files.probeContentType(filePath) + "\n";
		response += "Content-Length: " + Files.size(filePath);
		
		return response;
	}


	/**
	 * returns the file contents
	 */
	private byte[] processGet(Path filePath) throws IOException {
		return Files.readAllBytes(filePath);
	}


	private String getHostAddress(LinkedList<String[]> headers) {
		for(String[] header : headers){
			if(header[0].equals("Host")){
				return header[1];
			}
		}
		return null;
	}

	private String collectBody(BufferedReader inFromClient) throws IOException {
		
		String body = "";
		
		while(inFromClient.ready()){
			if(!body.equals("")) body += "\n";
			String nextLine = inFromClient.readLine();
			System.out.println(nextLine);
			if(nextLine.equals("") || nextLine.equals("\n")) return body;
			body += nextLine;
		}
		return body;
	}


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


	private String[] tokenizeProperties(String request) {
		
		Matcher m = cmdPattern.matcher(request);
		if(!m.matches()){
			//INCORRECT REQUEST!
		}
		
		String command = m.group("command");
		String path = m.group("path");
		String httpVersion = m.group("httpVersion");
		
		String[] res = {command,path,httpVersion};
		
		return res;
	}

}
