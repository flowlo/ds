package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
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

	private final Shell shell;

	private final ConcurrentSkipListMap<String, INameserver> zones;
	private final ConcurrentSkipListMap<String, String> users;

	private Registry registry;

	private String domain;

	private boolean isRoot;

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

		/* register shell */
		this.shell = new Shell(componentName, userRequestStream, userResponseStream);
		this.shell.register(this);

		this.isRoot = false;
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
			this.isRoot = true;
			logToShell("Root-nameserver has been started.");
		}

		if (this.isRoot) {
			this.domain = "root-domain";

			String bindingName = config.getString("root_id");

			/* Initialize Registry. */
			try {
				this.registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
			} catch (RemoteException e) {
				this.teardown("Creating Registry failed." + e.toString());
			}
			/* create remoteobject */
			INameserver remoteObject = null;
			try {
				remoteObject = (INameserver) UnicastRemoteObject.exportObject((Remote) this, 0);
			} catch (RemoteException e) {
				this.teardown("Creating remote object failed." + e.toString());
			}

			/* binding remoteObject to registry */
			try {
				registry.bind(bindingName, remoteObject);
			} catch (RemoteException | AlreadyBoundException e) {
				this.teardown("failed binding remoteObject to registry:" + e.toString());
			}

			this.logToShell("Bound to " + bindingName + ".");
		}
		/* nameserver is lower level */
		else {
			String regHost = this.config.getString("registry.host");
			int regPort = this.config.getInt("registry.port");

			/* obtain registry object */
			try {
				this.registry = LocateRegistry.getRegistry(regHost, regPort);
			} catch (RemoteException e) {
				this.teardown("Locating Registry failed.\n" + e.toString());
			}

			String lookupName = this.config.getString("root_id");
			INameserver rootServer = null;

			/* obtain server object */

			try {
				rootServer = (INameserver) this.registry.lookup(lookupName);
			} catch (RemoteException | NotBoundException e) {
				this.teardown("Obtaining rootServer object failed: " + e.toString());
			}

			/* create remoteobject */
			INameserver remoteObject = null;
			try {
				remoteObject = (INameserver) UnicastRemoteObject.exportObject((Remote) this, 0);
			} catch (RemoteException e) {
				this.logToShell("Creating remote object failed: " + e.toString());
			}

			/* register nameserver */
			boolean retry = true;
			while (retry) {
				try {
					rootServer.registerNameserver(this.domain, remoteObject, remoteObject);
					retry = false;
				} catch (RemoteException | AlreadyRegisteredException | InvalidDomainException e) {
					this.logToShell("Registering Nameserver failed: " + e.toString() + "\n Retrying.");
				}
			}

			this.logToShell("Successfully registered.");
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
		this.teardown();

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
		INameserver ret = null;

		if (zone == null) {
			this.logToShell("Bad Arguments for lookup passed.");
			return null;
		}

		ret = this.zones.get(zone);

		if (ret == null) {
			this.logToShell("No zone matching '" + zone + "' found.");
		}

		return ret;
	}

	@Override
	public String lookup(String username) throws RemoteException {
		String ret = null;

		if (username == null) {
			this.logToShell("Bad Arguments for lookup passed.");
			return null;
		}

		ret = this.users.get(username);

		if (ret == null) {
			this.logToShell("No user matching '" + username + "' found.");
		}

		return ret;
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

	public void teardown() {
		this.teardown(null);

	}

	public void teardown(String msg) {
		if (msg != null) {
			this.logToShell(msg);
		}

		this.logToShell("Nameserver is going down for shutdown NOW!");

		try {
			// unexport the previously exported remote object
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
			System.err.println("Error while unexporting object: " + e.getMessage());
		}
		
		if (this.isRoot) {

			try {
				// unbind the remote object so that a client can't find it
				// anymore
				registry.unbind(config.getString("root_id"));
			} catch (Exception e) {
				System.err.println("Error while unbinding object: " + e.getMessage());
			}
		}

		/* close streams */
		this.shell.close();

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
