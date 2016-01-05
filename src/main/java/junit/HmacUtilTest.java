package junit;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import chatserver.Chatserver;
import client.Client;
import util.Config;
import util.HmacUtil;

public class HmacUtilTest {
	private static HmacUtil hmac1;
	private static HmacUtil hmac2;
	private static String testHash;
	
	@BeforeClass
	public static void beforeAll(){
		hmac1 = new HmacUtil("keys/hmac.key");
		hmac2 = new HmacUtil("keys/hmac.key");
		
		testHash = "euxcEDbCRUh2v5pjAPxY9exol6f2YlgS97qg0MjI3vU=";
	}

	@Test
	public void testKeyGeneration(){
		String hash = hmac1.generateHash("TEST");
		assertTrue(testHash.equals(hash));
	}
	
	@Test
	public void test() {
		fail("Not yet implemented");

	}

	
	@AfterClass
	public static void afterAll(){
		
	}
}
