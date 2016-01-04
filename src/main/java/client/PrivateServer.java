package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cli.Shell;
import dto.MsgDTO;

public class PrivateServer implements Runnable {
	private final Shell shell;
	private final InetAddress address;
	private final int port;
	
	private final ExecutorService threadPool = Executors.newCachedThreadPool();

	private ServerSocket serverSocket;

	public PrivateServer(InetAddress address, int port, Shell shell) {
		this.address = address;
		this.port = port;
		this.shell = shell;
	}

	public void run() {
		try {
			this.serverSocket = new ServerSocket(port, 4, address);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		Socket socket = null;

		while (!Thread.currentThread().isInterrupted()) {
			try {
				socket = serverSocket.accept();
				threadPool.execute(new ClientHandler(socket));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (socket != null && !socket.isClosed()) {
			try {
				socket.close();
			} catch (IOException e) {
				System.out.println("Failed to close socket");
			}
		}

		Thread.currentThread().interrupt();

	}

	class ClientHandler implements Runnable {
		private ObjectInputStream input;

		ClientHandler(Socket socket) {
			try {
				this.input = new ObjectInputStream(socket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}

			System.out.println("Incoming Handler for private message established.");
		}

		public void run(){
			Object o;
			try {
				while ((o = input.readObject()) != null) {
<<<<<<< HEAD
					if(o instanceof MsgDTO){
						//DO HMAC Stuff
						this.client.getShell().writeLine(((MsgDTO) o).getMessage());
					};
=======
					if (o instanceof MsgDTO) {
						shell.writeLine(((MsgDTO) o).getMessage());
					}
>>>>>>> origin/crypto
				}
			} catch(IOException|ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}	
}

