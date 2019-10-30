package co.tinode.tinodesdk;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A thinly wrapped websocket connection.
 */
public class Connection {
    private static final String TAG = "Connection";
    private static int CONNECTION_TIMEOUT = 3000; // in milliseconds

    private WebSocketClient mWsClient;
    private WsListener mListener;

    private URI mEndpoint;
    private String mApiKey;

    private boolean useTls;

    private boolean reconnecting;
    private boolean autoreconnect;

    // Exponential backoff/reconnecting
    final private ExpBackoff backoff = new ExpBackoff();

    @SuppressWarnings("WeakerAccess")
    protected Connection(URI endpoint, String apikey, WsListener listener) {

        mEndpoint = endpoint;
        mApiKey = apikey;

        String path = endpoint.getPath();
        if (path.equals("")) {
            path = "/";
        } else if (path.lastIndexOf("/") != path.length() - 1) {
            path += "/";
        }
        path += "channels"; // ws://www.example.com:12345/v0/channels

        String scheme = endpoint.getScheme();
        useTls = scheme.equals("wss") || scheme.equals("https");
        int port = endpoint.getPort();
        if (port < 0) {
            port = useTls ? 443 : 80;
        }
        try {
            mEndpoint = new URI(scheme,
                    endpoint.getUserInfo(),
                    endpoint.getHost(),
                    port,
                    path,
                    endpoint.getQuery(),
                    endpoint.getFragment());
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        mListener = listener;
        reconnecting = false;
        autoreconnect = false;
    }

    private void socketConnectionRunnable(final WebSocketClient ws) {
        try {
            SSLSocket s = null;
            if (useTls) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                s = (SSLSocket) factory.createSocket(mEndpoint.getHost(), mEndpoint.getPort());
                s.setSoTimeout(CONNECTION_TIMEOUT);
                ws.setSocket(s);
            }
            ws.connect();
            if (s != null) {
                String host = s.getSession().getPeerHost();
                if (host == null || !host.equals(mEndpoint.getHost())) {
                    String reason = "Host '" + host + "' does not match '" + mEndpoint.getHost() + "'";
                    ws.close(-1, "SSL: " + reason);
                    throw new SSLPeerUnverifiedException(reason);
                }
            }
            mWsClient = ws;

        } catch (IOException e) {
            Log.d(TAG, "socketConnectionRunnable exception!", e);
            if (mListener != null) {
                mListener.onError(e);
            }
        }
    }

    private void connectSocket(boolean noNewThread) {
        Map<String,String> headers = new HashMap<>();
        headers.put("X-Tinode-APIKey", mApiKey);
        final WebSocketClient ws = new TinodeWSClient(mEndpoint, headers, CONNECTION_TIMEOUT);
        if (noNewThread) {
            socketConnectionRunnable(ws);
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    socketConnectionRunnable(ws);
                }
            }).start();
        }
    }

    /**
     * Establish a connection with the server. It opens a websocket in a separate
     * thread.
     *
     * This is a non-blocking call.
     *
     * @param autoReconnect if connection is dropped, reconnect automatically
     */
    @SuppressWarnings("WeakerAccess")
    synchronized public void connect(boolean autoReconnect) {
        this.autoreconnect = autoReconnect;

        if (autoreconnect && reconnecting) {
            // If we are waiting to reconnect, do it now.
            backoff.wakeUp();
        } else {
            // Create new socket and try to connect it.
            connectSocket(false);
        }
    }

    /**
     * Gracefully close websocket connection.
     */
    @SuppressWarnings("WeakerAccess")
    synchronized public void disconnect() {
        boolean wakeUp = autoreconnect;
        autoreconnect = false;

        // Actually close the socket
        if (mWsClient != null) {
            mWsClient.close();
            mWsClient = null;
        }

        if (wakeUp) {
            // Make sure we are not waiting to reconnect
            backoff.wakeUp();
        }
    }

    /**
     * Check if the socket is OPEN.
     *
     * @return true if the socket is OPEN, false otherwise;
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isConnected() {
        return mWsClient != null && mWsClient.isOpen();
    }

    /**
     * Check if the socket is waiting to reconnect.
     *
     * @return true if the socket is OPEN, false otherwise;
     */
    public boolean isWaitingToReconnect() {
        return reconnecting;
    }
    /**
     * Reset exponential backoff counter to zero.
     * If autoreconnect is true and WsListener is provided, then WsListener.onConnect must call
     * this method.
     */
    @SuppressWarnings("WeakerAccess")
    public void backoffReset() {
        backoff.reset();
    }

    public void send(String message) {
        mWsClient.send(message);
    }

    private class TinodeWSClient extends WebSocketClient {

        TinodeWSClient(URI endpoint, Map<String,String> headers, int timeout) {
            super(endpoint, new Draft_6455(), headers, timeout);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            try {
                getSocket().setSoTimeout(0);
            } catch (SocketException ignored) {}

            boolean r = reconnecting;
            reconnecting = false;

            if (mListener != null) {
                mListener.onConnect(r);
            } else {
                backoff.reset();
            }
        }

        @Override
        public void onMessage(String message) {
            mListener.onMessage(message);
        }

        @Override
        public void onMessage(ByteBuffer blob) {
            // do nothing, server does not send binary frames
            Log.e(TAG, "binary message received (should not happen)");
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Log.d(TAG, "onDisconnected for '" + reason + "' (code: " + code + ", remote: " +
                        remote + "); reconnecting=" + reconnecting);

            // Avoid infinite recursion
            if (reconnecting) {
                return;
            } else {
                reconnecting = autoreconnect;
            }

            if (mListener != null) {
                mListener.onDisconnect(remote, code, reason);
            }

            // The onClose is called while ws readystate is still OPEN. Therefore discard the client.
            if (mWsClient != null) {
                mWsClient.close();
                mWsClient = null;
            }

            if (autoreconnect) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (!isConnected()) {
                            backoff.doSleep();

                            // Check if an explicit disconnect has been requested.
                            if (!autoreconnect || isConnected()) {
                                reconnecting = false;
                                break;
                            }

                            // In reconnect loop, make sure the previously opened socket is closed.
                            // Otherwise, when the server becomes available, this sockets will
                            // open an unnecessary (orphaned from the very beginning) session.
                            if (reconnecting && mWsClient != null) {
                                mWsClient.close();
                                mWsClient = null;
                            }
                            connectSocket(true);
                        }
                    }
                }).start();
            }
        }

        @Override
        public void onError(Exception ex) {
            mListener.onError(ex);
        }
    }

    static class WsListener {
        WsListener() {}

        protected void onConnect(boolean reconnected) {
        }

        protected void onMessage(String message) {
        }

        protected void onDisconnect(boolean byServer, int code, String reason) {
        }

        protected void onError(Exception err) {
        }
    }
}
