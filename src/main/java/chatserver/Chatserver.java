package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



import cli.Command;
import cli.Shell;
import util.Config;

public class Chatserver implements IChatserverCli, Runnable {

	private static Thread mainThread;

	private Config config;
	private Shell shell;

	/* users */
	private Config users_config;
	private ConcurrentHashMap<String, ChatSession> users;

	private int tcp_port;
	private int udp_port;

	private ServerSocket tcp_socket;
	private DatagramSocket udp_socket;

	private static ExecutorService main_threadPool = Executors.newFixedThreadPool(3);

	private ExecutorService tcp_threadPool;

	public String list(){
		String result = "Online users:\n";
		ArrayList<String> onlineList = new ArrayList<String>();

		for(ChatSession session : users.values()){
			if(session.isOnline()){
				onlineList.add(session.getUsername());
			}
		}

		Collections.sort(onlineList);

		for(String u:onlineList){
			result += u + "\n";
		}

		return result;

	}


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
	public Chatserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.config = config;

		this.shell = new Shell(componentName, userRequestStream, userResponseStream);
		this.shell.register(this);
	}

	@Override
	public void run() {

		/*start shell*/
		new Thread(this.shell).start();

		System.out.println(getClass().getName()
				+ " up and waiting for commands!");


		/* get ports */
		this.tcp_port = config.getInt("tcp.port");
		this.udp_port = config.getInt("udp.port");

		/* get users */
		this.users_config = new Config("user");
		this.users = new ConcurrentHashMap<String, ChatSession>();

		for(String key:this.users_config.listKeys()){
			ChatSession session = new ChatSession();

			session.setUsername(key.replace(".password", ""));
			session.setPassword(users_config.getString(key));
			session.setOnline(false);

			users.put(session.getUsername(), session);
		}


		/* create threadPools */
		this.tcp_threadPool = Executors.newCachedThreadPool();

		try {
			this.tcp_socket = new ServerSocket(tcp_port);
		} catch (IOException e) {
			System.err.println("Error creating TCP ServerSocket on port: ");
			System.err.println(Integer.toString(tcp_port));
			e.printStackTrace();
			try {
				this.exit();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		try {
			this.udp_socket = new DatagramSocket(udp_port);
		} catch (IOException e) {
			System.err.println("Error creating UDP DatagramSocket on port: ");
			System.err.println(Integer.toString(udp_port));
			e.printStackTrace();
			try {
				exit();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		//main_threadPool.execute(new Interaction_Thread());
		main_threadPool.execute(new ChatServerTCPThread(this, this.tcp_socket, this.tcp_threadPool, this.users));

		main_threadPool.execute(new ChatServerUDPThread(this, this.udp_socket));

	}

	@Override
	@Command
	public String users() throws IOException {
		String ret = ""; 
		for(String key: users.keySet()){
			ret += key + " is " + users.get(key).getState() + "!\n";
		}

		return ret;
	}

	@Override
	@Command
	public String exit() throws IOException {
		System.out.println("Server is going down for shutdown now!!!");

		this.tcp_socket.close();
		this.udp_socket.close();

		tcp_threadPool.shutdownNow();
		main_threadPool.shutdownNow();

		shell.close();

		return "Shut down completed!";
	}

	public Shell getShell(){
		return this.shell;
	}

	public ConcurrentHashMap<String, ChatSession> getUsers(){
		return this.users;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		Chatserver chatserver = new Chatserver(args[0],
				new Config("chatserver"), System.in, System.out);
		mainThread = new Thread(chatserver);
		mainThread.start();

		try {
			mainThread.join();
		} catch (InterruptedException e) {
			System.out.println("Server rootThread is going to die now");
			Thread.currentThread().interrupt();
		}
	}

}
