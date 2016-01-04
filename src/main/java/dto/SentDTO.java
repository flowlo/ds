package dto;

import java.io.Serializable;

public class SentDTO implements Serializable {

	private static final long serialVersionUID = -2266836023086302854L;

	private String message;

	public SentDTO(String message) {
		super();
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}


}
