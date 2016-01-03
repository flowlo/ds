package util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;

import java.security.SecureRandom;
import java.security.Security;

/**
 * Please note that this class is not needed for Lab 1, but can later be
 * used in Lab 2.
 * 
 * Provides security provider related utility methods.
 */
public final class SecurityUtils {

	public static final String ASYMMETRIC_SPEC = "RSA/NONE/OAEPWithSHA256AndMGF1Padding";
	public static final String SYMMETRIC_SPEC = "AES/CTR/NoPadding";

	static {
		r = new SecureRandom();
	}

	private static SecureRandom r;

	/**
	 * Registers the {@link BouncyCastleProvider} as the primary security
	 * provider if necessary.
	 */
	public static synchronized void registerBouncyCastle() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.insertProviderAt(new BouncyCastleProvider(), 0);
		}
	}

	public static String randomBytesEncoded(int n) {
		byte[] tmp = new byte[n];
		r.nextBytes(tmp);
		return new String(Base64.encode(tmp));
	}

	public static byte[] randomBytes(int n) {
		byte[] tmp = new byte[n];
		r.nextBytes(tmp);
		return tmp;
	}
}
