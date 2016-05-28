/**
 * FileServer.java:<p>
 * @author Darong Leng, Chris Knakal
 * @since 05/24/2016
 **/

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;

/**
 * This class server is used to accept requests from clients. It is used to manages the cache and decides
 * which client will get the file. Some of its features are adding readers to cache objects, sending owner a 
 * writeback request, sending invalidations to readers, and updating the content of the cache
 *
 * It has information of all cached files
 **/

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
    

    /**
     * saves client port
     * instantiates vector
     * add a shut down hook so that when the server is closed with Ctrl^C,
     * the server will saves everything in memory back into disk
     * @param int port is the port that will be used to connect to the client
     * @throws RemoteException
     */
    public FileServer(int port) throws RemoteException {
    	clientPort = port;
    	entryList = new Vector<ServerEntry>();
    	addShutdownHook();
    }

    /**
     * add clientIp to the cache list or
     * gives write permission to the client
     * and finally returns the content to the client
     * 
     * @param String clientIp is IP name of the client
     * @param String fileName is name of the file client wants to access
     * @param String mode is "r" for read, "w" for write
     * @return FileContent if file is found, otherwise null
     * @throws RemoteException
     */
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
				targetEntry = new ServerEntry(fileName);
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
			addReader(targetEntry, clientIp, fileName);
		else 		
			addWriter(targetEntry, clientIp, fileName);

		FileContents outputContent = new FileContents( targetEntry.content );
		System.out.println("Sends content to client " + clientIp);

		return outputContent;
	}

	/**
     * updates the content of cache if method is called
     * 
     * @param String clientIp is IP name of the client
     * @param String fileName is name of the file client wants to access
     * @param FileContents content of the file
     * @return boolean true if updates successfully, false otherwise
     * @throws RemoteException
     */
    public boolean upload( String clientIp, String fileName, FileContents contents ) 
    throws RemoteException 
	{	
		System.out.println("Received upload request from: " + clientIp + ".");
		
		// get file from cache if there is one
		ServerEntry entry = getEntry(fileName);

		// check if file not in cache
		if ( entry == null )  		
			return false;
		
		// return entry.updateContent(contents);
		return updateContent(entry, contents);
	}


	/**
     * add clientIp to the entry's reader list
     * and sets entry's state to READ_SHARED if neccessary
     */
	private void addReader(ServerEntry entry, String clientIp, String fileName) {
		if (!entry.readerList.contains( clientIp )) 
			entry.readerList.add(clientIp); 	
		if (entry.isNotShared())
			entry.stateToReadShared();
	}

	/**
     * it gives client the write permission immediately or
     * has them wait.
     * It sets entry's state to WRITE_SHARED if neccessary
     */
	private void addWriter(ServerEntry entry, String clientIp, String fileName) {
		if (entry.isReadShared() || entry.isNotShared()) {
			entry.setOwner( clientIp );					// updates owner
			entry.stateToWriteShared( );						// updates state
		} else {
			
			/*
				this block sends a writeback request to the file owner.
				if the file owner receives the request and uploads the file back immediately
				then clientIp (requester) doesn't have to wait
			*/
			try {
				ClientInterface fileOwner = connectToClient( entry.owner );
				boolean writebackSuccess = fileOwner.writeback();
				if (writebackSuccess) {
					entry.setOwner( clientIp );
					entry.stateToWriteShared(); 
					return;
				}
			} catch (RemoteException re) {
				System.out.println("Error when asking an owner to write back.");
			}
			
			/*
				this block only runs when the first writeback request returns false
				that means the file owner is still editing the file and will be uploading when done editng
				then clientIp (file requester) will need have to wait.
			*/
			synchronized (this) {
				System.out.println("Entered WRITE synchronization.");
				try {	
					while ( entry.isWriteShared() ) {
						ClientInterface fileOwner = connectToClient( entry.owner );
						fileOwner.writeback();
						System.out.println(clientIp + " is releasing monitor and entering waiting mode.");
						wait();
					}
					System.out.println(clientIp + " uploaded file back.");
					entry.setOwner( clientIp );
					entry.stateToWriteShared();
				} catch (InterruptedException ie) {
					System.out.println("InterruptedException when adding writer.");
				} catch (Exception e) {
					e.printStackTrace(); 
					System.out.println("Something gone terribly wrong when adding writer.");
				}
			}	
		} 
	}


	/**
	 * It updates the content of the cache
	 * sets state to NOT_SHARED, resets the owner, invalidates all readers
	 * It also notify all of the waiting threads
     * return false if update is not successful
     * return true if update is sucessful
     */
	private boolean updateContent(ServerEntry entry, FileContents contents) {
		if ( entry.isNotShared() || entry.isReadShared() )
			return false;
		else { 
			synchronized (this) {
				System.out.println("Entered UPDATE synchronization.");
				entry.content = contents.get();
				entry.stateToNotShared();
				entry.resetOwner();
				invalidateCopies(entry);
				notifyAll();  		// resume the downloads of other threads
			}
			return true;
		}
	}

	/**
	 * It iterates through entry's reader list
	 * It first connect to client via RMI lookup
	 * and then calls client.invalidate()
	 * once done iteration, it removes all element from the reader list
     */
	private void invalidateCopies(ServerEntry entry) {
		Vector<String> readerList = entry.readerList;
		for (int i = 0; i < readerList.size(); i++) {
			try {
				ClientInterface reader = ( ClientInterface )
						Naming.lookup( "rmi://" + readerList.get(i) + ":" + clientPort + "/client" );
				reader.invalidate();
			}catch (Exception e) {
				System.out.println("Error: when invalidating client.");
			}
		}
		readerList.removeAllElements();
	}

	/**
	 * It connects to a clientIp via RMI lookup
     */
	private ClientInterface connectToClient(String clientIp) {
		try {
		    ClientInterface client =  ( ClientInterface )
					Naming.lookup( "rmi://" + clientIp + ":" + clientPort + "/client" );
			return client;
		}catch ( Exception e ) { 
			System.out.println("Error: in connectToClient()");
			return null;
		}
	}

	/**
	 * Iterates throught entryList to see if a file has been cached based on fileName
     */
	private ServerEntry getEntry(String fileName) {
		for (int i = 0; i < entryList.size(); i++) {
			ServerEntry curEntry = entryList.get(i);
			if (curEntry.isFileName(fileName)) {
				return curEntry;
			}
		}
		return null;
	}

	/**
	 * this hook will make sure that when the client shut down the application with
	 * control-c, the server will write all of cached files to the disk
	 */
    private void addShutdownHook() {
    	Runtime.getRuntime().addShutdownHook( new Thread() {
    		public void run() {
    			for (int i = 0; i < entryList.size(); i++) {
    				ServerEntry curEntry = entryList.get(i);
    				writeToDisk(curEntry.getFileName(), curEntry.content);
    			}
    		}
    	});
    }

    /**
	 * write the given content to disk
	 */ 
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


}
