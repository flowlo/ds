package datatransfer;

import java.io.Serializable;

public class LookupDTO implements Serializable {

	private static final long serialVersionUID = -7010980197806559200L;

	private String username;

	public LookupDTO(String username) {
		super();
		this.username = username;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}


}
