package dto;

import java.io.Serializable;

public class RegisterDTO implements Serializable{

	private static final long serialVersionUID = 395641218004767438L;

	private String address;

	public RegisterDTO(String address) {
		super();
		this.address = address;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}


}
