package com.geekcommune.friendlybackup.server;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import com.geekcommune.friendlybackup.FriendlyBackupException;
import com.geekcommune.friendlybackup.server.format.high.ClientUpdate;
import com.geekcommune.identity.EncryptionUtil;
import com.geekcommune.identity.PublicIdentity;
import com.geekcommune.identity.PublicIdentityHandle;
import com.geekcommune.util.FileUtil;

/**
 * Represents one of the storage nodes out on FB.
 * @author wurp
 *
 */
public class User {
	private static final Logger log = Logger.getLogger(User.class);
	
	private static final String USER_PROPERTIES_FILE_NAME = "User.properties";
	private static final String PUB_KEYRING_FILE_NAME = "pubring.gpg";

	private static final String PORT_KEY = "port";
	private static final String INET_ADDRESS_KEY = "inetAddress";
	private static final String LAST_UPDATE_KEY = "lastUpdate";
	private static final String STORAGE_KEY = "storageAvailable";
	private static final String EMAIL_KEY = "email";
	private static final String NAME_KEY = "name";

	private static final String EQ = "=";
	private static final String SEP = "~";

	private static final String FORMAT = "yyyy.MM.dd HH:mm:ss z";
	
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
		/**
		 * someone who could become eligible if they are up w/ sufficient storage consistently enough
		 */
		CANDIDATE, 
		/**
		 * someone who is ready to be in a backup circle, but has not been placed in one yet
		 */
		ELIGIBLE,
		/**
		 * someone who is currently in a backup circle
		 */
		IN_CIRCLE,
		/**
		 * anyone who has been taken out of being a candidate for backups for some reason.
		 * Perhaps they are down too consistently, or they've abused the system somehow.
		 */
		REJECTED;
		
		public String toString() {
			return name().toLowerCase();
		}
	}
	
	public User(File homeDirectory, Eligibility eligibility) throws FriendlyBackupException {
		this.eligibility = eligibility;

		Reader rdr = null;
		try {
			rdr = new BufferedReader(new FileReader(new File(homeDirectory, USER_PROPERTIES_FILE_NAME)));
			Properties props = new Properties();
			props.load(rdr);
			
			name = props.getProperty(NAME_KEY);
			email = props.getProperty(EMAIL_KEY);
			storageAvailable = Long.parseLong(props.getProperty(STORAGE_KEY));
			lastUpdate = new SimpleDateFormat(FORMAT).parse(props.getProperty(LAST_UPDATE_KEY));
			inetAddress = InetAddress.getByName(props.getProperty(INET_ADDRESS_KEY));
			port = Integer.parseInt(props.getProperty(PORT_KEY));
			
			File keyRingFile = new File(homeDirectory, PUB_KEYRING_FILE_NAME);
			byte[] publicKeyRingData =
				FileUtil.instance().getFileContents(keyRingFile);
			keyRing = makeKeyRing(publicKeyRingData);
			
			pubIdentity = getPublicIdentity();
		} catch (IOException e) {
			throw new FriendlyBackupException(
					"Could not load friend from " + homeDirectory,
					e);
		} catch (ParseException e) {
			throw new FriendlyBackupException(
					"Invalid date while reading friend from " + homeDirectory,
					e);
		} finally {
			FileUtil.instance().close(
					rdr,
					"Failed to close prop reader",
					log);
		}
	}

	public User(
			ClientUpdate cu,
			PublicIdentity pubIdentity,
			InetAddress inetAddress,
			int port,
			Date date,
			PGPPublicKeyRing keyRing) {
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

	public void setPublicIdentity(PublicIdentity pubIdentity) {
		this.pubIdentity = pubIdentity;
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
		return "User changed" + SEP + 
			pubIdentity.getHandle().getHandleString() + SEP +
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
			
			File props = new File(directory, USER_PROPERTIES_FILE_NAME);
			propWriter = new BufferedWriter(new FileWriter(props));
			propWriter.write(NAME_KEY+EQ+name);
			propWriter.write(EMAIL_KEY+EQ+email);
			propWriter.write(STORAGE_KEY+EQ+storageAvailable);
			propWriter.write(LAST_UPDATE_KEY+EQ+new SimpleDateFormat(FORMAT).format(lastUpdate));
			propWriter.write(INET_ADDRESS_KEY+EQ+inetAddress.getHostName());
			propWriter.write(PORT_KEY+EQ+port);
			
			File keyRingFile = new File(directory, PUB_KEYRING_FILE_NAME);
			keyRingOut = new BufferedOutputStream(new FileOutputStream(keyRingFile));
			keyRing.encode(keyRingOut);
		} catch (IOException e) {
			new FriendlyBackupException(
					"Could not save user " +
						pubIdentity.getHandle().getHandleString(),
					e);
		} finally {
			FileUtil.instance().close(
					propWriter,
					"Could not close keyring file for " +
						pubIdentity.getHandle().getHandleString(),
					log);
			
			FileUtil.instance().close(
					keyRingOut,
					"Could not close keyring file for " +
						pubIdentity.getHandle().getHandleString(),
					log);
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

	public static PGPPublicKeyRing makeKeyRing(byte[] publicKeyRingData)
			throws FriendlyBackupException {
		try {
			return new PGPPublicKeyRing(publicKeyRingData);
		} catch (IOException e) {
			throw new FriendlyBackupException(
					"Could not make public identity",
					e);
		}
	}


	public static PublicIdentity makePublicIdentity(PGPPublicKeyRing keyRing)
		throws FriendlyBackupException {
		try {
			return new PublicIdentity(
					keyRing,
					new PublicIdentityHandle(
							EncryptionUtil.instance().findFirstSigningKey(keyRing),
							EncryptionUtil.instance().findFirstEncryptingKey(keyRing)
							));
		} catch (FriendlyBackupException e) {
			throw new FriendlyBackupException(
					"Could not make public identity",
					e);
		} catch (PGPException e) {
			throw new FriendlyBackupException(
					"Could not make public identity",
					e);
		}
	}
}
