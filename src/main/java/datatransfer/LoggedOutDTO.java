package datatransfer;

import java.io.Serializable;

public class LoggedOutDTO implements Serializable {
	private static final long serialVersionUID = -4979189969487190858L;

	private LogoutDTO request;
	private String message;

	public LoggedOutDTO(LogoutDTO request, String message) {
		super();
		this.request = request;
		this.message = message;
	}

	public LogoutDTO getDto() {
		return request;
	}

	public void setDto(LogoutDTO dto) {
		this.request = dto;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}


}
