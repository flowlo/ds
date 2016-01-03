package nameserver;

import java.rmi.RemoteException;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

public class NameserverImpl implements INameserver{

	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String lookup(String username) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
					throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		// TODO Auto-generated method stub
		
	}
	
}
