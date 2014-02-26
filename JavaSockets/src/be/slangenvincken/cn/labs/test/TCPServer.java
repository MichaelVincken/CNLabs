package be.slangenvincken.cn.labs.test;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

class TCPServer {
	public static void main(String argv[]) throws Exception {
		ServerSocket welcomeSocket = new ServerSocket(6789);
		while (true) {
			Socket connectionSocket = welcomeSocket.accept();
			BufferedReader inFromClient = new BufferedReader(
					new InputStreamReader(connectionSocket.getInputStream()));
			DataOutputStream outToClient = new DataOutputStream(
					connectionSocket.getOutputStream());
			String clientSentence = inFromClient.readLine();
			System.out.println("Received: " + clientSentence);
			String capsSentence = clientSentence.toUpperCase() + '\n';
			outToClient.writeBytes(capsSentence);
		}
	}
}