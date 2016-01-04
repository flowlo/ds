package chatserver;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class User {
	final String name;
	final Set<Session> sessions = new HashSet<>();

	String address = null;

	public User(String name) {
		this.name = name;
	}

	public boolean isOnline() {
		for (Session session : sessions) {
			if (session.isOnline()) {
				return true;
			}
		}
		return false;
	}

	public void writeObject(Object o) throws IOException {
		for (Session session : sessions) {
			session.writeObject(o);
		}
	}

	public Set<Session> getSessions() {
		return sessions;
	}

	public String getName() {
		return name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}
}
