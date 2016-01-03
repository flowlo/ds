package chatserver;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

import datatransfer.ErrorResponseDTO;
import datatransfer.LoggedInDTO;
import datatransfer.LoggedOutDTO;
import datatransfer.LoginDTO;
import datatransfer.LogoutDTO;
import datatransfer.LookedUpDTO;
import datatransfer.LookupDTO;
import datatransfer.RegisterDTO;
import datatransfer.RegisteredDTO;
import datatransfer.SendDTO;
import datatransfer.SentDTO;
import util.AbstractHandler;

public class ChatServerClientHandler extends AbstractHandler  implements Runnable{
	private ChatSession session;
	private Chatserver server; 



	public ChatServerClientHandler(Socket socket, Chatserver server) {
		super(socket);

		this.server = server;
		this.session = null;
	}


	public String processLogin(LoginDTO dto){
		ChatSession candidate = this.server.getUsers().get(dto.getUsername());

		if(candidate == null || !candidate.getPassword().equals(dto.getPassword())){
			return "[[ Login unsuccessful. ]]";
		}

		session = candidate;
		candidate.setOnline(true);
		candidate.setSocket(socket);

		return "[[ Successfully logged in. ]]";
	}

	public void processSend(SendDTO dto){
		ConcurrentHashMap<String, ChatSession> users = this.server.getUsers();

		for(String key:users.keySet()){


			ChatSession session = users.get(key);

			if(!session.isOnline() || session.getSocket() == null || session.getUsername().equals(this.session.getUsername())){
				continue;
			}

			ObjectOutputStream output = null;

			try {
				output = new ObjectOutputStream(session.getSocket().getOutputStream());
				output.writeObject(new SentDTO(this.session.getUsername() + ": " + dto.getMessage()));
				output.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	public String processRegister(RegisterDTO dto){
		this.session.setAddress(dto.getAddress());
		return "[[ Successfully registered address for " + this.session.getUsername() + ". ]]";
	}

	public String processLookup(LookupDTO dto){
		ChatSession session = this.server.getUsers().get(dto.getUsername());


		String address = null;

		if(session != null && (address=session.getAddress()) !=null){
			return address;
		}else {
			return "[[ No address for this user! ]]";
		}
	}

	public String processLogout(LogoutDTO dto){
		this.session.setOnline(false);
		this.session.setSocket(null);

		return "[[ Successfully logged out. ]]";

	}


	@Override
	public void run() {

		ObjectInputStream input = null;
		ObjectOutputStream output = null;


		Object o;

		try{

			while(!Thread.currentThread().isInterrupted()){
				try {
					input = new ObjectInputStream(socket.getInputStream());
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					output = new ObjectOutputStream(socket.getOutputStream());
				} catch (IOException e1) {
					e1.printStackTrace();
				}


				o=input.readObject();

				if(this.session==null||!this.session.isOnline()){
					if (o instanceof LoginDTO){
						output.writeObject(new LoggedInDTO((LoginDTO)o, this.processLogin((LoginDTO)o)));
					}else {
						output.writeObject(new ErrorResponseDTO("[[ Login first. ]]"));
					}
				}else{
					if (o instanceof LoginDTO){
						output.writeObject(new LoggedInDTO((LoginDTO)o, "[[ You are already logged in! ]]"));
					}
					else if(o instanceof SendDTO){
						output.writeObject(null);
						this.processSend((SendDTO)o);
					}
					else if(o instanceof RegisterDTO){
						output.writeObject(new RegisteredDTO((RegisterDTO) o, this.processRegister((RegisterDTO)o)));
					}
					else if(o instanceof LookupDTO){
						output.writeObject(new LookedUpDTO((LookupDTO) o, this.processLookup((LookupDTO)o)));
					}
					else if(o instanceof LogoutDTO){
						output.writeObject(new LoggedOutDTO((LogoutDTO) o, this.processLogout((LogoutDTO)o)));
					}
				}
				output.flush();
			}
		}catch(IOException e){
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

}
