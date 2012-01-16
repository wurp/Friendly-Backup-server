package com.geekcommune.friendlybackup.communication.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.geekcommune.communication.message.AbstractMessage;
import com.geekcommune.friendlybackup.FriendlyBackupException;
import com.geekcommune.friendlybackup.proto.Basic;
import com.geekcommune.friendlybackup.server.format.high.ClientUpdate;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class ClientStartupMessage extends AbstractMessage {
	private static final int INT_TYPE = 6;
	
	private byte[] data;

	protected ClientStartupMessage(int transactionId, int originNodePort) {
		super(transactionId, originNodePort);
	}
	
	public ClientUpdate getClientUpdate() throws FriendlyBackupException {
		try {
			return ClientUpdate.fromProto(Basic.ClientUpdate.parseFrom(ByteString.copyFrom(getData())));
		} catch (InvalidProtocolBufferException e) {
			throw new FriendlyBackupException("Could not retrieve client update data", e);
		}
	}

	private byte[] getData() {
		return data;
	}

	@Override
	public int getType() {
		return INT_TYPE;
	}

	@Override
	protected void internalRead(DataInputStream is) throws IOException,
			FriendlyBackupException {
        int len = is.readInt();
        
        if( len != 0 ) {
            data = new byte[len];
            readBytes(is, data, 0, data.length);
//            if( data.length != bytesRead ) {
//                int remaining = is.read();
//                throw new RuntimeException("Could not read all " + data.length + " bytes, found " + bytesRead + ", found " + remaining + " when looking for more data");
//            }
        }
	}

    private void readBytes(InputStream is, byte[] data, int startIdx, int length) throws IOException {
        int total = 0;
        while( total < length ) {
            int lastRead = is.read(data, startIdx + total, length - total);
            if( lastRead == -1 ) {
                throw new RuntimeException("Could not read all " + length + " bytes, found " + total + ", before hitting end of stream");
            }
            total += lastRead;
        }
    }

	@Override
	protected void internalWrite(DataOutputStream os) throws IOException,
			FriendlyBackupException {
        byte[] data = getData();
        os.writeInt(data.length);
        os.write(data);
	}

}
