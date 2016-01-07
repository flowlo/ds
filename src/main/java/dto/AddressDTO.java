package dto;

import java.io.Serializable;

public class AddressDTO implements Serializable {
	private static final long serialVersionUID = 395641218004767438L;

	private final String address;

	public AddressDTO(final String address) {
		this.address = address;
	}

	public String getAddress() {
		return address;
	}
}
