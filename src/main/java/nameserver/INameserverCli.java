package nameserver;

import java.io.IOException;

public interface INameserverCli {
	/**
	 * Prints out some information about each known nameserver (zones) from the
	 * perspective of this nameserver.<br/>
	 * 
	 * @return information about the nameservers
	 * @throws IOException
	 */
	public String nameservers() throws IOException;

	/**
	 * Prints out some information about each handled address, containing
	 * username and address (IP:port).<br/>
	 * 
	 * @return the address information
	 * @throws IOException
	 */
	public String addresses() throws IOException;

	/**
	 * Performs a shutdown of the nameserver and releases all resources. <br/>
	 * Shutting down an already terminated nameserver has no effect.
	 *
	 * @return any message indicating that the nameserver is going to terminate
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public String exit() throws IOException;
}
