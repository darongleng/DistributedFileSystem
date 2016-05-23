import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;

public class FileClient extends UnicastRemoteObject	implements ClientInterface {

	public static void main( String args[] ) {
		// verify arguments
		int port = 0;
		try {
		    if ( args.length == 2 ) {
			port = Integer.parseInt( args[1] );
			if ( port < 5001 || port > 65535 )
			    throw new Exception( );
		    }
		    else
				throw new Exception( );
		} catch ( Exception e ) {
		    System.err.println( "usage: java Client serverIp port" );
		    System.exit( -1 );
		}

		// server IP
		String serverIp = args[0];

		// set up client registry so that server can find
		try {     
		    startRegistry( port );
		    FileClient clientObject = new FileClient( serverIp, port );
		    Naming.rebind( "rmi://localhost:" + port + "/client", clientObject );
		    clientObject.run();
		    System.out.println( "Client ready." );
		} catch ( Exception e ) {
		    e.printStackTrace( );
		    System.exit( -1 );
		}

    }

    private static void startRegistry( int port ) throws RemoteException {
		try {
		    Registry registry = 
			LocateRegistry.getRegistry( port );
		    registry.list( );  
		}
		catch ( RemoteException e ) { 
		    Registry registry = 
			LocateRegistry.createRegistry( port );
		}
    }


    // -------------------------- CLASS START HERE -----------------------
	/*
	enum FileState represents file state
	*/
	public enum ClientFileState {
		INVALID,
		READ_SHARED,
		WRITE_OWNED,
		RELEASE_OWNERSHIP
	}

	private String fileName;
	private String mode;
	private boolean owner;
	private ClientFileState state;

	private ServerInterface server = null;
	private String username;
	private String myIp;
	private String cached_file_path = "";
	private final String TEMP_DIR = "/tmp/";
	private final String EMACS = "emacs";

	private Process commandProcess; 				// this process is used to exec commands


	// variables for testing
	private boolean doneWriting;

    public FileClient( String serverIp, int port) throws RemoteException {
 		fileName = new String();
 		mode = new String();
 		owner = false;
 		state = ClientFileState.INVALID;   	
 		setupUserInfo();							// get user name 
 		cached_file_path = "/tmp/"+username+".txt";
 		connectToServer(serverIp, port);			// connect to server with rmi lookup
    }



    public boolean invalidate( ) throws RemoteException {
    	System.out.println("Received invalidation request.");
    	this.state = ClientFileState.INVALID;
    	System.out.println("file invalidated.");
    	return true;
    }

    public String stateToString(ClientFileState state) {
    	if (state == ClientFileState.INVALID)
    		return "INVALID";
    	else if (state == ClientFileState.READ_SHARED)
    		return "READ_SHARED";
    	else if (state == ClientFileState.WRITE_OWNED)
    		return "WRITE_OWNED";
    	else
    		return "RELEASE_OWNERSHIP";
    }

    public boolean writeback( ) throws 	RemoteException {

    	System.out.println("Received write back request.");
    	if ( !isProcessAlive(commandProcess) ) {
    		uploadModifiedFile();  // writeback immedietly
    		return true;
    	}

    	// if (doneWriting) {
    	// 	System.out.println("I'm done writing");
    	// 	uploadModifiedFile();  // writeback immedietly
    	// 	return true;
    	// }

    	System.out.println("I'm not done writing");
    	state = ClientFileState.RELEASE_OWNERSHIP;
    	System.out.println("Still editing. Will write back soon.");
    	return false;
    }

    private void setupUserInfo() {
    	try {    		
    		username = System.getProperty("user.name");
    		myIp = InetAddress.getLocalHost().getHostName();
    	}catch(Exception e) {
    		System.out.println("Error: in getUserName()");
    	}
    }

    public void run() {
    	while (true) {
    		// gets file name
    		System.out.print("File name: ");
    		Scanner reader = new Scanner(System.in);
    		fileName = reader.nextLine();

    		// gets mode -- "r" or "w"
    		System.out.print("How(r/w): ");
    		mode =  reader.nextLine();

    		// check mode
    		if (!(mode.equals("r") || mode.equals("w"))) {
    			System.out.println("Uknown download mode.");
    			continue;
    		}

    		// uploads local cached file before read/write a new file
    		if (state == ClientFileState.WRITE_OWNED && !fileName.equals(this.fileName)) {
    			uploadModifiedFile();
    			state = ClientFileState.INVALID;
    		}

    		// if not in cache
    		if ( !this.fileName.equals(fileName) || state == ClientFileState.INVALID) {
    			System.out.println("goes in here");
    			boolean success = downloadFileFromServer(fileName, mode);
    			if (!success) {
    				System.out.println("downloadFileFromServer() fails or file doesn't exist.");
    				continue;
    			}
    		}
    			
    		if ( mode.equals("r") ) {
    			if (state == ClientFileState.INVALID)
    				state = ClientFileState.READ_SHARED;
    			openFile();
    			// readFile();
    		}else { // mode is "w"
    			if (state == ClientFileState.INVALID || state == ClientFileState.READ_SHARED)
    				state = ClientFileState.WRITE_OWNED;
    			doneWriting = false;
    			openFile();
				// readFile();		
    		}
    	}
    }

    private void writeToDisk(byte[] content) {
    	try {
    		changeFileAccess("w");		//change file access before write, otherwise expception is thrown
			FileOutputStream output = new FileOutputStream(cached_file_path);   
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

    // change file access mode
    // ex. chmod 400 file.txt
    private void changeFileAccess(String mode) {
    	try {
    		Runtime runtime = Runtime.getRuntime();
    		int intMode = 0;
    		if (mode.equals("r"))
    			intMode = 400; 		// read only
    		else if (mode.equals("w"))
    			intMode = 600; 		// read only
    		else 
    			return;

	    	Process process = runtime.exec("chmod " + intMode  + " " + cached_file_path);
	    	process.waitFor();	
    	}catch (Exception e) {
    		System.out.println("Error: in changeFileAccess()");
    	}
    }

    // blocks until done editing
    // open the file from /tmp/filename directory "emacs"
    private void openFile() {
    	try {
    		Runtime runtime = Runtime.getRuntime();
	    	commandProcess = runtime.exec(EMACS + " " + cached_file_path);
	    	commandProcess.waitFor();
	    	// check to see if server needs the file
	    	if (state == ClientFileState.RELEASE_OWNERSHIP)
	    		uploadModifiedFile();
	    	
    	}catch (Exception e) {
    		System.out.println("Error: in openFile()");
    	}
    }

    // open the file from /tmp/filename directory with "cat"
    private void readFile() {
    	try {
    		Thread.sleep(3000);
    		Runtime runtime = Runtime.getRuntime( );		// get runTime
		    commandProcess = runtime.exec("cat " + cached_file_path);
		    InputStream input = commandProcess.getInputStream();   
		    BufferedReader bufferedInput
					= new BufferedReader( new InputStreamReader( input ) );

			String line;
			System.out.println("File Content:");
		    while ( ( line = bufferedInput.readLine( ) ) != null ) { // reads each line
				System.out.println(line);
		    }
		    System.out.println();
		    // check to see if server needs the file
		    if (state == ClientFileState.RELEASE_OWNERSHIP)	{
	    		uploadModifiedFile();
	    	}
	    	doneWriting = true;
    	}catch (Exception e) {
    		System.out.println("Error: in readFile()");
    	}
    }

    // read content from a file on disk
    // fileName may also include path to the file
    private byte[] getFileContent(String fileName) {
    	try {
    		File file = new File(fileName);
			if ( !file.exists() )
				return null;
			byte[] content = Files.readAllBytes( file.toPath() ); // read from disk
			return content;
    	} catch (Exception e) {
    		System.out.println("Error: in getFileContent()");
    		return null;
    	}
    } 

    // uploads file to the server
    private void uploadModifiedFile() {
    	try {	    		
    		FileContents fileContent = new FileContents( getFileContent(cached_file_path) );
    		state = ClientFileState.INVALID;
    		server.upload(myIp, fileName, fileContent);
    		System.out.println("Uploaded modified file back to the server.");
    	} catch (Exception e) {
    		e.printStackTrace();
    		System.out.println("Error: in uploadModifiedFile().");
    	}
    }   

    // connect to server
    private void connectToServer(String serverIp, int port) {
    	// connect to server
		try {
		    server =  ( ServerInterface )
					Naming.lookup( "rmi://" + serverIp + ":" + port + "/server" );
			System.out.println("Server found");
		}catch ( Exception e ) { 
			System.out.println("Error: in connectToServer()");
		}
    }

    // download file from server
    private boolean downloadFileFromServer(String fileName, String mode) {
    	try {
    		// download content from server
    		FileContents fileContent = server.download(myIp, fileName, mode);
    		// if file is not found at the server
			if (fileContent == null)   {
				System.out.println("content is null");
				return false;
			}

			writeToDisk(fileContent.get());			// cache file to disk
			changeFileAccess(mode);					// change file access mode accordingly

			this.fileName = fileName;				
			this.mode = mode;						

			if (mode.equals("r") )
				this.state = ClientFileState.READ_SHARED;
			else if (mode.equals("w") && this.state != ClientFileState.RELEASE_OWNERSHIP)// mode == "w"
				this.state = ClientFileState.WRITE_OWNED;

    		return true;
    	}catch (Exception e) {
    		System.out.println("Error: in downloadFileFromServer()");
    		return false;
    	}
    }


    private boolean isProcessAlive(Process process) {
    	try {
    		int exitValue = process.exitValue();
    		return exitValue != 0; 
    	} catch (IllegalThreadStateException e) {
    		// if process is still running, this execption is thrown
    		return true; // to indicate process is still alive
    	}
    }

    public String toString() {
    	return "fileName: " + fileName + "\n" +
	    	"mode: " + mode + "\n" +
	    	"owner: " + owner +	"\n" + 
	    	"state: " + state + "\n" +
	    	"username: " + username; 
    }

}
