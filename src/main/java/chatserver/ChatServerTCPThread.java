package chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

class ChatServerTCPThread implements Runnable {
	private Chatserver chatserver; 
	private ServerSocket tcp_socket;
	private ExecutorService tcp_threadPool;
	private ConcurrentHashMap<String, ChatSession> users;
	
	public ChatServerTCPThread(Chatserver chatserver, ServerSocket tcp_socket, ExecutorService tcp_threadPool, ConcurrentHashMap<String, ChatSession> users){
		this.chatserver = chatserver;
		this.tcp_socket = tcp_socket;
		this.tcp_threadPool = tcp_threadPool;
		this.users = users;
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
	
	/*class ClientHandler implements Runnable{
		private Socket socket;
		private BufferedReader reader;
		private PrintWriter writer;
		private ChatSession session = null;
		
		ClientHandler (Socket socket){
			this.socket = socket;
			
			try{
				reader = new BufferedReader(
						new InputStreamReader(socket.getInputStream()));
			
				writer = new PrintWriter(socket.getOutputStream());
			}catch(IOException e){
				System.out.println(e.getStackTrace().toString());
			}
			
			System.out.println("Client TCP Thread constructed");
		}
		
		public void run(){
			System.out.println("Client TCP Thread started");
			
			BufferedReader server_input = null;
			String response = null;
			
			
			try {
				String request;
				// read client requests
				while ((request = reader.readLine()) != null) {
					if(session==null || !session.isOnline()){
						if(request.startsWith("!login")){
							String[] split_request = request.split(" ");
							if(split_request.length==3){
								ChatSession candidate = users.get(split_request[1]);
								if(candidate!=null){
									if(candidate.isOnline()){
										System.out.println("User already logged in.");
									}else if(candidate.getPassword().equals(split_request[2])){
										session = candidate;
										candidate.setOnline(true);
										candidate.setSocket(socket);
										writer.println("Successfully logged in.");
									}else{
										writer.println("Wrong username or password.");
										System.out.println("bad request. wrong password.");
									}
								}else{
									writer.println("Wrong username or password.");
									System.out.println("bad request. wrong username.");
								}
							}else {
								System.out.println("bad request. wrong number of arguments");
							}	
						}else{
							System.out.println("bad request. no login -> no session");
						}
					}else if(request.startsWith("!login")){
						System.out.println("Bad request. User is already logged in.");
						writer.println("You are already logged in.");
					}else if(request.startsWith("!send")){
						this.sendToAll(request.substring(5, request.length()));
					}else if(request.startsWith("!logout")){
						this.session.setOnline(false);
						this.session.setSocket(null);
						writer.println("Successfully logged out.");
						
						System.out.println("logged out user " + session.getUsername());
						
						this.session = null;
						
						//TODO:users();
					}else if(request.startsWith("!register")) {
						this.session.setAddress(request.split(" ")[1]);
						writer.println("Successfully registered address for " + this.session.getUsername() + '.');
					}else if(request.startsWith("!lookup")){
						ChatSession session = users.get(request.split(" ")[1]);
						String address = session.getAddress();
						
						if(address != null){
							writer.println(address);
						}else {
							writer.println("No address for this user!");
						}
					}
					else{
					
						System.out.println("Unknown command.");
					}
					
					
					writer.flush();
					
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}*/
		
		private void sendToAll(String msg){
			for(String key:users.keySet()){
				
				PrintWriter writer = null;
				try {
					ChatSession session = users.get(key);
					
					if(!session.isOnline() || session.getSocket() == null){
						continue;
					}
					
					writer = new PrintWriter(session.getSocket().getOutputStream());
				} catch (IOException e) {
					System.out.println(e.getStackTrace().toString());
					continue;
				}
				
				writer.println(msg);
				writer.flush();
			}
		}
}

