package client;

import java.io.File;
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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;

import cli.Command;
import cli.Shell;
import common.CipherInputStream;
import common.CipherOutputStream;
import datatransfer.ErrorResponseDTO;
import datatransfer.LoggedOutDTO;
import datatransfer.LogoutDTO;
import datatransfer.LookedUpDTO;
import datatransfer.LookupDTO;
import datatransfer.MsgDTO;
import datatransfer.RegisterDTO;
import datatransfer.RegisteredDTO;
import datatransfer.SendDTO;
import datatransfer.SentDTO;
import util.Config;
import util.Keys;
import util.SecurityUtils;

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
	}

	@Override
	public String login(String username, String password) throws IOException {
		return null;
	}

	@Override
	@Command
	public String logout() throws IOException {
		output.writeObject(new LogoutDTO());
		output.flush();

		return null;
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		this.lastPublicMessage = message;
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
		SecurityUtils.registerBouncyCastle();

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
		private final Client client;
		private final InputStream is;
		private ObjectInputStream ois;

		public TCP_Thread(Client client, InputStream is) {
			this.client = client;
			this.is = is;
		}

		public void run() {
			if (is == null) {
				return;
			}

			try {
				ois = new ObjectInputStream(is);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			System.out.println("TCPThread started");

			try {
				Object o = null;
				while (!Thread.currentThread().isInterrupted()) {
					try {
						o = ois.readObject();
					} catch (SocketException e) {
						e.printStackTrace();
						ois.close();
						break;
					}

					if (o instanceof LoggedOutDTO) {
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

				ois.close();
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	@Command
	@Override
	public String authenticate(String username) throws IOException {
		File privateKeyFile = new File(config.getString("keys.dir"), username + ".pem");
		if (!privateKeyFile.exists()) {
			shell.writeLine("Could not find private key file for username \"" + username + "\".");
			return null;
		}

		File publicKeyFile = new File(config.getString("chatserver.key"));
		if (!publicKeyFile.exists()) {
			shell.writeLine("Could not find server's public key.");
			return null;
		}

		// Initialize key material. We'll need the public key of the server
		// to encrypt the first message, and our own private key to decrypt
		// the second message.
		PrivateKey priv = Keys.readPrivatePEM(privateKeyFile);
		PublicKey pub = Keys.readPublicPEM(publicKeyFile);

		InputStream is = tcp_socket.getInputStream();

		Cipher cipher = null;
		try {
			cipher = Cipher.getInstance(SecurityUtils.ASYMMETRIC_SPEC);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			cipher.init(Cipher.ENCRYPT_MODE, pub);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Obtain some random bytes used as challenge. We'll encrypt this and
		// the server
		// will have to send it back so we can be sure that the server decrypted
		// the
		// challenge using the private key that matches the public key we have.
		String challenge = SecurityUtils.randomBytesEncoded(32);

		// Construct the first message.
		byte[] message = ("!authenticate " + username + " " + challenge).getBytes();

		// Encrypt and encode the first message.
		try {
			message = Base64.encode(cipher.doFinal(message));
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Send off the first message.
		tcp_socket.getOutputStream().write(message);

		// Now prepare to receive the second message, so set up everything for
		// decryption.
		try {
			cipher.init(Cipher.DECRYPT_MODE, priv);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Receive the second message.
		message = new byte[684];
		int messageLen = 0;
		try {
			messageLen = is.read(message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Decode and decrypt the second message.
		message = Base64.decode(Arrays.copyOfRange(message, 0, messageLen));
		try {
			message = cipher.doFinal(message);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String[] params = new String(message).split(" ");

		if (params == null || params.length != 5 || !params[0].equals("!ok")) {
			shell.writeLine("Handshake failed (malformed message).");
			return null;
		}

		if (!params[1].equals(challenge)) {
			shell.writeLine("Handshake failed (wrong challenge: " + params[1] + " != " + challenge + ").");
			return null;
		}

		// Now that we have verified our part of the challenge,
		// we have to authenticate to the server by encrypting it.
		challenge = params[2];

		// Extract parameters for symmetric channel.
		byte[] secret = Base64.decode(params[3].getBytes());
		byte[] iv = Base64.decode(params[4].getBytes());

		// Override cipher, as communication is symmetrically encrypted from
		// this
		// point on.
		try {
			cipher = Cipher.getInstance(SecurityUtils.SYMMETRIC_SPEC);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new IvParameterSpec(iv));
		} catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			message = Base64.encode(cipher.doFinal(challenge.getBytes()));
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Send off the third message. Handshake is completed.
		tcp_socket.getOutputStream().write(message);

		this.output = new ObjectOutputStream(new CipherOutputStream(tcp_socket.getOutputStream(), cipher));

		Cipher decryptionCipher = null;
		try {
			decryptionCipher = Cipher.getInstance(SecurityUtils.SYMMETRIC_SPEC);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			decryptionCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new IvParameterSpec(iv));
		} catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.main_threadPool.execute(new TCP_Thread(this, new CipherInputStream(is, decryptionCipher)));

		return null;
	}
}
