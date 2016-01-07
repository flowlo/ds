package chatserver;

import java.io.*;
import java.net.*;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
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
import nameserver.INameserver;
import nameserver.INameserverForChatserver;
import util.Config;
import util.Keys;
import util.SecurityUtils;

public class Chatserver implements IChatserverCli, Runnable {
	private final Shell shell;
	private final int tcpPort;
	private final int udpPort;
	private final int regPort;
	private final String regHost;
	private final String rootID;
	private final File keyDir;
	private final File keyFile;
	private final Set<User> users = Collections.synchronizedSet(new HashSet<User>());

	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;

	private Thread udpThread;

	private ExecutorService threadPool = Executors.newCachedThreadPool();

	private PrivateKey privateKey;

	private INameserverForChatserver rootNameserver;

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
		tcpPort = config.getInt("tcp.port");
		udpPort = config.getInt("udp.port");
		regHost = config.getString("registry.host");
		regPort = config.getInt("registry.port");
		rootID = config.getString("root_id");
		keyDir = new File(config.getString("keys.dir"));
		keyFile = new File(config.getString("key"));

		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
	}

	public Set<User> getUsers() {
		return users;
	}

	public INameserverForChatserver getRootNameserver() {
		return rootNameserver;
	}

	public Shell getShell() {
		return shell;
	}

	private void initRootNameserver() {
		Registry registry = null;
		try {
			registry = LocateRegistry.getRegistry(regHost, regPort);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		try {
			rootNameserver = (INameserver) registry.lookup(rootID);
		} catch (RemoteException | NotBoundException e) {
			e.printStackTrace();
		}
	}

	private void initKeys() {
		try {
			privateKey = Keys.readPrivatePEM(keyFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		File[] keys = keyDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				// The server will find it's own public key. To avoid that,
				// derive the name of the public key from the name of the
				// private key.
				// This purely relies on convention.
				if (pathname.equals(new File(keyFile.getPath().replaceFirst("\\.pem$", ".pub.pem")))) {
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
	}

	@Override
	public void run() {
		new Thread(this.shell).start();

		initRootNameserver();
		initKeys();

		try {
			this.serverSocket = new ServerSocket(tcpPort);
		} catch (IOException e) {
			System.err.println("Error creating TCP ServerSocket on port: " + tcpPort);
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
			try {
				exit();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		udpThread = new Thread(new UDPThread());
		udpThread.start();

		while (true) {
			try {
				final Socket socket = serverSocket.accept();
				Thread handler = new Thread() {
					@Override
					public void run() {
						setName("handle-" + socket.toString());

						try {
							Session session = null;

							try {
								session = shakeHands(socket);
							} catch (IOException ignored) {
								// Somebody bothered us with a failed attempt to
								// shake hands. Maybe block the source if this
								// happens too often.
								shell.writeLine("Shaking hands with " + socket.toString() + " failed.");
							}

							if (session == null) {
								try {
									socket.close();
								} catch (IOException ignored) {
								}
							}

							session.talk();
						} catch (IOException e) {
							throw new RuntimeException();
						}
					}
				};

				threadPool.execute(handler);
			} catch (IOException e) {
				// Return if the serverSocket raised an IOE on accept(),
				// which is also the case when it's closed.
				return;
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

		message = ("!ok " + params[2] + " " + // Here we return the client
												// challenge.
		challenge + " " + new String(Base64.encode(secret)) + " " + new String(Base64.encode(iv))).getBytes();

		PublicKey publicKey = Keys.readPublicPEM(new File(keyDir, username + ".pub.pem"));

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
		user.addSession(session);
		return session;
	}

	private class UDPThread implements Runnable {
		public void run() {
			byte[] buffer;
			String message;

			while (!Thread.currentThread().isInterrupted()) {
				buffer = new byte[1024];
				try {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					datagramSocket.receive(packet);
					message = new String(packet.getData(), packet.getOffset(), packet.getLength());

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
				} catch (SocketException se) {
					se.printStackTrace();
					if (datagramSocket != null) {
						datagramSocket.close();
					}
				} catch (IOException e) {
				}
			}
		}
	}

	@Override
	@Command
	public String exit() throws IOException {
		// Close serverSocket so that we can't
		// accept new connections and interrupt
		// our listening thread which could be
		// blocking on accept.
		if (serverSocket != null) {
			serverSocket.close();
			serverSocket = null;
		}

		// Attempt to shut down all sessions.
		if (threadPool != null) {
			threadPool.shutdownNow();
			threadPool = null;
		}

		// Close datagramSocket to interrupt udpThread if
		// it is currently blocking on receive. This will
		// likely raise an IOException (also upon further
		// attempts to receive).
		if (datagramSocket != null) {
			datagramSocket.close();
			datagramSocket = null;
		}

		// If closing datagramSocket has not brought down
		// udpThread by now, interrupt it.
		if (udpThread != null && udpThread.isAlive()) {
			udpThread.interrupt();
			udpThread = null;
		}

		if (shell != null) {
			shell.close();
		}

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
		chatserver.run();
	}
}
