package com.autoStation.serv.mongodb;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

public class ClientSide {

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		
		ArrayList<Integer> paymentData = new ArrayList<Integer>(); //object which contains user's data to sent to the server
		
		paymentData.add(1);/** user id
		                      This data can be obtained over System.in or etc
		                      For the testing purpose I used indexes between 1 and 9
		                    */
		
		
	new Thread(new Runnable(){ //creating a new thread for server
		public void run() {
			try {
				ServSide server = new ServSide();
				server.startServ();      //starting the server
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
	}).start();
		
		String host = "localhost"; //set your server address
		Socket socket = new Socket(host,9988);
		ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());// output stream for messaging the server side.
		out.writeObject(paymentData); //sent object to server
		ObjectInputStream in = new ObjectInputStream(socket.getInputStream());//getting answer from server
		String msgFromServer = (String)in.readObject();
		System.out.println(msgFromServer);
		socket.close();
        out.flush();
        out.close();
	}

}