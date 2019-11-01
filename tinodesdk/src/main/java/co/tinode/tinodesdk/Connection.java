package co.tinode.tinodesdk;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A thinly wrapped websocket connection.
 */
public class Connection extends WebSocketClient {
    private static final String TAG = "Connection";
    private static int CONNECTION_TIMEOUT = 3000; // in milliseconds

    private WsListener mListener;

    private boolean mReconnecting;
    private boolean mAutoreconnect;

    // Exponential backoff/reconnecting
    final private ExpBackoff backoff = new ExpBackoff();

    @SuppressWarnings("WeakerAccess")
    protected Connection(URI endpoint, String apikey, WsListener listener) {
        super(normalizeEndpoint(endpoint), new Draft_6455(), wrapApiKey(apikey), CONNECTION_TIMEOUT);
        setReuseAddr(true);

        mListener = listener;
        mReconnecting = false;
        mAutoreconnect = false;
    }

    private static Map<String,String> wrapApiKey(String apikey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Tinode-APIKey",apikey);
        return headers;
    }

    private static URI normalizeEndpoint(URI endpoint) {
        String path = endpoint.getPath();
        if (path.equals("")) {
            path = "/";
        } else if (path.lastIndexOf("/") != path.length() - 1) {
            path += "/";
        }
        path += "channels"; // ws://www.example.com:12345/v0/channels

        String scheme = endpoint.getScheme();
        // Normalize scheme to ws or wss.
        scheme = ("wss".equals(scheme) || "https".equals(scheme)) ? "wss" : "ws";

        int port = endpoint.getPort();
        if (port < 0) {
            port = "wss".equals(scheme) ? 443 : 80;
        }
        try {
            endpoint = new URI(scheme,
                    endpoint.getUserInfo(),
                    endpoint.getHost(),
                    port,
                    path,
                    endpoint.getQuery(),
                    endpoint.getFragment());
        } catch (URISyntaxException e) {
            Log.w(TAG, "Invalid endpoint URI", e);
        }

        return endpoint;
    }

    private void connectSocket() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SSLSocket s = null;
                    URI endpoint = getURI();
                    String scheme = endpoint.getScheme();
                    if ("wss".equals(scheme)) {
                        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                        s = (SSLSocket) factory.createSocket(endpoint.getHost(), endpoint.getPort());
                        setSocket(s);
                    }
                    connectBlocking(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (s != null) {
                        // This is a potentially blocking call.
                        String host = s.getSession().getPeerHost();
                        // Verify host name.
                        if (host == null || !host.equals(endpoint.getHost())) {
                            String reason = "Host '" + host + "' does not match '" + endpoint.getHost() + "'";
                            close(-1, "SSL: " + reason);
                            throw new SSLPeerUnverifiedException(reason);
                        }
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "socketConnectionRunnable exception!", ex);
                    if (mListener != null) {
                        mListener.onError(Connection.this, ex);
                    }
                }
            }
        }).start();
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
        Log.i(TAG, "WS.connect: " + hashCode(), new Exception("stacktrace"));

        mAutoreconnect = autoReconnect;

        if (mAutoreconnect && mReconnecting) {
            // If we are waiting to reconnect, do it now.
            backoff.wakeUp();
        } else {
            // Set up new connection.
            connectSocket();
        }
    }

    /**
     * Gracefully close websocket connection. The socket will attempt
     * to send a frame to the server.
     *
     * The call is idempotent: if connection is already closed it does nothing.
     */
    @SuppressWarnings("WeakerAccess")
    synchronized public void disconnect() {
        boolean wakeUp = mAutoreconnect;
        mAutoreconnect = false;

        // Actually close the socket
        close();

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
        return isOpen();
    }

    /**
     * Check if the socket is waiting to reconnect.
     *
     * @return true if the socket is OPEN, false otherwise;
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isWaitingToReconnect() {
        return mReconnecting;
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

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        boolean r = mReconnecting;
        mReconnecting = false;

        if (mListener != null) {
            mListener.onConnect(this, r);
        } else {
            backoff.reset();
        }
    }

    @Override
    public void onMessage(String message) {
        if (mListener != null) {
            mListener.onMessage(this, message);
        }
    }

    @Override
    public void onMessage(ByteBuffer blob) {
        // do nothing, server does not send binary frames
        Log.w(TAG, "binary message received (should not happen)");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // Avoid infinite recursion
        if (mReconnecting) {
            return;
        } else {
            mReconnecting = mAutoreconnect;
        }

        if (mListener != null) {
            mListener.onDisconnect(this, remote, code, reason);
        }

        if (mAutoreconnect) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mAutoreconnect && !isConnected()) {
                        backoff.doSleep();
                        // Check if an explicit disconnect has been requested.
                        if (!mAutoreconnect || isConnected()) {
                            mReconnecting = false;
                            return;
                        }
                        reconnect();
                    }
                }
            }).start();
        }
    }

    @Override
    public void onError(Exception ex) {
        if (mListener != null) {
            mListener.onError(this, ex);
        }
    }

    static class WsListener {
        WsListener() {}

        protected void onConnect(Connection conn, boolean reconnected) {
        }

        protected void onMessage(Connection conn, String message) {
        }

        protected void onDisconnect(Connection conn, boolean byServer, int code, String reason) {
        }

        protected void onError(Connection conn, Exception err) {
        }
    }
}
