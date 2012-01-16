package com.geekcommune.friendlybackup.server.format.high;

import com.geekcommune.friendlybackup.FriendlyBackupException;
import com.geekcommune.friendlybackup.format.BaseData;
import com.geekcommune.friendlybackup.proto.Basic;
import com.geekcommune.identity.PublicIdentity;
import com.geekcommune.identity.Signature;
import com.google.protobuf.ByteString;

/**
 * Represents an update to the information about some FB client (storage node).
 * (Or the initial upload of the information.)
 * 
 * @author wurp
 */
public class ClientUpdate extends BaseData<Basic.ClientUpdate>{
	//private static final Logger log = Logger.getLogger(ClientUpdate.class);
	
	private String email;
	private long storageAvailable;
	private Signature signature;
	private String name;
	private byte[] pubKeyRingBytes;

	public ClientUpdate(
			String name,
			String email,
			long storageAvailable,
			byte[] pubKeyRingBytes,
			Signature signature) {
		this.name = name;
		this.email = email;
		this.storageAvailable = storageAvailable;
		this.pubKeyRingBytes = pubKeyRingBytes;
		this.signature = signature;
	}

	@Override
	public Basic.ClientUpdate toProto() {
        Basic.ClientUpdate.Builder bldrId = Basic.ClientUpdate.newBuilder();
        
        bldrId.setVersion(1);
        bldrId.setEmail(email);
        bldrId.setName(name);
        bldrId.setStorageAvailable(storageAvailable);
        bldrId.setPublicKeyRing(ByteString.copyFrom(pubKeyRingBytes));
        bldrId.setSignature(this.signature.toProto());
        
        return bldrId.build();
	}

	public String getEmail() {
		return email;
	}

	public long getStorageAvailable() {
		return storageAvailable;
	}

	public Signature getSignature() {
		return signature;
	}

	public static ClientUpdate fromProto(
			Basic.ClientUpdate proto) throws FriendlyBackupException {
        versionCheck(1, proto.getVersion(), proto);
        
        String name = proto.getName();
        String email = proto.getEmail();
        long storageAvailable = proto.getStorageAvailable();
        byte[] pubKeyRingBytes = proto.getPublicKeyRing().toByteArray();
        Signature signature = Signature.fromProto(proto.getSignature());
        
        return new ClientUpdate(name, email, storageAvailable, pubKeyRingBytes, signature);
	}

    public boolean verifySignature(PublicIdentity pubIdent)
            throws FriendlyBackupException {
        boolean retval = false;
        
		Signature origSig = this.signature;
		byte[] keyData = pubKeyRingBytes;

        try {
            this.signature = Signature.INTERNAL_SELF_SIGNED;
            keyData = new byte[0];
            retval = origSig.verify(pubIdent, toProto().toByteArray());
        } finally {
            this.signature = origSig;
            this.pubKeyRingBytes = keyData;
        }
        
        return retval;
    }

	public String getName() {
		return name;
	}

	public byte[] getPublicKeyRingData() {
		return pubKeyRingBytes;
	}
}
