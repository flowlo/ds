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

public class SimpleLoginError {
	private static Chatserver server = null; 
	private static Client client = new Client(null, null, null, null);
	
	private static InputStream server_reader = null;
	private static PrintStream server_writer  = null;
	
	private static InputStream client_reader = null;
	private static PrintStream client_writer  = null;
	
	
	@BeforeClass
	public static void beforeAll(){
		byte[] server_in_buffer = new byte[1024];
		byte[] client_in_buffer = new byte[1024];
		
		server_reader = new ByteArrayInputStream(server_in_buffer);
		client_reader = new ByteArrayInputStream(client_in_buffer);
		
		
		
		server = new Chatserver("chatserver",
				new Config("chatserver"), server_reader, server_writer);
		client = new Client("client", new Config("client"), client_reader,
				client_writer);
	}
	
	@Test
	public void test() {
		fail("Not yet implemented");
	}

	
	@AfterClass
	public static void afterAll(){
		
	}
}
