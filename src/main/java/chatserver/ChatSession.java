package chatserver;

import java.net.Socket;

public class ChatSession {
	private Socket socket;
	private String username;
	private String password;
	private boolean online;
	private String address;

	public ChatSession(Socket socket){
		this.socket = socket;
	}

	public ChatSession() {
		// TODO Auto-generated constructor stub
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}


	public Socket getSocket() {
		return socket;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public boolean isOnline(){
		return online;
	}

	public void setOnline(boolean online){
		this.online = online;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getState(){
		if(this.isOnline()){
			return "online";
		}else{
			return "offline";
		}
	}


}