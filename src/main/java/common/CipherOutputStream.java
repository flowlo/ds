package common;

import java.io.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

public class CipherOutputStream extends OutputStream {
	private Cipher c;

	private OutputStream os;

	private byte[] buf;

	public CipherOutputStream(OutputStream os, Cipher c) {
		this.os = os;
		this.c = c;
	};

	public void write(int b) throws IOException {
		buf = c.update(new byte[] { (byte) b }, 0, 1);
		if (buf != null) {
			os.write(buf);
			buf = null;
		}
	};

	public void write(byte b[]) throws IOException {
		write(b, 0, b.length);
	}

	public void write(byte b[], int off, int len) throws IOException {
		buf = c.update(b, off, len);
		if (buf != null) {
			os.write(buf);
			buf = null;
		}
	}

	public void flush() throws IOException {
		if (buf != null) {
			os.write(buf);
			buf = null;
		}
		os.flush();
	}

	public void close() throws IOException {
		try {
			buf = c.doFinal();
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
			buf = null;
		}

		try {
			flush();
		} catch (IOException e) {
			e.printStackTrace();
		}

		os.close();
	}
}
