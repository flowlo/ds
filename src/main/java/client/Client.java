package client;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
import dto.LoggedOutDTO;
import dto.LogoutDTO;
import dto.LookupDTO;
import dto.AddressDTO;
import dto.RegisteredDTO;
import dto.MessageDTO;
import util.Config;
import util.HmacUtil;
import util.Keys;
import util.SecurityUtils;

public class Client implements IClientCli, Runnable {
	private static final String NEED_AUTH = "You need to be authenticated in order to issue this command.";

	private final Shell shell;
	private final String hostname;
	private final int tcpPort;
	private final int udpPort;
	private final String keyDir;
	private final String chatserverKey;

	private String lastPublicMessage = null;

	private Socket socket = new Socket();
	private Thread listenThread;

	private PrivateServer server = null;
	private Thread serverThread;

	private final ConcurrentHashMap<String, String> myContacts = new ConcurrentHashMap<String, String>();

	private ObjectOutputStream oos = null;
	private ObjectInputStream ois = null;

	private HmacUtil hmac;

	private final Buffer buffer = new Buffer(5, TimeUnit.SECONDS);

	private String username = null;

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
		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);

		hmac = new HmacUtil(config.getString("hmac.key"));

		hostname = config.getString("chatserver.host");
		tcpPort = config.getInt("chatserver.tcp.port");
		udpPort = config.getInt("chatserver.udp.port");

		keyDir = config.getString("keys.dir");
		chatserverKey = config.getString("chatserver.key");
	}

	private boolean connect() {
		if (socket != null && socket.isConnected()) {
			return true;
		}

		if (socket != null) {
			try {
				socket.close();
			} catch (IOException ignored) {}
			socket = null;
		}

		try {
			socket = new Socket(hostname, tcpPort);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return socket.isConnected();
	}

	private void teardown() throws IOException {
		// NOTE: Maybe teardown should also clear previous
		// lookups, however this is not specified.

		// We're tearing down, so break authentication.
		username = null;

		// If we're still connected, disconnect the socket.
		// This will likely interrupt the listener thread,
		// which blocks on reading from ois most of the time
		// forcing it to tear down as well.
		//
		// Dropping the reference to socket altogether
		// would break re-authentication, so let's keep it.
		// In case we're exiting, the reference will be cleared
		// up without any side-effects.
		if (socket != null && socket.isConnected()) {
			socket.close();
			socket = null;
		}

		// Closing the socket should have already done enough
		// to kill the listener thread. However, there's nothing
		// wrong with interrupting it. It could have waited on
		// buffer for example (very unlikely).
		if (listenThread != null && listenThread.isAlive()) {
			listenThread.interrupt();
			listenThread = null;
		}

		// If there's a private server, instruct it to shut down
		// it's socket and thread pool.
		if (server != null) {
			server.close();
			server = null;
		}

		// After giving the server a chance to tear down, interrupt.
		if (serverThread != null && serverThread.isAlive()) {
			serverThread.interrupt();
			serverThread = null;
		}
	}

	@Override
	public void run() {
		shell.run();
	}

	@Override
	@Deprecated
	public String login(String username, String password) throws IOException {
		return null;
	}

	@Override
	@Command
	public String logout() throws IOException {
		if (username == null)
			return NEED_AUTH;

		if (oos != null) {
			oos.writeObject(new LogoutDTO());
			oos.flush();
		}

		LoggedOutDTO dto = null;

		try {
			dto = (LoggedOutDTO) buffer.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		teardown();

		return dto != null ? "Successfully logged out." : "Server did not confirm logout.";
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		if (username == null)
			return NEED_AUTH;

		this.lastPublicMessage = message;
		oos.writeObject(new MessageDTO(message));
		oos.flush();
		return null;
	}

	@Override
	@Command
	public String list() throws IOException {
		InetAddress address = InetAddress.getByName(hostname);

		DatagramSocket datagramSocket = new DatagramSocket();
		datagramSocket.setSoTimeout(5000);

		byte[] buffer = "!list".getBytes();
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, udpPort);
		datagramSocket.send(packet);

		buffer = new byte[1024];
		packet = new DatagramPacket(buffer, buffer.length);
		datagramSocket.receive(packet);

		datagramSocket.close();
		return new String(packet.getData(), packet.getOffset(), packet.getLength());
	}

	@Override
	@Command
	public String msg(String username, String message) throws IOException {
		if (!myContacts.containsKey(username)) {
			if (username == null) {
				return NEED_AUTH;
			}
			if (!performLookup(username)) {
				return "Failed to look up \"" + username + "\".";
			}
		}

		String address = this.myContacts.get(username);

		if (address == null) {
			return "Address for user \"" + username + "\" unknown. Maybe lookup first?";
		}

		String hostname = address.split(":")[0];
		String port = address.split(":")[1];

		Socket socket = new Socket(hostname, Integer.parseInt(port));

		OutputStream os = socket.getOutputStream();
		InputStream is = socket.getInputStream();
		message = hmac.prependHash("!msg " + message);
		os.write(message.getBytes());
		os.flush();

		byte[] buf = new byte[1024];
		int len = is.read(buf);
		message = new String(buf, 0, len);
		String hash = message.substring(0, 44);
		String payload = message.substring(45);

		String result = null;
		if (!hmac.checkHash(payload, hash)) {
			result = "Incoming message from " + username + " was tampered.";
		} else if (payload.equals("!ack")) {
			result = username + " replied with !ack.";
		} else {
			result = username + " signalled tampering of our message!";
		}

		os.close();
		is.close();
		socket.close();

		return result;
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		if (this.username == null)
			return NEED_AUTH;

		if (performLookup(username)) {
			return myContacts.get(username);
		}

		return "Failed to look up \"" + username + "\".";
	}

	private boolean performLookup(String username) throws IOException {
		oos.writeObject(new LookupDTO(username));
		oos.flush();

		AddressDTO dto = null;

		try {
			dto = (AddressDTO) buffer.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

		if (dto == null || dto.getAddress() == null) {
			return false;
		}

		myContacts.put(username, dto.getAddress());
		return true;
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		if (username == null)
			return NEED_AUTH;

		if (this.server != null) {
			return "It appears you already are registered.";
		}

		URI uri = null;
		try {
			uri = new URI("tcp://" + privateAddress);
		} catch (URISyntaxException e) {
			return "Invalid address.";
		}

		if (!uri.getPath().equals("")) {
			return "Invalid address.";
		}

		if (uri.getPort() == -1) {
			return "Invalid port.";
		}

		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(uri.getHost());
		} catch (UnknownHostException e) {
			return "Unkown host.";
		}

		server = new PrivateServer(addr, uri.getPort(), shell, hmac);
		serverThread = new Thread(server);
		serverThread.start();

		oos.writeObject(new AddressDTO(privateAddress));
		oos.flush();

		RegisteredDTO dto = null;

		try {
			dto = (RegisteredDTO) buffer.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return e.toString();
		}

		if (dto == null) {
			return "Failed to register.";
		}

		return "Successfully registered address for \"" + username + "\".";
	}

	@Override
	@Command
	public String lastMsg() throws IOException {
		if (lastPublicMessage == null) {
			return "No public message in memory.";
		}
		return lastPublicMessage;
	}

	@Override
	@Command
	public String exit() throws IOException {
		teardown();

		if (shell != null) {
			shell.close();
		}

		return "Bye!";
	}

	@Command
	@Override
	public String authenticate(String username) throws IOException {
		if (this.username != null) {
			return "It appears that you already are authenticated. !logout first.";
		}

		if (!connect()) {
			return "Failed to connect to server.";
		}

		File privateKeyFile = new File(keyDir, username + ".pem");
		if (!privateKeyFile.exists()) {
			return "Could not find private key file for username \"" + username + "\".";
		}

		File publicKeyFile = new File(chatserverKey);
		if (!publicKeyFile.exists()) {
			return "Could not find server's public key.";
		}

		// Initialize key material. We'll need the public key of the server
		// to encrypt the first message, and our own private key to decrypt
		// the second message.
		PrivateKey priv = Keys.readPrivatePEM(privateKeyFile);
		PublicKey pub = Keys.readPublicPEM(publicKeyFile);

		InputStream is = socket.getInputStream();

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
		socket.getOutputStream().write(message);

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
			return "Handshake failed (malformed message).";
		}

		if (!params[1].equals(challenge)) {
			return "Handshake failed (wrong challenge: " + params[1] + " != " + challenge + ").";
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
		socket.getOutputStream().write(message);

		oos = new ObjectOutputStream(new CipherOutputStream(socket.getOutputStream(), cipher));

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

		ois = new ObjectInputStream(new CipherInputStream(is, decryptionCipher));

		listenThread = new Thread(new Listener());
		listenThread.start();

		this.username = username;

		return "Successfully established secure connection with server!";
	}

	class Listener implements Runnable {
		@Override
		public void run() {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					Object o;
					try {
						o = ois.readObject();
					} catch (EOFException e) {
						// Server hung up on us, or somebody closed the
						// underlying stream.
						return;
					}

					if (!(o instanceof MessageDTO)) {
						buffer.put(o);
						continue;
					}

					shell.writeLine((lastPublicMessage = ((MessageDTO) o).getMessage()));
				}
			} catch (ClassNotFoundException | InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// Blow up spectacularily in case I/O fails.
				throw new RuntimeException(e);
			}
		}
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
}
