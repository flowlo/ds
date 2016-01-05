package common;

import java.io.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

public class CipherInputStream extends InputStream {
	private Cipher c;

	private InputStream is;

	// Input and output buffers (we need both, because
	// we'll be performing crypto).
	private byte[] readBuffer = new byte[512];
	private byte[] buffer;

	// Flag to indicate whether we are done reading (input returned -1).
	private boolean done = false;

	// Indices inside buffer that bound the range that's safe to return
	// on read.
	private int from, to = 0;

	private int buffer() throws IOException {
		if (done) {
			return -1;
		}

		int readin = is.read(readBuffer);
		if (readin == -1) {
			done = true;
			try {
				buffer = c.doFinal();
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				e.printStackTrace();
				buffer = null;
			}

			if (buffer == null) {
				return -1;
			} else {
				from = 0;
				to = buffer.length;
				return to;
			}
		}

		try {
			buffer = c.update(readBuffer, 0, readin);
		} catch (IllegalStateException e) {
			e.printStackTrace();
			buffer = null;
		}

		from = 0;
		return (to = buffer == null ? 0 : buffer.length);
	}

	public CipherInputStream(InputStream is, Cipher c) {
		this.is = is;
		this.c = c;
	}

	public int read() throws IOException {
		if (from >= to) {
			int i = 0;
			while (i == 0) {
				i = buffer();
			}
			if (i == -1) {
				return -1;
			}
		}
		return ((int) buffer[from++] & 0xff);
	}

	public int read(byte b[]) throws IOException {
		return read(b, 0, b.length);
	}

	public int read(byte b[], int off, int len) throws IOException {
		if (from >= to) {
			int i = 0;
			while (i == 0) {
				i = buffer();
			}
			if (i == -1) {
				return -1;
			}
		}

		if (len <= 0) {
			return 0;
		}

		int available = to - from;
		if (len < available) {
			available = len;
		}

		if (b != null) {
			System.arraycopy(buffer, from, b, off, available);
		}
		from += available;

		return available;
	}

	public long skip(long n) throws IOException {
		int available = to - from;
		if (n > available) {
			n = available;
		}
		if (n < 0) {
			return 0;
		}
		from += n;
		return n;
	}

	public int available() throws IOException {
		return to - from;
	}

	public void close() throws IOException {
		is.close();
		try {
			// throw away the unprocessed data
			c.doFinal();
		} catch (BadPaddingException | IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		from = 0;
		to = 0;
	}

	public boolean markSupported() {
		return false;
	}
}
