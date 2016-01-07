package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cli.Shell;
import util.HmacUtil;

public class PrivateServer implements Runnable {
	private final Shell shell;
	private final InetAddress address;
	private final int port;
	private final HmacUtil hmac;

	private final ExecutorService threadPool = Executors.newCachedThreadPool();

	private ServerSocket serverSocket;

	public PrivateServer(InetAddress address, int port, Shell shell, HmacUtil hmac) {
		this.address = address;
		this.port = port;
		this.shell = shell;
		this.hmac = hmac;
	}

	public void run() {
		try {
			this.serverSocket = new ServerSocket(port, 4, address);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		Socket socket = null;

		while (true) {
			try {
				socket = serverSocket.accept();
				threadPool.execute(new ClientHandler(socket.getInputStream(), socket.getOutputStream()));
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
	}

	public void close() {
		try {
			serverSocket.close();
		} catch (IOException ignored) {}

		threadPool.shutdownNow();
	}

	class ClientHandler implements Runnable {
		private final InputStream is;
		private final OutputStream os;
		private final byte[] buf = new byte[1024];

		ClientHandler(InputStream is, OutputStream os) {
			this.is = is;
			this.os = os;
		}

		public void run() {
			try {
				int len = is.read(buf);

				String message = new String(buf, 0, len);
				String hash = message.substring(0, 44);
				String payload = message.substring(45);

				if (!payload.startsWith("!msg ")) {
					shell.writeLine("Received bogus message. Ignoring.");
				} else {
					message = payload.substring(5);

					if (hmac.checkHash(payload, hash)) {
						shell.writeLine("Received message: " + message);
						message = hmac.prependHash("!ack");
					} else {
						message = hmac.prependHash("!tampered " + message);
					}

					os.write(message.getBytes());
					os.flush();
				}

				is.close();
				os.close();
			} catch(IOException e) {
				throw new RuntimeException(e);
			}
		}
	}	
}

