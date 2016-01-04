package dto;

import java.io.Serializable;

public class LookedUpDTO implements Serializable {
	private static final long serialVersionUID = 7824014387252795698L;

	private LookupDTO request;
	private String message;


	public LookedUpDTO(LookupDTO request, String message) {
		super();
		this.request = request;
		this.message = message;
	}
	public LookupDTO getRequest() {
		return request;
	}
	public void setRequest(LookupDTO request) {
		this.request = request;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}


}
