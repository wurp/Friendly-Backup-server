package com.geekcommune.friendlybackup.communication;


import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.geekcommune.communication.message.AbstractMessage;
import com.geekcommune.communication.message.Message;
import com.geekcommune.friendlybackup.FriendlyBackupException;
import com.geekcommune.friendlybackup.communication.message.ClientStartupMessage;
import com.geekcommune.friendlybackup.server.UserManager;
import com.geekcommune.friendlybackup.server.config.BackupServerConfig;
import com.geekcommune.friendlybackup.server.format.high.ClientUpdate;

public class MessageListener {

    private static final Logger log = Logger.getLogger(MessageListener.class);

    private static final int NUM_THREADS = 10;

    private static MessageListener instance;

    public static MessageListener instance() {
        return instance;
    }

    public static void setInstance(MessageListener instance) {
        MessageListener.instance = instance;
    }

    protected BackupServerConfig bakcfg;

    private Thread listenThread;

    private ThreadPoolExecutor listenExecutor;


    public MessageListener() {
    }
    
    public BackupServerConfig getBackupConfig() {
        return bakcfg;
    }

    public void setBackupConfig(BackupServerConfig bakcfg) {
        this.bakcfg = bakcfg;
    }

    public void startListenThread() {
        if( listenThread != null ) {
            throw new RuntimeException("Listen thread already started");
        }

        LinkedBlockingQueue<Runnable> listenWorkQueue = new LinkedBlockingQueue<Runnable>();
        listenExecutor = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 1000, TimeUnit.MILLISECONDS, listenWorkQueue);

        listenThread = new Thread(new Runnable() {
            public void run() {
                try {
                    ServerSocket serversocket = null;
                    serversocket = new ServerSocket(bakcfg.getLocalPort());
                    log.debug("server socket listening on " + bakcfg.getLocalPort());
                    do {
                        try {
                            Socket socket = serversocket.accept();
                            log.debug("Server socket open");
                            listenExecutor.execute(makeHandleAllMessagesOnSocketRunnable(socket));
                        } catch(Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    } while (true);
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error("Couldn't start listening for unsolicited messages: " + e.getMessage(), e);
                }
            }
        });

        listenThread.start();
    }

    private Runnable makeHandleAllMessagesOnSocketRunnable(final Socket socket) {
        return new Runnable() {
            public void run() {
                try {
                    while(socket.isConnected() && !socket.isInputShutdown()) {
                        DataInputStream dis = new DataInputStream(socket.getInputStream());
                        final Message msg = AbstractMessage.parseMessage(dis);
                        final InetAddress address = socket.getInetAddress();
                        msg.setState(Message.State.NeedsProcessing);
                        makeProcessMessageRunnable(msg, address).run();
                        socket.getOutputStream().write(1);
                        socket.getOutputStream().flush();
                    }
                } catch (IOException e) {
                    log.error("Error talking to " + socket + ", " + e.getMessage(), e);
                } catch (FriendlyBackupException e) {
                    log.error("Error talking to " + socket + ", " + e.getMessage(), e);
                } finally {
                    try {
                        log.debug("Server socket finished");
                        socket.close();
                    } catch( Exception e ) {
                        log.error("Error closing socket to " + socket + ", " + e.getMessage(), e);
                    }
                }
            }
        };
    }

    private Runnable makeProcessMessageRunnable(final Message msg,
            final InetAddress address) {
        return new Runnable() {
            public void run() {
                try {
                    processMessage(msg, address);
                } catch (SQLException e) {
                    log.error("Error talking processing " + msg + ": " + e.getMessage(), e);
                } catch (FriendlyBackupException e) {
                    log.error("Error talking processing " + msg + ": " + e.getMessage(), e);
                }
            }
        };
    }

    public void processMessage(Message msg, InetAddress inetAddress) throws SQLException, FriendlyBackupException {
        log.debug("processing " + msg.getTransactionID());
        //TODO reject message if we've already processed its transaction id
        msg.setState(Message.State.Processing);

        if( msg instanceof ClientStartupMessage ) {
        	ClientStartupMessage csm = (ClientStartupMessage) msg;

        	ClientUpdate cu = csm.getClientUpdate();
            UserManager.instance().handleUpdate(cu, new Date(), inetAddress, csm.getOriginNodePort());
            
            msg.setState(Message.State.Finished);
        } else {
            msg.setState(Message.State.Error);
            log.error("Unexpected message type; message: " + msg + " from inetAddress " + inetAddress);
        }

        log.debug("processed " + msg.getTransactionID());
    }
}
