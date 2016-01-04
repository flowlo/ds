package util;

import java.io.File;
import java.io.IOException;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;

import java.util.Base64;


/**
 * Helper Class to generate/check Hmac Hash
 * @author Nikloaus Laessig, Lorenz Leutgeb, Christoph Gwihs
 *
 */
public class HmacUtil {
	private static String algorithmen = "HmacSHA256";
	
	private Mac hmac = null;
	
	public HmacUtil(String keyFile){
		File file = new File(keyFile);
		Key key = null;
		try {
			key = Keys.readSecretKey(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			hmac = Mac.getInstance(algorithmen);
			hmac.init(key);
		}catch(NoSuchAlgorithmException e){
			e.printStackTrace();
		}catch(InvalidKeyException ie){
			ie.printStackTrace();
		}
	}
	
	/**
	 * Generates an base64 Encoded HmacSHA256 Hash for given 
	 * message
	 * @param message
	 * @return
	 */
	public String generateHash(String message){
		String hashB64 = Base64.getEncoder().encodeToString(generateHashAsByteArray(message));
		return hashB64;
	}
	
	/**
	 * 
	 * @param message
	 * @return
	 */
	private byte[] generateHashAsByteArray(String message){
		hmac.update(message.getBytes());
		byte[] hash = hmac.doFinal();

		return hash;
	}
	
	/**
	 * Checks if a message has the same hmac hash as the provided one
	 * @param message
	 * @param hash
	 * @return true  - if generated and provided hash are equals
	 * 		   false - otherwise
	 */
	public Boolean checkHash(String message, String hash){
		byte[] recceivedHash = Base64.getDecoder().decode(hash);
		if(MessageDigest.isEqual(this.generateHashAsByteArray(message),recceivedHash)){
			return true;
		}else{
			return false;
		}
	}
}