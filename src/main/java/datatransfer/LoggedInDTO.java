package datatransfer;

import java.io.Serializable;

public class LoggedInDTO implements Serializable{
	private static final long serialVersionUID = 1500909379565575610L;

	private LoginDTO request;
	private String message;

	public LoggedInDTO(LoginDTO request, String message) {
		super();
		this.request = request;
		this.message = message;
	}
	public LoginDTO getRequest() {
		return request;
	}
	public void setRequest(LoginDTO request) {
		this.request = request;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}


}
