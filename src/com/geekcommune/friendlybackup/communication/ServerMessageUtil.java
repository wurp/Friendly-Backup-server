package com.geekcommune.friendlybackup.communication;


import java.net.InetAddress;
import java.util.Date;

import com.geekcommune.communication.MessageHandler;
import com.geekcommune.communication.MessageUtil;
import com.geekcommune.communication.RemoteNodeHandle;
import com.geekcommune.communication.message.ClientStartupMessage;
import com.geekcommune.communication.message.ConfirmationMessage;
import com.geekcommune.communication.message.Message;
import com.geekcommune.friendlybackup.FriendlyBackupException;
import com.geekcommune.friendlybackup.server.UserManager;
import com.geekcommune.friendlybackup.server.config.BackupServerConfig;
import com.geekcommune.friendlybackup.server.format.high.ClientUpdate;

public class ServerMessageUtil extends MessageUtil {
    //private static final Logger log = Logger.getLogger(ServerMessageUtil.class);

    private static ServerMessageUtil instance;

    public static ServerMessageUtil instance() {
        return instance;
    }

    public static void setInstance(ServerMessageUtil instance) {
        ServerMessageUtil.instance = instance;
    }

    protected BackupServerConfig bakcfg;

    public ServerMessageUtil() {
    	initMessageHandlers();
    }
    
    private void initMessageHandlers() {
		addMessageHandler(new MessageHandler() {
			
			@Override
			public boolean handleMessage(Message msg, InetAddress address,
					boolean responseHandled) throws FriendlyBackupException {
				boolean handled = false;
				
		        if( msg instanceof ClientStartupMessage ) {
		        	handled = true;
		        	ClientStartupMessage csm = (ClientStartupMessage) msg;

		        	ClientUpdate cu = csm.getClientUpdate();
		            UserManager.instance().handleUpdate(cu, new Date(), address, csm.getOriginNodePort());
		            
					RemoteNodeHandle destination =
						new RemoteNodeHandle(
								"none",
								"none",
								address.getHostName() + ":" +
									csm.getOriginNodePort());
					queueMessage(new ConfirmationMessage(
							destination,
							csm.getTransactionID(),
							bakcfg.getLocalPort()));
		            msg.setState(Message.State.Finished);
		        }
		        
				return handled;
			}
		});
	}

	public BackupServerConfig getBackupConfig() {
        return bakcfg;
    }

    public void setBackupConfig(BackupServerConfig bakcfg) {
        this.bakcfg = bakcfg;
    }

	@Override
	protected int getLocalPort() {
		return bakcfg.getLocalPort();
	}
}
