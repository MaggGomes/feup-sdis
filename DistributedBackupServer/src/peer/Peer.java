package peer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

import subprotocols.Backup;
import subprotocols.BackupEnhancement;
import subprotocols.Delete;
import subprotocols.DeleteEnhancement;
import subprotocols.Protocol;
import subprotocols.Reclaim;
import subprotocols.Restore;
import subprotocols.RestoreEnhancement;
import utilities.Utilities;

import channels.BackupChannel;
import channels.ControlChannel;
import channels.RestoreChannel;
import filesystem.BackedUpFile;
import filesystem.Chunk;
import filesystem.FileManager;

public class Peer implements IPeerInterface{

	private String protocolVersion;	
	private String accessPoint;

	private ControlChannel mc;
	private BackupChannel mdb;
	private RestoreChannel mdr;

	private Thread control;
	private Thread backup;
	private Thread restore;

	private String serverID;
	public static final String BACKUP = "Backup/";
	public static final String RESTORED = "Restored/";
	public static final String DATA = "Data/data";
	public static final String enhancedProtocolVersion = "2.0";

	public static void main(String[] args) {
		
		if(args.length != 9){
			System.out.println("Invalid command. Please try again!");			
			System.out.println("USAGE: java Peer <protocol_version> <peer_id> <peer_ap> <MC> <MC port> <MDB> <MDB port> <MDR> <MDR port>");
			System.out.println("protocol_version>: Peer's protocol version");
			System.out.println("<peer_id>: Peer's id");
			System.out.println("<peer_ap>: Peer's access point");
			System.out.println("<MC>: MC address");
			System.out.println("<MC port>: MC port");
			System.out.println("<MDB>: MDB address");
			System.out.println("<MDB port>: MDB port");
			System.out.println("<MDR>: MDR address");
			System.out.println("<MDR port>: MDR port");
			System.out.println("\nExiting...");
			
			return;
		}

		try {
			Peer peer = new Peer(args);	
			IPeerInterface stub =  (IPeerInterface) UnicastRemoteObject.exportObject(peer, 0);
			Registry registry = LocateRegistry.getRegistry();
			registry.bind(peer.getAccessPoint(), stub);
			peer.listen();

		} catch(Exception e) {
			e.printStackTrace();			
		}		
	}

	/**
	 * Peer constructor
	 * 
	 * @param args
	 */
	public Peer(String[] args){
		this.protocolVersion = args[0];
		this.serverID = args[1];
		this.accessPoint = args[2];

		System.out.println("Peer "+this.serverID+" initiated!");

		init(args);
		loadMetadata();
	}

	/**
	 * Connects the peer to the channels
	 * 
	 * @param args
	 */
	public void init(String args[]) {

		try {
			this.mc = new ControlChannel(this, args[3], args[4]);
			this.mdb = new BackupChannel(this, args[5], args[6]);
			this.mdr = new RestoreChannel(this, args[7], args[8]);	

		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		Protocol.start(this);		
	}

	/**
	 * Initializes channels
	 */
	public void listen(){		
		this.mc.listen();
		this.mdb.listen();
		this.mdr.listen();
	}

	/**
	 * Back up a file
	 * 
	 * @param version
	 * @param filePath
	 * @param replicationDeg
	 */
	@Override
	public void backup(String version, String filePath, int replicationDeg) throws RemoteException {		
		if(version.equals("1.0") && protocolVersion.equals("1.0"))
			Backup.backUpFile(filePath, replicationDeg);
		else
			BackupEnhancement.backUpFile(filePath, replicationDeg);	
	}

	/**
	 * Restores a file
	 * 
	 * @param filePath
	 */
	@Override
	public void restore(String version,String filePath) throws RemoteException {
		if(version.equals("1.0") && protocolVersion.equals("1.0"))
			Restore.restoreFile(filePath);
		else
			RestoreEnhancement.restoreFile(filePath);
	}

	/**
	 * Attempts to delete a file
	 * 
	 * @param filePath of the file to be deleted
	 */
	@Override
	public void delete(String version, String filePath) throws RemoteException {
		if(version.equals("1.0") && protocolVersion.equals("1.0"))
			Delete.deleteFile(filePath);
		else
			DeleteEnhancement.deleteFile(filePath);				
	}

	/**
	 * Attempts to reclaim the specified space
	 * 
	 * @param space to be reclaimed
	 */
	@Override
	public void reclaim(long space) throws RemoteException {
		Reclaim.reclaimSpace(space);
	}

	/**
	 * Provides the state of the files in the peer in string format
	 */
	@Override
	public String state() throws RemoteException {		
		return FileManager.getState();
	}

	/**
	 * Saves metadata in non-volatile memory
	 */
	public void saveMetadata(){
		ObjectOutputStream os;		

		try {
			String path = Utilities.createDataPath(this.serverID);
			Path osPath = Paths.get(path);			
			Files.createDirectories(osPath.getParent());			
			File osFile = new File(path);

			os = new ObjectOutputStream(new FileOutputStream(osFile));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		try {
			os.writeObject(FileManager.nameToFileID);
			os.writeObject(FileManager.backedUpFiles);
			os.writeObject(FileManager.filesTrackReplication);
			os.writeObject(FileManager.storedChunks);
			os.writeObject(FileManager.maxStorage);
			os.writeFloat(FileManager.maxStorage);
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

	/**
	 * Loads metadata saved in non-volatile memory
	 */
	@SuppressWarnings("unchecked")
	public void loadMetadata(){
		ObjectInputStream is;		

		try {
			String path = Utilities.createDataPath(this.serverID);
			Path osPath = Paths.get(path);	
			// Verifies if there is any saved metadata
			if(!Files.exists(osPath))
				return;
			
			File isFile = new File(path);

			is = new ObjectInputStream(new FileInputStream(isFile));
		} catch (IOException e) {
			System.out.println("No saved metadata found!");
			return;
		}

		try {
			FileManager.nameToFileID = (ConcurrentHashMap<String, String>) is.readObject();
			FileManager.backedUpFiles = (ConcurrentHashMap<String, BackedUpFile>) is.readObject();
			FileManager.filesTrackReplication = (ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>>) is.readObject();
			FileManager.storedChunks = (ConcurrentHashMap<String, ConcurrentHashMap<Integer, Chunk>>) is.readObject();
			FileManager.maxStorage = (float) is.readObject();
			is.close();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}		
	}

	/**
	 * Gets server ID
	 * 
	 * @return
	 */
	public String getServerID() {
		return serverID;
	}	

	/**
	 * Gets protocol version
	 * 
	 * @return
	 */
	public String getProtocolVersion(){
		return protocolVersion;
	}

	
	/**
	 * Gets peer access point
	 * 
	 * @return
	 */
	public String getAccessPoint() {
		return accessPoint;
	}

	/**
	 * Gets control channel
	 * 
	 * @return
	 */
	public ControlChannel getMc() {
		return mc;
	}

	/**
	 * Gets back up channel
	 * 
	 * @return
	 */
	public BackupChannel getMdb() {
		return mdb;
	}

	/**
	 * Gets restore channel
	 * 
	 * @return
	 */
	public RestoreChannel getMdr() {
		return mdr;
	}

	/**
	 * Gets the control channel thread
	 * 
	 * @return control channel thread
	 */
	public Thread getControl() {
		return control;
	}
	
	/**
	 * Gets the backup channel thread
	 * 
	 * @return backup channel thread
	 */
	public Thread getBackup() {
		return backup;
	}

	/**
	 * Gets the restore channel thread
	 * 
	 * @return restore channel thread
	 */
	public Thread getRestore() {
		return restore;
	}
}
