package com.geekcommune.friendlybackup.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;

import org.apache.log4j.Logger;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import com.geekcommune.friendlybackup.FriendlyBackupException;
import com.geekcommune.friendlybackup.server.User.Eligibility;
import com.geekcommune.friendlybackup.server.format.high.ClientUpdate;
import com.geekcommune.identity.EncryptionUtil;
import com.geekcommune.identity.PublicIdentity;
import com.geekcommune.identity.PublicIdentityHandle;

/**
 * Provides services to manipulate users (friends/storage nodes) on a Friendly Backup server.
 * FB clients connect to a FB server when they start up, and the FB server tracks which clients
 * are up consistently, and uses that info to match friends with one another so they get
 * consistent storage. 
 * @author wurp
 *	
 */
public class UserManager {
	private Logger updateLog = Logger.getLogger(UserManager.class.getName() + ".Updates");

	private static UserManager instance;

	public static UserManager instance() {
		return instance;
	}

	private File root;

	/**
	 * Handles an update to the user data.
	 * @param cu
	 * @param date
	 * @param inetAddress
	 * @param originNodePort
	 * @throws FriendlyBackupException 
	 */
	public void handleUpdate(ClientUpdate cu, Date date,
			InetAddress inetAddress, int originNodePort) throws FriendlyBackupException {
		PGPPublicKeyRing keyRing = makeKeyRing(cu.getPublicKeyRingData());
		PublicIdentity pubIdentity = makePublicIdentity(keyRing);
		
		User user = getUser(pubIdentity, keyRing, cu, inetAddress, originNodePort, date);
		if( cu.verifySignature(user.getPublicIdentity()) ) {
			user.setEmail(cu.getEmail());
			user.setName(cu.getName());
			user.setStorageAvailable(cu.getStorageAvailable());
			user.setLastUpdate(date);
			user.setInetAddress(inetAddress);
			user.setPort(originNodePort);
			user.setPublicIdentity(pubIdentity);
			
			updateLog.info(user.getUpdateLogEntry());
			
			user.save(root);
		}
	}

	private User getUser(
			PublicIdentity pubIdentity,
			PGPPublicKeyRing keyRing,
			ClientUpdate cu,
			InetAddress inetAddress,
			int port,
			Date date) throws FriendlyBackupException {
		
		//check if user already exists
		for(Eligibility directory : User.Eligibility.values()) {
			File file =
				User.getUserDirectory(
						getRoot(),
						directory,
						pubIdentity.getHandle());
			
			if( file.exists() ) {
				return new User(file, directory, date);
			}
		}
		
		return new User(cu, pubIdentity, inetAddress, port, date, keyRing);
	}

	private PublicIdentity makePublicIdentity(PGPPublicKeyRing keyRing)
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

	private PGPPublicKeyRing makeKeyRing(byte[] publicKeyRingData)
			throws FriendlyBackupException {
		try {
			return new PGPPublicKeyRing(publicKeyRingData);
		} catch (IOException e) {
			throw new FriendlyBackupException(
					"Could not make public identity",
					e);
		}
	}

	private File getRoot() {
		return root;
	}

}
