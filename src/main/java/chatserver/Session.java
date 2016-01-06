package chatserver;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;

import dto.LoggedOutDTO;
import dto.LogoutDTO;
import dto.LookedUpDTO;
import dto.LookupDTO;
import dto.RegisterDTO;
import dto.RegisteredDTO;
import dto.SendDTO;
import dto.SentDTO;
import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

public class Session implements Runnable {
	private final Chatserver server;
	private final User user;
	private ObjectInputStream ois;
	private ObjectOutputStream oos;
	private final INameserverForChatserver rootNameserver;

	public Session(Chatserver server, User user, ObjectInputStream ois, ObjectOutputStream oos, INameserverForChatserver rootNameserver) {
		this.server = server;
		this.user = user;
		this.ois = ois;
		this.oos = oos;
		this.rootNameserver = rootNameserver;
	}

	public boolean isOnline() {
		return ois != null && oos != null && !user.getName().equals("");
	}

	public void processSend(SendDTO dto) throws IOException {
		System.err.println("Looping over " + server.getUsers().size() + " users!");
		for (User u : server.getUsers()) {
			if (u == user || !u.isOnline()) {
				System.err.println("Skipping " + u.getName());
				continue;
			}
			System.err.println("Writing object to " + u.getName());
			u.writeObject(new SentDTO(user.getName() + ": " + dto.getMessage()));
		}
	}

	public String processRegister(RegisterDTO dto) {
		this.user.address = dto.getAddress();
		
		try {
			this.rootNameserver.registerUser(this.user.getName(), dto.getAddress());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AlreadyRegisteredException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidDomainException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return "Successfully registered address for " + this.user.getName() + '.';
	}

	public String processLookup(LookupDTO dto) {
		
		try {
			return this.rootNameserver.lookup(dto.getUsername());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		return null;
	}

	public String processLogout(LogoutDTO dto) throws IOException {
		return "Successfully logged out.";
	}

	public void writeObject(Object o) throws IOException {
		oos.writeObject(o);
	}
	
	private void removeSession(){
		this.user.getSessions().remove(this); //Remove the instance form session set
	}

	@Override
	public void run() {
		try {
			Object o = null;

			while (!Thread.currentThread().isInterrupted()) {
				o = ois.readObject();
				System.out.println("Received " + o.getClass().getName());
				if (o instanceof SendDTO) {
					this.processSend((SendDTO) o);
				} else if (o instanceof RegisterDTO) {
					writeObject(new RegisteredDTO((RegisterDTO) o, this.processRegister((RegisterDTO) o)));
				} else if (o instanceof LookupDTO) {
					writeObject(new LookedUpDTO((LookupDTO) o, this.processLookup((LookupDTO) o)));
				} else if (o instanceof LogoutDTO) {
					writeObject(new LoggedOutDTO((LogoutDTO) o, this.processLogout((LogoutDTO) o)));
					this.oos.close();
					this.ois.close();
					break;
				}
				oos.flush();
			}
		} catch(EOFException eof){
			System.out.printf("User:[%s] closed connection!\n",user.getName());
		}catch (IOException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			this.oos = null;
			this.ois = null;
			removeSession();
		}
	}
}
