package chatserver;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;

import cli.Command;
import cli.Shell;
import common.CipherInputStream;
import common.CipherOutputStream;
import util.Config;
import util.Keys;
import util.SecurityUtils;

public class Chatserver implements IChatserverCli, Runnable {
	private static Thread mainThread;

	private Config config;

	private Shell shell;

	private final List<User> users = Collections.synchronizedList(new LinkedList<User>());

	private int tcpPort;
	private int udpPort;

	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;

	private Thread udpThread;

	private ExecutorService threadPool = Executors.newCachedThreadPool();

	private PrivateKey privateKey;

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
	public Chatserver(String componentName, Config config, InputStream userRequestStream,
			PrintStream userResponseStream) {
		this.config = config;

		this.shell = new Shell(componentName, userRequestStream, userResponseStream);
		this.shell.register(this);
	}

	public List<User> getUsers() {
		return this.users;
	}

	@Override
	public void run() {
		new Thread(this.shell).start();

		System.out.println(getClass().getName() + " up and waiting for commands!");

		this.tcpPort = config.getInt("tcp.port");
		this.udpPort = config.getInt("udp.port");

		final File privateKeyFile = new File(config.getString("key"));

		try {
			this.privateKey = Keys.readPrivatePEM(privateKeyFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		File[] keys = new File(config.getString("keys.dir")).listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				// The server will find it's own public key. To avoid that,
				// derive the name of the public key from the name of the private key.
				// This purely relies on convention.
				if (pathname.equals(new File(privateKeyFile.getPath().replaceFirst("\\.pem$", ".pub.pem")))) {
					return false;
				}
				if (!pathname.getName().endsWith(".pub.pem")) {
					return false;
				}
				return !pathname.isDirectory();
			}
		});

		for (File file : keys) {
			users.add(new User(file.getName().substring(0, file.getName().length() - ".pub.pem".length())));
		}

		try {
			this.serverSocket = new ServerSocket(tcpPort);
		} catch (IOException e) {
			System.err.println("Error creating TCP ServerSocket on port: " + tcpPort);
			e.printStackTrace();
			try {
				exit();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		try {
			this.datagramSocket = new DatagramSocket(udpPort);
		} catch (IOException e) {
			System.err.println("Error creating UDP DatagramSocket on port: " + udpPort);
			e.printStackTrace();
			try {
				exit();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		udpThread = new Thread(new UDPThread(this));
		udpThread.start();

		while (!Thread.currentThread().isInterrupted()) {
			try {
				final Socket socket = serverSocket.accept();
				Thread handler = new Thread() {
					@Override
					public void run() {
						Session session = null;

						do {
							try {
								session = shakeHands(socket);
							} catch (IOException e) {
								e.printStackTrace();
								return;
							}
						} while (session == null);

						session.run();

					}
				};
				threadPool.execute(handler);
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
	}

	@Override
	@Command
	public String users() throws IOException {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (User user : users) {
			sb.append(++i + ". ");
			sb.append(user.getName());
			sb.append(" o");
			sb.append(user.isOnline() ? "n" : "ff");
			sb.append("line\n");
		}
		return sb.toString();
	}

	private Session shakeHands(Socket socket) throws IOException {
		InputStream is = socket.getInputStream();
		OutputStream os = socket.getOutputStream();

		// Prepare asymmetric cipher to receive challenge from client.
		Cipher cipher = null;
		try {
			cipher = Cipher.getInstance(SecurityUtils.ASYMMETRIC_SPEC);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Use servers private key to decrypt, as the client will have
		// encrypted the first message using the public key it has associated
		// with the server.
		try {
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Receive first message.
		byte[] message = new byte[684];
		int messageLen = is.read(message);
		// Decode and decrypt first message.
		message = Base64.decode(Arrays.copyOfRange(message, 0, messageLen));
		try {
			message = cipher.doFinal(message);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Extract parameters from first message.
		String[] params = new String(message).split("\\s");

		if (params == null || params.length != 3 || !params[0].equals("!authenticate")) {
			// Error out of this, because message 1 is malformed.
			return null;
		}

		String username = params[1];

		User user = null;
		for (User u : users) {
			if (!u.getName().equals(username)) {
				continue;
			}

			user = u;
		}

		if (user == null) {
			System.err.println("Unknown user " + username + " tried to authenticate.");
			return null;
		}

		String challenge = SecurityUtils.randomBytesEncoded(32);

		byte[] secret = SecurityUtils.randomBytes(256 / 8);
		byte[] iv = SecurityUtils.randomBytes(16);

		message = (
			"!ok " +
			params[2] + " " + // Here we return the client challenge.
			challenge + " " +
			new String(Base64.encode(secret)) + " " +
			new String(Base64.encode(iv))
		).getBytes();

		PublicKey publicKey = Keys.readPublicPEM(new File(config.getString("keys.dir"), username + ".pub.pem"));

		// Use user's public key to encrypt.
		try {
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Encrypt and encode second message.
		try {
			message = Base64.encode(cipher.doFinal(message));
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Send second message.
		os.write(message);

		// Set up symmetric cipher, as from now on we'll use a symmetric scheme.
		try {
			cipher = Cipher.getInstance(SecurityUtils.SYMMETRIC_SPEC);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new IvParameterSpec(iv));
		} catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Receive third message.
		message = new byte[1024];
		messageLen = is.read(message);

		// Decode and decrypt third message.
		message = Base64.decode(Arrays.copyOfRange(message, 0, messageLen));
		try {
			message = cipher.doFinal(message);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Check challenge.
		if (!new String(message).equals(challenge)) {
			return null;
		}

		Cipher encryptionCipher = null;
		try {
			encryptionCipher = Cipher.getInstance(SecurityUtils.SYMMETRIC_SPEC);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			encryptionCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new IvParameterSpec(iv));
		} catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		ObjectOutputStream oos = new ObjectOutputStream(new CipherOutputStream(os, cipher));
		ObjectInputStream ois = new ObjectInputStream(new CipherInputStream(is, encryptionCipher));

		shell.writeLine("Successfully authenticated " + username);

		Session session = new Session(this, user, ois, oos);
		user.getSessions().add(session);
		return session;
	}

	private class UDPThread implements Runnable {
		private final Chatserver server;

		public UDPThread(Chatserver server) {
			this.server = server;
		}

		public void run() {
			byte[] buffer;
			String message;

			while (!Thread.currentThread().isInterrupted()) {
				buffer = new byte[1024];
				try {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					datagramSocket.receive(packet);
					message = new String(packet.getData(), 0, packet.getLength()).trim();

					if (!message.equals("!list")) {
						continue;
					}

					ArrayList<String> onlineList = new ArrayList<String>(users.size());
					for (User user : users) {
						if (!user.isOnline()) {
							continue;
						}
						onlineList.add(user.getName());
					}

					if (onlineList.isEmpty()) {
						message = "No online users.";
					} else {
						Collections.sort(onlineList);

						message = "Online users:\n";
						for (String u : onlineList) {
							message += "* " + u + "\n";
						}
					}

					InetAddress address = packet.getAddress();
					int port = packet.getPort();
					buffer = message.getBytes();
					packet = new DatagramPacket(buffer, buffer.length, address, port);
					datagramSocket.send(packet);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	@Command
	public String exit() throws IOException {
		System.out.println("Server is going down for shutdown now!");

		this.serverSocket.close();
		this.datagramSocket.close();

		threadPool.shutdownNow();

		udpThread.interrupt();

		shell.close();

		return "Shutdown completed!";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		SecurityUtils.registerBouncyCastle();

		Chatserver chatserver = new Chatserver(args[0], new Config("chatserver"), System.in, System.out);

		mainThread = new Thread(chatserver);
		mainThread.start();

		try {
			mainThread.join();
		} catch (InterruptedException e) {
			System.out.println("Server root thread is going to die now.");
			Thread.currentThread().interrupt();
		}
	}
}
