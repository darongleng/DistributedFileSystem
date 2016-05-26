import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;

public class ServerEntry {

	/*
	enum FileState represents file state
	*/
	public enum FileState {
		NOT_SHARED,
		READ_SHARED,
		WRITE_SHARED,
		OWNERSHIP_CHANGE
	}

	protected String fileName; 				// name of the file
	protected FileState state;				// state of the file
	protected String owner;					// store clientIp
	protected byte[] content;				// content of the file
	protected int numOfWriter;	 			// count the number of waiting writers who are waiting
	protected int clientPort = 0;
	protected boolean ready;

	protected Vector<String> readerList;		// clients who involved with the file, 
												// stores clients' IPS

	public ServerEntry(int clientPort, String fileName) throws IOException{
		this.clientPort = clientPort;
		state = FileState.NOT_SHARED;
		readerList = new Vector<String>();
		this.fileName = fileName;
		File file = new File(fileName);
		this.content = Files.readAllBytes( file.toPath() ); // read from disk
	}

	// add client to reader list
	public void addReader(String clientIp, String fileName) {
		if ( !readerList.contains( clientIp ) ) 
			readerList.add(clientIp); 	
		if (state == FileState.NOT_SHARED)
			state = FileState.READ_SHARED;
	}

	// give client the write permission
	public void addWriter(String clientIp, String fileName) {
		if (isReadShared() || isNotShared()) {
			owner = clientIp;							// updates owner
			state = FileState.WRITE_SHARED;			// updates state
			System.out.println("State:" + stateToString(state));
		} else {
			boolean writebackSuccess = false;
			try {
				ClientInterface fileOwner = connectToClient( owner );
				writebackSuccess = fileOwner.writeback();	
			} catch (RemoteException re) {
				System.out.println("Error when asking an owner to write back.");
			}
			
			if (writebackSuccess) {
				owner = clientIp; 
				state = FileState.WRITE_SHARED;		
			}else {
				System.out.println("Enters WRITE synchronization.");
				synchronized (this) {
					System.out.println("In WRITE synchronization.");
					try {	
						System.out.println("State:" + stateToString(state));
						while ( isWriteShared() ) {
							ClientInterface fileOwner = connectToClient( owner );
							fileOwner.writeback();
							System.out.println(clientIp + " releases monitor.");
							wait();
						}
						System.out.println(clientIp + " is done waiting");
						owner = clientIp; 
						state = FileState.WRITE_SHARED;
					} catch (InterruptedException ie) {
						System.out.println("InterruptedException when adding writer.");
					} catch (Exception e) {
						e.printStackTrace(); 
						System.out.println("Something gone terribly wrong when adding writer.");
					}
				}
			}
			
		} 
	}


	public boolean updateContent(FileContents contents) {
		if ( isNotShared() || isReadShared() )
			return false;
		else { 
			System.out.println("Enters UPDATE synchronization.");
			synchronized (this) {
				System.out.println("In UPDATE synchronization.");
				System.out.println("Recieved Content: " + Arrays.toString(contents.get()));
				this.content = contents.get();
				System.out.println("My Content: " + Arrays.toString(this.content));
				invalidateCopies();
				stateToNotShared();
				resetOwner();
				notifyAll();  		// resume the downloads of other threads
			}
			return true;
		}
	}

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

	private void invalidateCopies() {
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

	public boolean isCacheOf(String fileName) {
		return fileName.equals(this.fileName);
	}

	public byte[] getContent() {
		return content;
	}

	public boolean isFileName(String fileName) {
		return fileName.equals(this.fileName);
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public void resetOwner() {
		this.owner = null;
	}

	public void addReader(String readerIp) {
		readerList.add(readerIp);
	}

	public boolean isNotShared() {
		return state == FileState.NOT_SHARED;
	}

	public boolean isReadShared() {
		return state == FileState.READ_SHARED;
	}

	public boolean isWriteShared() {
		return state == FileState.WRITE_SHARED;
	}

	public boolean isOwnershipChanged() {
		return state == FileState.OWNERSHIP_CHANGE;
	}

	public void stateToNotShared() {
		state = FileState.NOT_SHARED;		
	}

	public void stateToReadShared() {
		state = FileState.READ_SHARED;
	}

	public void stateToWriteShared() {
		state = FileState.WRITE_SHARED;	
	}

	public void stateToOwnershipChanged() {
		state = FileState.OWNERSHIP_CHANGE;		
	}

	private String stateToString(FileState state) { 
		if (state == FileState.NOT_SHARED) {
			return "NOT_SHARED";
		} else if (state == FileState.READ_SHARED) {
			return "READ_SHARED";
		} else if (state == FileState.WRITE_SHARED) {
			return "WRITE_SHARED";
		}else {
			return "OWNERSHIP_CHANGE";
		}
	}

	public String toString() {
		return "File Name: " + fileName + "\n" +
			   "State    : " + stateToString(state) + "\n" +
			   "Owner    : " + owner + "\n" + 
			   "Readers  : " + readerList;
	}

}
