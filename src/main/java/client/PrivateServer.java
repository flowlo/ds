package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import datatransfer.MsgDTO;

public class PrivateServer implements Runnable {

	private ServerSocket serverSocket;

	private Client client;

	private int port = 0;
	private String address = "";

	public PrivateServer(String address, Client client){
		String[] input = address.split(":");

		this.port = Integer.parseInt(input[1]);
		this.address = input[0];

		this.client = client;
	}

	public void run(){
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(this.address);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		try {
			this.serverSocket = new ServerSocket(port, 1024, addr);
		} catch (IOException e) {
			e.printStackTrace();
		}


		System.out.println("TCP_Thread started");
		Socket socket = null;

		while(!Thread.currentThread().isInterrupted()){
			try {
				socket = serverSocket.accept();
				client.getTCPThreadPool().execute(new ClientHandler(socket, this.client));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if(socket!=null && !socket.isClosed()){
			try {
				socket.close();
			} catch (IOException e) {
				System.out.println("Failed to close socket");
			}
		}

		Thread.currentThread().interrupt();

	}

	class ClientHandler implements Runnable {
		private ObjectInputStream input;
		private Client client;

		ClientHandler (Socket socket, Client client){
			this.client = client;

			try {
				this.input = new ObjectInputStream(socket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}

			System.out.println("Incoming Handler for private message established.");
		}


		public void run(){
			Object o;
			try {
				while ((o = input.readObject()) != null) {
					if(o instanceof MsgDTO){
						this.client.getShell().writeLine(((MsgDTO) o).getMessage());
					};
				}
			}catch(IOException e){
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}	
}

