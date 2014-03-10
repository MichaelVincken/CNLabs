package be.slangenvincken.cn.labs.test;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;

class TCPClient {
	public static void main(String argv[]) throws Exception {
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(
				System.in));
		Socket clientSocket = new Socket("173.194.45.63", 80);
		DataOutputStream outToServer = new DataOutputStream(
				clientSocket.getOutputStream());
		BufferedReader inFromServer = new BufferedReader(new InputStreamReader(
				clientSocket.getInputStream()));
		String sentence = inFromUser.readLine();
		outToServer.writeBytes(sentence + "\n\n");
		
		String senteceFromServer;
		while((senteceFromServer = inFromServer.readLine()) != null) {
			System.out.println(senteceFromServer);
		}
		clientSocket.close();
	}
}
