import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;


public class Handler implements Runnable {
	
	Socket connectionSocket;
	
	public Handler(Socket clientSocket){
		connectionSocket = clientSocket;
	}

	@Override
	public void run() {
		BufferedReader inFromClient = new BufferedReader(new InputStreamReader (connectionSocket.getInputStream()));
		DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
		String clientSentence = inFromClient.readLine(); 
		System.out.println("Received: " + clientSentence); 
		String capsSentence = clientSentence.toUpperCase() + '\n'; outToClient.writeBytes(capsSentence); 
		
	}

}
