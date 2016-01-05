package datatransfer;

import java.io.Serializable;

public class MsgDTO implements Serializable{

	private static final long serialVersionUID = -5466356153201306187L;

	private String username;
	private String message;


	public MsgDTO(String username, String message) {
		super();
		this.username = username;
		this.message = message;
	}


	public String getUsername() {
		return username;
	}


	public void setUsername(String username) {
		this.username = username;
	}


	public String getMessage() {
		return message;
	}


	public void setMessage(String message) {
		this.message = message;
	}


}
