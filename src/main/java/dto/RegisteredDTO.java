package dto;

import java.io.Serializable;

public class RegisteredDTO implements Serializable {
	private static final long serialVersionUID = 1178087111755454210L;
	private RegisterDTO request;
	private String message;
	public RegisteredDTO(RegisterDTO request, String message) {
		super();
		this.request = request;
		this.message = message;
	}
	public RegisterDTO getRequest() {
		return request;
	}
	public void setRequest(RegisterDTO request) {
		this.request = request;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}


}
