package com.geekcommune.friendlybackup.server;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import com.geekcommune.friendlybackup.FriendlyBackupException;
import com.geekcommune.friendlybackup.server.format.high.ClientUpdate;
import com.geekcommune.identity.PublicIdentity;
import com.geekcommune.identity.PublicIdentityHandle;

/**
 * Represents one of the storage nodes out on FB.
 * @author wurp
 *
 */
public class User {
	private static final Logger log = Logger.getLogger(User.class);
	
	private static final String FORMAT = "yyyy.MM.dd HH:mm:ss z";
	private static final String SEP = "~";
	
	private PublicIdentity pubIdentity;
	private String email;
	private String name;
	private long storageAvailable;
	private Date lastUpdate;
	private InetAddress inetAddress;
	private int port;
	private Eligibility eligibility;
	private PGPPublicKeyRing keyRing;

	public static enum Eligibility {
		CANDIDATE,
		ELIGIBLE,
		IN_CIRCLE,
		REJECTED;
		
		public String toString() {
			return name().toLowerCase();
		}
	}
	
	public User(File homeDirectory, Eligibility eligibility, Date date) {
		// TODO Auto-generated constructor stub
		//read user from the directory
	}

	public User(ClientUpdate cu, PublicIdentity pubIdentity, InetAddress inetAddress, int port, Date date, PGPPublicKeyRing keyRing) {
		this.pubIdentity = pubIdentity;
		this.email = cu.getEmail();
		this.name = cu.getName();
		this.storageAvailable = cu.getStorageAvailable();
		this.lastUpdate = date;
		this.inetAddress = inetAddress;
		this.port = port;
		this.eligibility = Eligibility.CANDIDATE;
		this.keyRing = keyRing;
	}

	public PublicIdentity getPublicIdentity() {
		return pubIdentity;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setStorageAvailable(long storageAvailable) {
		this.storageAvailable = storageAvailable;
	}

	public void setLastUpdate(Date date) {
		this.lastUpdate = date;
	}

	public void setInetAddress(InetAddress inetAddress) {
		this.inetAddress = inetAddress;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getUpdateLogEntry() {
		return pubIdentity.getHandle().getHandleString() + SEP +
			name + SEP +
			email + SEP +
			storageAvailable + SEP +
			new SimpleDateFormat(FORMAT).format(lastUpdate) + SEP +
			inetAddress + SEP +
			port;
	}

	public void save(File root) {
		OutputStream keyRingOut = null;
		BufferedWriter propWriter = null;

		try {
			File directory = getUserDirectory(root, eligibility, pubIdentity.getHandle());
			directory.mkdirs();
			
			File props = new File(directory, "User.properties");
			propWriter = new BufferedWriter(new FileWriter(props));
			propWriter.write("name="+name);
			propWriter.write("email="+email);
			propWriter.write("storageAvailable="+storageAvailable);
			propWriter.write("lastUpdate="+new SimpleDateFormat(FORMAT).format(lastUpdate));
			propWriter.write("inetAddress="+inetAddress.getHostName());
			propWriter.write("port="+port);
			
			File keyRingFile = new File(directory, "pubring.gpg");
			keyRingOut = new BufferedOutputStream(new FileOutputStream(keyRingFile));
			keyRing.encode(keyRingOut);
		} catch (IOException e) {
			new FriendlyBackupException(
					"Could not save user " +
						pubIdentity.getHandle().getHandleString(),
					e);
		} finally {
			try {
				propWriter.close();
			} catch (IOException e) {
				log.error(
						"Could not close properties file for " +
						pubIdentity.getHandle().getHandleString());
			}
			
			try {
				keyRingOut.close();
			} catch (IOException e) {
				log.error(
						"Could not close keyring file for " +
						pubIdentity.getHandle().getHandleString());
			}
		}
	}

	public static File getUserDirectory(
			File root,
			Eligibility eligibility,
			PublicIdentityHandle handle) {
		return new File(
				root,
				eligibility.toString() + File.separatorChar +
					handle.getHandleString());
	}

	public void setPublicIdentity(PublicIdentity pubIdentity) {
		this.pubIdentity = pubIdentity;
	}
}
