package chatserver;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import datatransfer.LoggedOutDTO;
import datatransfer.LogoutDTO;
import datatransfer.LookedUpDTO;
import datatransfer.LookupDTO;
import datatransfer.RegisterDTO;
import datatransfer.RegisteredDTO;
import datatransfer.SendDTO;
import datatransfer.SentDTO;

public class Session implements Runnable {
	private final Chatserver server;
	private final User user;
	private final ObjectInputStream ois;
	private final ObjectOutputStream oos;

	public Session(Chatserver server, User user, ObjectInputStream ois, ObjectOutputStream oos) {
		this.server = server;
		this.user = user;
		this.ois = ois;
		this.oos = oos;
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
		return "Successfully registered address for " + this.user.getName() + '.';
	}

	public String processLookup(LookupDTO dto) {
		for (User user : server.getUsers()) {
			if (!user.getName().equals(dto.getUsername())) {
				continue;
			}
			return user.getAddress();
		}
		return null;
	}

	public String processLogout(LogoutDTO dto) throws IOException {
		this.oos.close();
		this.ois.close();
		return "Successfully logged out.";
	}

	public void writeObject(Object o) throws IOException {
		oos.writeObject(o);
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
				}
				oos.flush();
			}
		} catch (IOException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
