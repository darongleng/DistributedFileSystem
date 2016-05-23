import java.rmi.*;

public interface ServerInterface extends Remote {
    public FileContents download( String client, String filename, String mode )
		throws RemoteException;
    public boolean upload( String client, String filename, 
			   FileContents contents ) throws RemoteException;
}
