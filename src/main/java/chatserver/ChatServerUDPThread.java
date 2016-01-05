package chatserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ChatServerUDPThread implements Runnable {
	
	private DatagramSocket udp_socket;
	private Chatserver server;
	
	
	public ChatServerUDPThread(Chatserver server, DatagramSocket udp_socket){
		this.udp_socket = udp_socket;
		this.server = server;
	}

	
	public void run(){
		byte[] buffer;
		String request;
		String response;
		
		while(!Thread.currentThread().isInterrupted()){
			buffer = new byte[1024];
			try {
			
				
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				udp_socket.receive(packet);
				request = new String(packet.getData(), 0, packet.getLength()).trim();
				
				if(request.equals("!list")){
					response = server.list();
				}else{
					response = "BAD request! " + request + " unknown command.";
				}
				
				InetAddress address = packet.getAddress();
				int port = packet.getPort();
				buffer = response.getBytes();
				packet= new DatagramPacket(buffer, buffer.length, address, port);
				udp_socket.send(packet);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
