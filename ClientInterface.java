import java.rmi.*;

public interface ClientInterface extends Remote {
    public boolean invalidate( ) throws RemoteException;
    public boolean writeback( ) throws 	RemoteException;
}
