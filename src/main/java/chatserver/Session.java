package chatserver;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;

import dto.LoggedOutDTO;
import dto.LogoutDTO;
import dto.LookupDTO;
import dto.AddressDTO;
import dto.RegisteredDTO;
import dto.MessageDTO;
import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

public class Session {
	private final Chatserver server;
	private final User user;
	private final ObjectInputStream ois;
	private final ObjectOutputStream oos;

	private boolean online = true;

	public Session(Chatserver server, User user, ObjectInputStream ois, ObjectOutputStream oos) {
		this.server = server;
		this.user = user;
		this.ois = ois;
		this.oos = oos;
	}

	public User getUser() {
		return user;
	}

	public boolean isOnline() {
		return online;
	}

	public void processSend(MessageDTO dto) throws IOException {
		for (User u : server.getUsers()) {
			if (u == user || !u.isOnline()) {
				System.err.println("Skipping " + u.getName());
				continue;
			}
			System.err.println("Writing object to " + u.getName());
			u.writeObject(new MessageDTO(user.getName() + ": " + dto.getMessage()));
		}
	}

	public String processRegister(AddressDTO dto) {
		this.user.address = dto.getAddress();

		try {
			server.getRootNameserver().registerUser(this.user.getName(), dto.getAddress());
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
			return server.getRootNameserver().lookup(dto.getUsername());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public void writeObject(Object o) throws IOException {
		oos.writeObject(o);
	}

	private void removeSession() {
		this.user.getSessions().remove(this); // Remove the instance form
												// session set
	}

	public boolean talk() {
		try {
			Object o = null;

			while (!Thread.currentThread().isInterrupted()) {
				o = ois.readObject();
				System.out.println("Received " + o.getClass().getName());
				if (o instanceof MessageDTO) {
					this.processSend((MessageDTO) o);
				} else if (o instanceof AddressDTO) {
					writeObject(new RegisteredDTO());
				} else if (o instanceof LookupDTO) {
					writeObject(new AddressDTO(server.getRootNameserver().lookup(((LookupDTO)o).getUsername())));
				} else if (o instanceof LogoutDTO) {
					writeObject(new LoggedOutDTO());
					System.err.println("Returning from talk()");
					return true;
				}
				oos.flush();
			}
		} catch (EOFException eof) {
			System.out.printf("User:[%s] closed connection!\n", user.getName());
		} catch (IOException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			removeSession();
		}
		return false;
	}
}
