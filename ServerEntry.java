/**
 * ServerEntry.java:<p>
 * @author Darong Leng, Chris Knakal
 * @since 05/24/2016
 **/

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * This class is used to store the content of a file. In other words, it acts as a cached.
 * It has information about the file's name, state of cache, owner of cache, a list of readers.
 **/

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
	protected String owner;					// store clientIp
	protected FileState state;				// state of the file
	protected byte[] content;				// content of the file
	protected Vector<String> readerList;		// clients who involved with the file, 
												// stores clients' IPS

	/**
     * sets state to NOT_SHARED
     * instatiate reader list
     * goes read the content from the disk into the memory based on fileName
     * @param fileName name of the cached file
     * @throws IOException if reading from a file went wrong
     */
	public ServerEntry(String fileName) throws IOException{
		this.state = FileState.NOT_SHARED;
		this.readerList = new Vector<String>();
		this.fileName = fileName;
		File file = new File(fileName);
		this.content = Files.readAllBytes( file.toPath() ); // read from disk
	}

	/**
	* @return byte[] contentn of the cache
	*/
	public byte[] getContent() {
		return content;
	}

	/**
	* @return String the name of the cached file
	*/
	public String getFileName() {
		return fileName;
	}

	/**
	* @return true if "fileName" is equal to the name of cache, false otherwise
	*/
	public boolean isFileName(String fileName) {
		return fileName.equals(this.fileName);
	}

	/**
	* @return void set owner
	*/
	public void setOwner(String owner) {
		this.owner = owner;
	}

	/**
	* @return void set owner to null
	*/
	public void resetOwner() {
		this.owner = null;
	}

	/**
	* @return void add readIp to the reader list
	*/
	public void addReader(String readerIp) {
		readerList.add(readerIp);
	}

	/**
	* @return boolean check if cache in is NOT_SHARED state
	*/
	public boolean isNotShared() {
		return state == FileState.NOT_SHARED;
	}

	/**
	* @return boolean check if cache is in READ_SHARED state
	*/
	public boolean isReadShared() {
		return state == FileState.READ_SHARED;
	}

	/**
	* @return boolean check if cache is in WRITE_SHARED state
	*/
	public boolean isWriteShared() {
		return state == FileState.WRITE_SHARED;
	}

	/**
	* @return boolean check if cache is in OWNERSHIP_CHANGE state
	*/
	public boolean isOwnershipChanged() {
		return state == FileState.OWNERSHIP_CHANGE;
	}

	/**
	* @return void change state of cache to NOT_SHARED
	*/
	public void stateToNotShared() {
		state = FileState.NOT_SHARED;		
	}

	/**
	* @return void change state of cache to READ_SHARED
	*/
	public void stateToReadShared() {
		state = FileState.READ_SHARED;
	}

	/**
	* @return void change state of cache to WRITE_SHARED
	*/
	public void stateToWriteShared() {
		state = FileState.WRITE_SHARED;	
	}

	/**
	* @return void change state of cache to OWNERSHIP_SHARED
	*/
	public void stateToOwnershipChanged() {
		state = FileState.OWNERSHIP_CHANGE;		
	}

	/**
	* @return String info of the cache
	*/
	public String toString() {
		return "File Name: " + fileName + "\n" +
			   "State    : " + state + "\n" +
			   "Owner    : " + owner + "\n" + 
			   "Readers  : " + readerList;
	}

}
