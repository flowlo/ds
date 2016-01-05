package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cli.Command;
import cli.Shell;
import datatransfer.ErrorResponseDTO;
import datatransfer.LoggedInDTO;
import datatransfer.LoggedOutDTO;
import datatransfer.LoginDTO;
import datatransfer.LogoutDTO;
import datatransfer.LookedUpDTO;
import datatransfer.LookupDTO;
import datatransfer.MsgDTO;
import datatransfer.RegisterDTO;
import datatransfer.RegisteredDTO;
import datatransfer.SendDTO;
import datatransfer.SentDTO;
import util.Config;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private Shell shell;

	private String hostname;
	private int tcp_port;
	private int udp_port;

	private String lastPublicMessage;

	private Socket tcp_socket;
	private DatagramSocket udp_socket;

	private ExecutorService main_threadPool = Executors.newFixedThreadPool(2);

	private ExecutorService tcp_threadPool = Executors.newCachedThreadPool();

	private PrivateServer server = null;

	private ConcurrentHashMap<String, String> myContacts;

	private ObjectOutputStream output = null;
	private ObjectInputStream input = null;

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
	public Client(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		this.lastPublicMessage = "No message received!";

		this.shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		this.shell.register(this);

		this.myContacts = new ConcurrentHashMap<String, String>();
	}

	@Override
	public void run() {
		/* start shell */
		new Thread(this.shell).start();

		System.out.println(getClass().getName() + " up and waiting for commands!");

		this.hostname = config.getString("chatserver.host");

		/* get ports */
		this.tcp_port = config.getInt("chatserver.tcp.port");
		this.udp_port = config.getInt("chatserver.udp.port");

		try {
			this.tcp_socket = new Socket(this.hostname, this.tcp_port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			this.udp_socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}

		this.main_threadPool.execute(new TCP_Thread(this));

	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {

		try {
			output = new ObjectOutputStream(tcp_socket.getOutputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		output.writeObject(new LoginDTO(username, password));
		output.flush();

		return null;
	}

	@Override
	@Command
	public String logout() throws IOException {
		try {
			output = new ObjectOutputStream(tcp_socket.getOutputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		output.writeObject(new LogoutDTO());
		output.flush();

		return null;
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		this.lastPublicMessage = message;

		try {
			output = new ObjectOutputStream(tcp_socket.getOutputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		output.writeObject(new SendDTO(message));
		output.flush();

		return null;
	}

	@Override
	@Command
	public String list() throws IOException {
		this.main_threadPool.execute(new UDP_Thread(this));
		return null;
	}

	@Override
	@Command
	public String msg(String username, String message) throws IOException {
		String address = this.myContacts.get(username);

		if (address == null) {
			return "[[ Not able to send message to " + username + " ]]";
		}

		Socket socket = null;

		String hostname = address.split(":")[0];
		String port = address.split(":")[1];

		try {
			socket = new Socket(hostname, Integer.parseInt(port));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ObjectOutputStream output = null;

		try {
			output = new ObjectOutputStream(socket.getOutputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		output.writeObject(new MsgDTO(username, message));
		output.flush();

		return "[[ Successfully sent.]]";
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		try {
			output = new ObjectOutputStream(tcp_socket.getOutputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		output.writeObject(new LookupDTO(username));
		output.flush();

		return null;
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		if (this.server != null) {
			return "[[ You already are registered fo pms. ]]";
		}

		this.server = new PrivateServer(privateAddress, this);
		this.main_threadPool.execute(this.server);

		try {
			output = new ObjectOutputStream(tcp_socket.getOutputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		output.writeObject(new RegisterDTO(privateAddress));
		output.flush();

		return "[[ Registered! ]]";
	}

	@Override
	@Command
	public String lastMsg() throws IOException {
		return this.lastPublicMessage;
	}

	@Override
	@Command
	public String exit() throws IOException {

		this.logout();

		if (this.output != null) {
			this.output.close();
		}

		if (this.input != null) {
			this.input.close();
		}

		/* close sockets */
		this.tcp_socket.close();
		this.udp_socket.close();

		/* shutdown thread pools */
		this.main_threadPool.shutdownNow();
		this.tcp_threadPool.shutdownNow();

		Thread.currentThread().interrupt();

		return "[[ Going down for shutdown now! ]]";
	}

	public Shell getShell() {
		return this.shell;
	}

	public ExecutorService getTCPThreadPool() {
		return this.tcp_threadPool;
	}

	public String getLastMsg() {
		return this.lastPublicMessage;
	}

	public void setLastMsg(String lastMsg) {
		this.lastPublicMessage = lastMsg;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in, System.out);

		Thread mainThread = new Thread(client);
		mainThread.start();

		try {
			mainThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	class UDP_Thread implements Runnable {
		private Client client;

		public UDP_Thread(Client client) {
			this.client = client;
		}

		public void run() {
			byte[] buffer;
			String request = "!list", response;
			InetAddress address = null;
			try {
				address = InetAddress.getByName(client.hostname);
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			}

			buffer = new byte[1024];
			try {
				buffer = request.getBytes();
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, client.udp_port);
				udp_socket.send(packet);

				buffer = new byte[1024];
				packet = new DatagramPacket(buffer, buffer.length);
				udp_socket.receive(packet);
				response = new String(packet.getData(), 0, packet.getLength()).trim();

				client.shell.writeLine(response);

			} catch (IOException e) {

			}
		}
	}

	class TCP_Thread implements Runnable {
		private Client client;

		public TCP_Thread(Client client) {
			this.client = client;
		}

		public void run() {
			System.out.println("TCP_Thread started");

			try {

				Object o = null;
				while (!Thread.currentThread().isInterrupted() && tcp_socket != null
						&& tcp_socket.getInputStream() != null) {

					input = new ObjectInputStream(tcp_socket.getInputStream());
					try {
						o = input.readObject();
					} catch (java.net.SocketException e) {
						input.close();
						break;
					}

					if (o instanceof LoggedInDTO) {
						shell.writeLine(((LoggedInDTO) o).getMessage());
					} else if (o instanceof LoggedInDTO) {
						shell.writeLine(((LoggedInDTO) o).getMessage());
					} else if (o instanceof LoggedOutDTO) {
						shell.writeLine(((LoggedOutDTO) o).getMessage());
					} else if (o instanceof LookedUpDTO) {
						String new_address = ((LookedUpDTO) o).getMessage();
						String username = ((LookedUpDTO) o).getRequest().getUsername();

						if (this.client.myContacts.containsKey(username)) {
							this.client.myContacts.remove(username);
						}

						this.client.myContacts.put(username, new_address);
						shell.writeLine(new_address);
					} else if (o instanceof RegisteredDTO) {
						shell.writeLine(((RegisteredDTO) o).getMessage());
					} else if (o instanceof SentDTO) {
						this.client.setLastMsg(((SentDTO) o).getMessage());
						shell.writeLine(((SentDTO) o).getMessage());
					} else if (o instanceof ErrorResponseDTO) {
						shell.writeLine(((ErrorResponseDTO) o).getMessage());
					}
				}

				input.close();
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
}
