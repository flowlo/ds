package chatserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

class ChatServerTCPThread implements Runnable {
	private Chatserver chatserver; 
	private ServerSocket tcp_socket;
	private ExecutorService tcp_threadPool;

	public ChatServerTCPThread(Chatserver chatserver, ServerSocket tcp_socket, ExecutorService tcp_threadPool){
		this.chatserver = chatserver;
		this.tcp_socket = tcp_socket;
		this.tcp_threadPool = tcp_threadPool;
	}

	public void run(){
		System.out.println("TCP_Thread started");
		Socket socket = null;

		while(!Thread.currentThread().isInterrupted()){
			try {
				try {
					socket = tcp_socket.accept();
				}catch(java.net.SocketException e){
					break;
				}
				tcp_threadPool.execute(new ChatServerClientHandler(socket, this.chatserver));
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
}

