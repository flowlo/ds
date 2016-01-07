package dto;

import java.io.Serializable;

public class LookupDTO implements Serializable {
	private static final long serialVersionUID = -7010980197806559200L;

	private final String username;

	public LookupDTO(final String username) {
		super();
		this.username = username;
	}

	public String getUsername() {
		return username;
	}
}
