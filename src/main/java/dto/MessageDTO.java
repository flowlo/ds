package dto;

import java.io.Serializable;

public class MessageDTO implements Serializable{
	private static final long serialVersionUID = -1745505969335661293L;

	private final String message;

	public MessageDTO(final String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
