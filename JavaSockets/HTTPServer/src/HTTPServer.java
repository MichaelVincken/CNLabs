import java.net.*; 




public class HTTPServer {

	public static void main(String[] args)  throws Exception { 
		if(args.length != 1){
			throw new IllegalArgumentException("Needs 1 argument: the port number.");
		}
		int portNumber = Integer.parseInt(args[0]);
		ServerSocket welcomeSocket = new ServerSocket(portNumber); 
		
		System.out.println("Listening to port " + portNumber +"...");
		
		while (true) { 
			Socket connectionSocket = welcomeSocket.accept();
			if(connectionSocket != null){
				Handler request = new Handler(connectionSocket);
				Thread thread = new Thread(request);
				thread.start();
			}
			

		}

	}

}
