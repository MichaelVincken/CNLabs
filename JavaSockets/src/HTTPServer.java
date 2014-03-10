import java.io.*; 
import java.net.*; 




public class HTTPServer {

	public static void main(String[] args)  throws Exception { 
		ServerSocket welcomeSocket = new ServerSocket(6789); 
		
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
