package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import cli.Command;
import cli.Shell;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserver, INameserverCli, Runnable {

	/* fields for Config & args for shell */
	private Config config;
	private String componentName;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private Shell shell;

	private Registry registry;
	
	private  ConcurrentSkipListMap<String, INameserver> zones;
	private  ConcurrentSkipListMap<String, String> users;
	
	private String zone;
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
	public Nameserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		
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
		try{
			this.domain = this.config.getString("domain");
			printToShell("[[ Nameserver for " + domain + " has been started. ]]");
		}catch(java.util.MissingResourceException ex){
			printToShell("[[ Root-nameserver has been started. ]]");
		}
		
		
		/* nameserver is root-nameserver */
		if(this. domain==null){
			String bindingName = config.getString("root_id");
			
			
			/* Initialize Registry. */
			try {
				this.registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
			} catch (RemoteException e) {
				this.printToShell("[[ Creating Registry failed. ]]");
			}
			
			/* create remoteobject */
			INameserver remoteObject = null;
			try {
				remoteObject = (INameserver) UnicastRemoteObject
						.exportObject((Remote) this, 0);
			} catch (RemoteException e1) {
				this.printToShell("[[ Creating remote object failed. ]]");
			}
			
			/* binding remoteObject to registry */
			try {
				registry.bind(bindingName, remoteObject);
			} catch (AccessException e) {
				this.printToShell("[[ Binding to registry failed: AccessException ]]");
			} catch (RemoteException e) {
				this.printToShell("[[ Binding to registry failed: RemoteException ]]");
			} catch (AlreadyBoundException e) {
				this.printToShell("[[ Binding to registry failed: AlreadyBoundException ]]");
			}
			
			this.printToShell("[[ Bound to " + bindingName + ". ]]");
			
		/* nameserver is lower level */
		}else{
			String regHost = this.config.getString("registry.host");
			int regPort = this.config.getInt("registry.port");
			
			try {
				this.registry = LocateRegistry.getRegistry(regHost, regPort);
			} catch (RemoteException e) {
				this.printToShell("[[ Locating Registry failed. ]]");
			}
		}
	}

	@Override
	@Command
	public String nameservers() throws IOException {
		
		if(this.zones.size()==0){
			return "No Nameservers registered on this Nameserver.";
		}
		
		String ret = "";
		Set<String> keys = this.zones.keySet();
		
		String formatString = "%" + (keys.size()/10) + "d. %20s\n";
		
		int cnt = 0;
		for(Iterator<String> i = keys.iterator();i.hasNext();){
			String key   = (String) i.next();
			cnt++;
			
			ret += (String.format(formatString, cnt, key));
		}
		
		return ret;
	}

	@Override
	@Command
	public String addresses() throws IOException {
		
		if(this.users.size()==0){
			return "No addresses registered on this Nameserver.";
		}
		
		String ret = "";
		Set<String> keys = this.users.keySet();
		
		String formatString = "%" + (keys.size()/10) + "d. %20s %20s\n";
		
		int cnt = 0;
		for(Iterator<String> i = keys.iterator();i.hasNext();){
			String key   = (String) i.next();
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
		
		
		return "[[ System is going down for shutdown now. ]]";
	}
	
	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);

		new Thread(nameserver).start();
	}
	/* private methods */

	private void teardown(){
		/* close streams */
		this.shell.close();

	}
	
	private void printToShell(String msg){
		try {
			this.shell.writeLine(msg);
		} catch (IOException e) {
			
		};
	}

	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		int sep = zone.lastIndexOf(".");
		
		if(sep==-1){
			INameserverForChatserver ret = this.zones.get(zone);
			
			if(ret==null){
				this.printToShell("[[ No zone matching '" + zone + "' found. ]]");
			}
			
			return ret;
		}
		
		String nextZone =  zone.substring(sep);
		String key = zone.substring(sep, zone.length());
		
		INameserverForChatserver next=  this.zones.get(key);
		
		if(next==null){
			this.printToShell("[[ No zone matching '" + key + "' found. ]]");
			return null;
		}
		
		this.printToShell("[[ Came across " + this.domain + " going deeper to find " + nextZone + "]]");
		return next.getNameserver(nextZone);
	}

	@Override
	public String lookup(String username) throws RemoteException {
		int sep = username.lastIndexOf(".");
		
		if(sep==-1){
			return this.users.get(username);
		}
		
		String nextUsername = username.substring(sep);
		String key = username.substring(sep, username.length());
		
		INameserver next=  this.zones.get(key);
		
		if(next==null){
			this.printToShell("[[ No zone matching '" + key + "' found. ]]");
			return null;
		}
		
		this.printToShell("[[ Came across " + this.domain + " going deeper to find " + nextUsername + "]]");
		return next.lookup(nextUsername);
		
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
					throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		// TODO Auto-generated method stub
		
	}
}
