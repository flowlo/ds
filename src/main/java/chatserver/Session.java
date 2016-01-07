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
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

public class Session {
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

	public void send(MessageDTO dto) {
		for (User u : server.getUsers()) {
			if (u != user && u.isOnline()) {
				try {
					u.writeObject(new MessageDTO(user.getName() + ": " + dto.getMessage()));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public RegisteredDTO register(AddressDTO dto) {
		user.setAddress(dto.getAddress());

		try {
			server.getRootNameserver().registerUser(user.getName(), user.getAddress());
		} catch (RemoteException | InvalidDomainException | AlreadyRegisteredException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return new RegisteredDTO();
	}

	public AddressDTO lookup(LookupDTO dto) {
		try {
			return new AddressDTO(server.getRootNameserver().lookup(dto.getUsername()));
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public void writeObject(Object o) throws IOException {
		oos.writeObject(o);
	}

	public void talk() {
		try {
			Object o = null;

			while (!Thread.currentThread().isInterrupted()) {
				o = ois.readObject();

				if (o instanceof MessageDTO) {
					send((MessageDTO) o);
				} else if (o instanceof AddressDTO) {
					writeObject(register((AddressDTO) o));
				} else if (o instanceof LookupDTO) {
					writeObject(lookup((LookupDTO) o));
				} else if (o instanceof LogoutDTO) {
					writeObject(new LoggedOutDTO());
					break;
				}

				oos.flush();
			}
		} catch (EOFException ignored) {
		} catch (IOException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				oos.flush();
				oos.close();
				ois.close();
			} catch (IOException ignored) {
			}

			user.removeSession(this);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ois == null) ? 0 : ois.hashCode());
		result = prime * result + ((oos == null) ? 0 : oos.hashCode());
		result = prime * result + ((server == null) ? 0 : server.hashCode());
		result = prime * result + ((user == null) ? 0 : user.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Session other = (Session) obj;
		if (ois == null) {
			if (other.ois != null)
				return false;
		} else if (!ois.equals(other.ois))
			return false;
		if (oos == null) {
			if (other.oos != null)
				return false;
		} else if (!oos.equals(other.oos))
			return false;
		if (server == null) {
			if (other.server != null)
				return false;
		} else if (!server.equals(other.server))
			return false;
		if (user == null) {
			if (other.user != null)
				return false;
		} else if (!user.equals(other.user))
			return false;
		return true;
	}
}
