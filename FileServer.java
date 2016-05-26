import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;

public class FileServer extends UnicastRemoteObject implements ServerInterface {

	public static void main( String args[] ) {
		// verify arguments
		int port = 0;
		try {
		    if ( args.length == 1 ) {
			port = Integer.parseInt( args[0] );
			if ( port < 5001 || port > 65535 )
			    throw new Exception( );
		    }
		    else 
				throw new Exception( );
		} catch ( Exception e ) {
		    System.err.println( "usage: java Server port" );
		    System.exit( -1 );
		}

		try {     
		    startRegistry( port );
		    FileServer serverObject = new FileServer( port );
		    Naming.rebind( "rmi://localhost:" + port + "/server", serverObject );
		    System.out.println( "Server ready." );
		} catch ( Exception e ) {
		    e.printStackTrace( );
		    System.exit( -1 );
		}
    }

    private static void startRegistry( int port ) throws RemoteException {
		try {
		    Registry registry = LocateRegistry.getRegistry( port );
		    registry.list( );  
		}
		catch ( RemoteException e ) { 
		    Registry registry = LocateRegistry.createRegistry( port );
		}
    }




   	// ------------------------- SERVER STARTS HERE -----------------------------
    private int clientPort = 0;					// we'll use this port for connection
	private Vector<ServerEntry> entryList; 		// stores files that have been read
    
    public FileServer(int port) throws RemoteException {
    	clientPort = port;
    	entryList = new Vector<ServerEntry>();
    	addShutDownHook();
    }

    private void addShutDownHook() {
    	Runtime.getRuntime().addShutdownHook( new Thread() {
    		public void run() {
    			for (int i = 0; i < entryList.size(); i++) {
    				ServerEntry curEntry = entryList.get(i);
    				writeToDisk(curEntry.getFileName(), curEntry.getContent());
    			}
    		}
    	});
    }


    // write the given content to disk
    private void writeToDisk(String fileName, byte[] content) {
        try {
            FileOutputStream output = new FileOutputStream(fileName);   
            output.write(content); 
            output.close(); 
        }catch(IOException ioException) {
            System.out.println("Error: when writing file.");
            ioException.printStackTrace();
        }catch(Exception e) {
            System.out.println("Error: in writeToDisk()");
            e.printStackTrace();
        }
    }


    public FileContents download( String clientIp, String fileName, String mode )
		throws RemoteException 
	{
		if (mode.equals("r"))
			System.out.println("Read-Download Request From: " + clientIp + ", fileName: " + fileName + ", mode: " + mode + ".");
		else 
			System.out.println("Write-Download Request From: " + clientIp + ", fileName: " + fileName + ", mode: " + mode + ".");

		// check if the mode is valid
		if ( !mode.equals("r") && !mode.equals("w") ) {
			System.out.println(mode + ": Unknown download mode. Download request not served.");
			return null;
		}

		ServerEntry targetEntry = getEntry(fileName);
		// check if file is not in cache
		if ( targetEntry == null ) {
			// load data of filefrom disk to memory 
			try {
				targetEntry = new ServerEntry(clientPort, fileName);
				entryList.add(targetEntry);
			} catch (IOException ie) {
				System.out.println("Error: IOException in download()");
				return null;
			} catch (Exception e) {
				System.out.println("Error: in download()");
				return null;
			}
		}
			
		if (mode.equals("r")) 
			targetEntry.addReader(clientIp, fileName);
		else 		
			targetEntry.addWriter(clientIp, fileName);

		FileContents outputContent = new FileContents( targetEntry.getContent() );

		System.out.println("Sent to client " + clientIp);
		return outputContent;
	}



    public boolean upload( String clientIp, String fileName, FileContents contents ) 
    	throws RemoteException 
	{	
		System.out.println("Received upload request from: " + clientIp + ".");
		
		// get file from cache if there is one
		ServerEntry entry = getEntry(fileName);

		// check if file not in cache
		if ( entry == null )  		
			return false;
		
		return entry.updateContent(contents);
	}


	private ServerEntry getEntry(String fileName) {
		for (int i = 0; i < entryList.size(); i++) {
			ServerEntry curEntry = entryList.get(i);
			if (curEntry.isFileName(fileName)) {
				return curEntry;
			}
		}
		return null;
	}


}
