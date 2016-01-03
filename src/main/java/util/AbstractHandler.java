package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public abstract class AbstractHandler implements Runnable {
	protected Socket socket;
	protected BufferedReader reader;
	protected PrintWriter writer;
	
	public AbstractHandler(Socket socket) {
		this.socket = socket;

		try {
			this.reader = new BufferedReader(
				new InputStreamReader(socket.getInputStream())
			);
			this.writer = new PrintWriter(socket.getOutputStream());
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public abstract void run();
}
