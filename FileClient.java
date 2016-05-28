/**
 * FileClient.java:<p>
 * @author Darong Leng, Chris Knakal
 * @since 05/24/2016
 **/

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;

/**
 * This class is run by a user. Once it is run, it will keep asking the user what files they want to open.
 * Some of its features are writing file back to the server and invalidating the current files,
 * and openinng up emacs.
 **/

public class FileClient extends UnicastRemoteObject	implements ClientInterface {

	public static void main( String args[] ) {
		// verify arguments
		int port = 0;
		try {
		    if ( args.length == 2 ) {
			port = Integer.parseInt( args[1] );
			if ( port < 5001 || port > 65535 )
			    throw new Exception( );
		    }else
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

	private String fileName;                  // names of cached file
	private String mode;                      // mode of the file
	private boolean owner;                    // tells if this client is the owner
	private ClientFileState state;            // state of the file
	
	private String username;                  // name of the client
	private String myIp;                      // IP of client
	private String cached_file_path = "";     // cached file path
    private ServerInterface server = null;    // will store RMI server object

    private boolean doneWriting;            // indicates whether 

    private final String TEMP_DIR = "/tmp/";  // path to /tmp
    private final String EMACS = "emacs";     // emacs command

    /**
     * it set state of cached file to INVALID
     * sets up username and user IP 
     * add a shut down hook so that when the server is closed with Ctrl^C,
     * the client will sends its cached file back to the server.
     * @param String serverIp is IP of the server
     * @param int port is the port that will be used to connect to the client
     * @throws RemoteException
     */   
    public FileClient( String serverIp, int port) throws RemoteException {
 		fileName = null;
 		mode = null;
 		owner = false;
 		state = ClientFileState.INVALID;   	
 		setupUserInfo();							// get user name 
 		cached_file_path = TEMP_DIR+username+".txt";
 		connectToServer(serverIp, port);			// connect to server with rmi lookup
        addShutdownHook();
    }

    /**
     * it sets the state of the cache to INVALID
     * @throw RemoteException
     */   
    public boolean invalidate( ) throws RemoteException {
    	System.out.println("Received invalidation request.");
    	this.state = ClientFileState.INVALID;
    	return true;
    }

    /**
     * it sets the state of the cache to RELEASE_OWNERSHIP
     * if the client is already done writing, it uploads the file back
     * to the server immediately
     * @throw RemoteException
     */
    public boolean writeback( ) throws 	RemoteException {

        if (doneWriting) {
            System.out.println("Received write back request. Writing file back to server.");
            uploadModifiedFile();  // writeback immedietly
            return true;
        }

        state = ClientFileState.RELEASE_OWNERSHIP;
        System.out.println("Received write back request. Still editing the file.");

        return false;
    }


    /**
     * It enters a loop that keep asking for file name and access mode
     * before sending any request to the server, it first checks user inputs
     * it checks whether access mode is known.
     * it checks whether the current cached file needs to be uploaded to server.
     * it checks if the requested file is already cached 
     * if it is not, it will download it from the server.
     * it sets state of the cache accordingly
     */
    public void run() {
    	while (true) {
    		// gets file name
    		System.out.print("File name: ");
    		Scanner reader = new Scanner(System.in);
    		String requestName = reader.nextLine();

    		// gets mode -- "r" or "w"
    		System.out.print("How(r/w): ");
    		String requestMode =  reader.nextLine();

    		// check mode
    		if (!(requestMode.equals("r") || requestMode.equals("w"))) {
    			System.out.println("Uknown download mode.");
    			continue;
    		}

    		// uploads local cached file before read/write a new file
    		if (state == ClientFileState.WRITE_OWNED && !requestName.equals(fileName)) {
    			uploadModifiedFile();
    			state = ClientFileState.INVALID;
    		}

    		// if not in cache
    		if ( !requestName.equals(fileName) || 
                state == ClientFileState.INVALID  ||
                (requestMode.equals("w") && state == ClientFileState.READ_SHARED)) {

                doneWriting = false;     // client just begins writing
                boolean success = downloadFileFromServer(requestName, requestMode);
                System.out.println("Received dowonload response from server.");                
    			if (!success) {
    				System.out.println("downloadFileFromServer() fails or file doesn't exist.");
    				continue;
    			}
    		}

            fileName = requestName;
            mode = requestMode;
    			
    		if ( mode.equals("r") ) {
    			if (state == ClientFileState.INVALID)
    				state = ClientFileState.READ_SHARED;
    			openFile();
    		}else { // mode is "w"
    			if (state == ClientFileState.INVALID || state == ClientFileState.READ_SHARED)
    				state = ClientFileState.WRITE_OWNED;    			
    			openFile();		
    		}
    	}
    }


    // ---------------------- PRIVATE FUNCTIONS --------------------------
    /**
     * it first reads the content from the disk
     * it then uploads file to the server
     */
    private void uploadModifiedFile() {
    	try {	    		
    		FileContents fileContent = new FileContents( getFileContent(cached_file_path) );
    		state = ClientFileState.INVALID;
    		server.upload(myIp, fileName, fileContent);
    		System.out.println("Uploaded modified file back to the server.");
    	} catch (Exception e) {
    		System.out.println("Error: in uploadModifiedFile().");
    	}
    }   

    /**
     * it sends a download request to the server
     * if server doesn't have the file it will return NULL
     * if it gets some content from the server it will write the client disk
     * and changes access mode accordingly
     */
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

    /**
     * write the given content to disk
     */
    private void writeToDisk(byte[] content) {
        try {
            changeFileAccess("w");      //change file access before write, otherwise expception is thrown
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

     /**
     * read content from a file on disk
     */   
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

    /**
     * it changes file access mode by using chmod
     * a process will be created to change file access mode
     */   
    private void changeFileAccess(String mode) {
        try {
            Runtime runtime = Runtime.getRuntime();
            int intMode = 0;
            if (mode.equals("r"))
                intMode = 400;      // read only
            else if (mode.equals("w"))
                intMode = 600;      // read only
            else 
                return;

            Process process = runtime.exec("chmod " + intMode  + " " + cached_file_path);
            process.waitFor();  
        }catch (Exception e) {
            System.out.println("Error: in changeFileAccess()");
        }
    }

    /**
     * it opens a file with emacs
     * a process will be created when opening emacs
     * it is blocked until the user closes the emacs
     * and sets doneWriting field to true
     * it uploads file back to server if neccesary
     */    
    private void openFile() {
        try {
            Runtime runtime = Runtime.getRuntime();
            Process commandProcess = runtime.exec(EMACS + " " + cached_file_path);
            commandProcess.waitFor();
            // check to see if server needs the file
            if (state == ClientFileState.RELEASE_OWNERSHIP) {
                uploadModifiedFile();
            }
            doneWriting = true;
        }catch (Exception e) {
            System.out.println("Error: in openFile()");
        }
    }

    /**
     * open the file from /tmp/filename directory with "cat"
     * this function is used for testing only 
     */
    private void readFile() {
        try {
            Thread.sleep(3000);
            Runtime runtime = Runtime.getRuntime( );        // get runTime
            Process commandProcess = runtime.exec("cat " + cached_file_path);
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
            if (state == ClientFileState.RELEASE_OWNERSHIP) {
                uploadModifiedFile();
            }
            doneWriting = true;
        }catch (Exception e) {
            System.out.println("Error: in readFile()");
        }
    }

    /**
     * connects to the server via RMI lookup and saves server proxy to
     * server field
     */
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

    /**
     * gets user name from the system and saves it to a username field
     * get user IP and saves it to myIp field
     */
    private void setupUserInfo() {
        try {           
            username = System.getProperty("user.name");
            myIp = InetAddress.getLocalHost().getHostName();
        }catch(Exception e) {
            System.out.println("Error: in getUserName()");
        }
    }

    /**
     * converts FileState to String
     * helps with testing
     */
    private String stateToString(ClientFileState state) {
        if (state == ClientFileState.INVALID)
            return "INVALID";
        else if (state == ClientFileState.READ_SHARED)
            return "READ_SHARED";
        else if (state == ClientFileState.WRITE_OWNED)
            return "WRITE_OWNED";
        else
            return "RELEASE_OWNERSHIP";
    }

    /**
     * this hook will make sure that when the client shut down the application with
     * control-c, the server will write all of cached files to the disk
     */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook( new Thread() {
            public void run() {
                if (state == ClientFileState.WRITE_OWNED)
                    uploadModifiedFile();
            }   
        });
    }

    public String toString() {
    	return "fileName: " + fileName + "\n" +
	    	"mode: " + mode + "\n" +
	    	"owner: " + owner +	"\n" + 
	    	"state: " + state + "\n" +
	    	"username: " + username; 
    }

}
