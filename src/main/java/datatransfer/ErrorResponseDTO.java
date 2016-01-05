package datatransfer;

import java.io.Serializable;

public class ErrorResponseDTO implements Serializable {

	private static final long serialVersionUID = -3104045461568212348L;

	private String message;

	public ErrorResponseDTO(String message) {
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
