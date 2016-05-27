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
	protected int clientPort = 0;

	protected Vector<String> readerList;		// clients who involved with the file, 
												// stores clients' IPS

	public ServerEntry(int clientPort, String fileName) throws IOException{
		this.clientPort = clientPort;
		this.state = FileState.NOT_SHARED;
		this.readerList = new Vector<String>();
		this.fileName = fileName;
		File file = new File(fileName);
		this.content = Files.readAllBytes( file.toPath() ); // read from disk
	}

	public byte[] getContent() {
		return content;
	}

	public String getFileName() {
		return fileName;
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
