package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import cli.Command;
import cli.Shell;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import util.Config;

public class Nameserver implements INameserver, INameserverCli, Runnable {

	/* fields for Config & args for shell */
	private final Config config;
	private final String componentName;
	private final InputStream userRequestStream;
	private final PrintStream userResponseStream;

	private final Shell shell;

	private final ConcurrentSkipListMap<String, INameserver> zones;
	private final ConcurrentSkipListMap<String, String> users;

	private Registry registry;

	private String domain;

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Nameserver(String componentName, Config config, InputStream userRequestStream,
			PrintStream userResponseStream) {

		/* initialize collections for zones and users */
		this.zones = new ConcurrentSkipListMap<String, INameserver>();
		this.users = new ConcurrentSkipListMap<String, String>();

		/* setup config and args for shell */
		this.config = config;
		this.componentName = componentName;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		/* register shell */
		this.shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		this.shell.register(this);
	}

	@Override
	public void run() {
		/* start shell */
		(new Thread(this.shell)).start();

		/* read domain */
		this.domain = null;
		try {
			this.domain = this.config.getString("domain");
			logToShell("Nameserver for " + domain + " has been started.");
		} catch (java.util.MissingResourceException ex) {
			logToShell("Root-nameserver has been started.");
		}

		/* nameserver is root-nameserver */
		if (this.domain == null) {
			String bindingName = config.getString("root_id");

			this.domain = "root-domain";

			/* Initialize Registry. */
			try {
				this.registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
			} catch (RemoteException e) {
				this.logToShell("Creating Registry failed.");
			}

			/* create remoteobject */
			INameserver remoteObject = null;
			try {
				remoteObject = (INameserver) UnicastRemoteObject.exportObject((Remote) this, 0);
			} catch (RemoteException e1) {
				this.logToShell("Creating remote object failed.");
			}

			/* binding remoteObject to registry */
			try {
				registry.bind(bindingName, remoteObject);
			} catch (AccessException e) {
				this.logToShell("Binding to registry failed: AccessException");
			} catch (RemoteException e) {
				this.logToShell("Binding to registry failed: RemoteException");
			} catch (AlreadyBoundException e) {
				this.logToShell("Binding to registry failed: AlreadyBoundException");
			}

			this.logToShell("Bound to " + bindingName + ".");

			/* nameserver is lower level */
		} else {
			String regHost = this.config.getString("registry.host");
			int regPort = this.config.getInt("registry.port");

			try {
				this.registry = LocateRegistry.getRegistry(regHost, regPort);
			} catch (RemoteException e) {
				this.logToShell("Locating Registry failed.");
			}

			String lookupName = this.config.getString("root_id");
			INameserver rootServer = null;
			try {
				rootServer = (INameserver) this.registry.lookup(lookupName);
			} catch (AccessException e) {
				this.logToShell("Looking up from registry failed: AccessException" + e.getMessage());
			} catch (RemoteException e) {
				this.logToShell("Looking up from registry failed: RemoteException" + e.getMessage());
			} catch (NotBoundException e) {
				this.logToShell("Looking up from registry failed: NotBoundException" + e.getMessage());
			}

			/* create remoteobject */
			INameserver remoteObject = null;
			try {
				remoteObject = (INameserver) UnicastRemoteObject.exportObject((Remote) this, 0);
			} catch (RemoteException e1) {
				this.logToShell("Creating remote object failed.");
			}

			try {
				rootServer.registerNameserver(this.domain, remoteObject, remoteObject);
			} catch (RemoteException e) {
				this.logToShell("Registering Nameserver failed:\n\tRemoteException: " + e.getMessage());
				this.teardown(1);
			} catch (AlreadyRegisteredException e) {
				this.logToShell("Registering Nameserver failed:\n\tAlreadyRegisteredException: " + e.getMessage());
				this.teardown(2);
			} catch (InvalidDomainException e) {
				this.logToShell("Registering Nameserver failed:\n\tInavlidDomainException: " + e.getMessage());
				this.teardown(3);
			}
		}
	}

	@Override
	@Command
	public String nameservers() throws IOException {

		if (this.zones.size() == 0) {
			return "No Nameservers registered on this Nameserver.";
		}

		String ret = "";
		Set<String> keys = this.zones.keySet();

		int zeros = (keys.size() / 10);
		String formatString = (zeros > 0) ? ("%" + zeros + "d. %20s\n") : ("%d. %20s\n");

		int cnt = 0;
		for (Iterator<String> i = keys.iterator(); i.hasNext();) {
			String key = (String) i.next();
			cnt++;

			ret += (String.format(formatString, cnt, key));
		}

		return ret;
	}

	@Override
	@Command
	public String addresses() throws IOException {

		if (this.users.size() == 0) {
			return "No addresses registered on this Nameserver.";
		}

		String ret = "";
		Set<String> keys = this.users.keySet();

		int zeros = (keys.size() / 10);
		String formatString = (zeros > 0) ? ("%" + zeros + "d. %20s %20s\n") : ("d. %20s %20s\n");

		int cnt = 0;
		for (Iterator<String> i = keys.iterator(); i.hasNext();) {
			String key = (String) i.next();
			String value = (String) this.users.get(key);
			cnt++;

			ret += (String.format(formatString, cnt, key, value));
		}

		return ret;
	}

	@Override
	@Command
	public String exit() throws IOException {
		this.teardown(0);

		return "System is going down for shutdown now.";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]), System.in, System.out);

		new Thread(nameserver).start();
	}

	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

		String[] splitStr = this.stripToResolve(username);

		if (splitStr == null) {
			this.logToShell("Bad Arguments for registering user passed.");
			return;
		}

		if (splitStr.length == 1) {
			if (this.users.get(splitStr[0]) != null) {
				throw new AlreadyRegisteredException("Already registered a user known as '" + splitStr[0]
						+ "' . Therefore " + username + " cannot be registered on " + this.domain + ".");
			}

			this.users.put(splitStr[0], address);
			this.logToShell("Successfully registered user " + splitStr[0] + " to domain " + this.domain);
			return;
		}

		INameserver next = this.zones.get(splitStr[1]);

		if (next == null) {
			this.logToShell("No zone matching '" + splitStr[1] + "' found.");
			throw new InvalidDomainException("No zone matching '" + splitStr[1] + "' found. Therefore " + username
					+ " cannot be registered on " + this.domain + ".");
		}

		this.logToShell("Came across " + this.domain + " going deeper to find " + splitStr[0]);
		next.registerUser(splitStr[0], address);

	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		String[] splitStr = this.stripToResolve(zone);
		INameserver ret = null;

		if (splitStr == null) {
			this.logToShell("Bad Arguments for lookup passed.");
			return null;
		}

		if (splitStr.length == 1) {
			ret = this.zones.get(splitStr[0]);

			if (ret == null) {
				this.logToShell("No user matching '" + zone + "' found.");
			}

			return ret;
		}

		INameserver next = this.zones.get(splitStr[1]);

		if (next == null) {
			this.logToShell("No zone matching '" + splitStr[1] + "' found.");
			return null;
		}

		this.logToShell("Came across " + this.domain + " going deeper to find " + splitStr[0]);
		return next.getNameserver(splitStr[0]);
	}

	@Override
	public String lookup(String username) throws RemoteException {
		String[] splitStr = this.stripToResolve(username);
		String ret = null;

		if (splitStr == null) {
			this.logToShell("Bad Arguments for lookup passed.");
			return null;
		}

		if (splitStr.length == 1) {
			ret = this.users.get(splitStr[0]);

			if (ret == null) {
				this.logToShell("No user matching '" + username + "' found.");
			}

			return ret;
		}

		INameserver next = this.zones.get(splitStr[1]);

		if (next == null) {
			this.logToShell("No zone matching '" + splitStr[1] + "' found.");
			return null;
		}

		this.logToShell("Came across " + this.domain + " going deeper to find " + splitStr[0]);
		return next.lookup(splitStr[0]);

	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
					throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

		String[] splitStr = this.stripToResolve(domain);

		if (splitStr == null || nameserver == null || nameserverForChatserver == null) {
			this.logToShell("Bad Arguments for registering nameserver passed.");
			return;
		}

		if (splitStr.length == 1) {
			if (this.zones.get(splitStr[0]) != null) {
				throw new AlreadyRegisteredException("Already registered a nameserver known as '" + splitStr[0]
						+ "' . Therefore " + domain + " cannot be registered on " + this.domain + ".");
			}

			this.zones.put(splitStr[0], nameserver);
			this.logToShell("Successfully registered zone " + splitStr[0] + " to domain " + this.domain);
			return;
		}

		INameserver next = this.zones.get(splitStr[1]);

		if (next == null) {
			this.logToShell("No zone matching '" + splitStr[1] + "' found.");
			throw new InvalidDomainException("No zone matching '" + splitStr[1] + "' found. Therefore " + domain
					+ " cannot be registered on " + this.domain + ".");
		}

		this.logToShell("Came across " + this.domain + " going deeper to find " + splitStr[0]);
		next.registerNameserver(splitStr[0], nameserver, nameserverForChatserver);
	}

	/* private methods */

	private void teardown(int exitCode) {
		/* close streams */
		this.shell.close();
		System.exit(exitCode);

	}

	private void printToShell(String msg) {
		try {
			this.shell.writeLine(msg);
		} catch (IOException e) {

		}
		;
	}

	private void logToShell(String msg) {
		printToShell(String.format("[[ %tc ]]: %s", new Date(), msg));
	}

	private String[] stripToResolve(String str) {
		if (str == null) {
			return null;
		}

		String[] ret = null;
		int sep = str.lastIndexOf(".");

		if (sep == -1) {
			ret = new String[1];
			ret[0] = str;
			return ret;
		}

		ret = new String[2];
		ret[0] = str.substring(0, sep);
		ret[1] = str.substring(sep + 1);

		return ret;
	}
}
