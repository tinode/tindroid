package co.tinode.tinodesdk;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * A thinly wrapped websocket connection.
 */
public class Connection extends WebSocketClient {
    private static final String TAG = "Connection";

    private static int CONNECTION_TIMEOUT = 3000; // in milliseconds

    // Connection states
    // TODO: consider extending ReadyState
    private enum State {
        // Created. No attempts were made to reconnect.
        NEW,
        // Created, in process of creating or restoring connection.
        CONNECTING,
        // Connected.
        CONNECTED,
        // Disconnected. A thread is waiting to reconnect again.
        WAITING_TO_RECONNECT,
        // Disconnected. Not waiting to reconnect.
        CLOSED
    }

    private WsListener mListener;

    // Connection status
    private State mStatus;

    // If connection should try to reconnect automatically.
    private boolean mAutoreconnect;

    // Exponential backoff/reconnecting
    final private ExpBackoff backoff = new ExpBackoff();

    @SuppressWarnings("WeakerAccess")
    protected Connection(URI endpoint, String apikey, WsListener listener) {
        super(normalizeEndpoint(endpoint), new Draft_6455(), wrapApiKey(apikey), CONNECTION_TIMEOUT);
        setReuseAddr(true);

        mListener = listener;
        mStatus = State.NEW;
        mAutoreconnect = false;

        // Horrible hack to support SNI on API<21
        if ("wss".equals(getURI().getScheme())) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, null, null);
                SSLSocketFactory factory = sslContext.getSocketFactory();
                setSocketFactory(new SNISocketFactory(factory));
            } catch (NoSuchAlgorithmException | KeyManagementException ex) {
                Log.w(TAG, "Failed to set up SSL", ex);
            }
        }
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

    private void connectSocket(final boolean reconnect) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (reconnect) {
                        reconnectBlocking();
                    } else {
                        connectBlocking(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
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
     * Establish a connection with the server. It opens or reopens a websocket in a separate
     * thread.
     *
     * This is a non-blocking call.
     *
     * @param autoReconnect if connection is dropped, reconnect automatically
     */
    @SuppressWarnings("WeakerAccess")
    synchronized public void connect(boolean autoReconnect) {
        mAutoreconnect = autoReconnect;

        switch (mStatus) {
            case CONNECTED:
            case CONNECTING:
                // Already connected or in process of connecting: do nothing.
                break;
            case WAITING_TO_RECONNECT:
                backoff.wakeUp();
                break;
            case NEW:
                mStatus = State.CONNECTING;
                connectSocket(false);
                break;
            case CLOSED:
                mStatus = State.CONNECTING;
                connectSocket(true);
                break;
            // exhaustive, no default:
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

        // Actually close the socket (non-blocking).
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
        return mStatus == State.WAITING_TO_RECONNECT;
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
        synchronized (this) {
            mStatus = State.CONNECTED;
        }

        if (mListener != null) {
            mListener.onConnect(this);
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
        synchronized (this) {
            if (mStatus == State.WAITING_TO_RECONNECT) {
                return;
            } else if (mAutoreconnect) {
                mStatus = State.WAITING_TO_RECONNECT;
            } else {
                mStatus = State.CLOSED;
            }
        }

        if (mListener != null) {
            mListener.onDisconnect(this, remote, code, reason);
        }

        if (mAutoreconnect) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mStatus == State.WAITING_TO_RECONNECT) {
                        backoff.doSleep();

                        synchronized (Connection.this) {
                            // Check if we no longer need to connect.
                            if (mStatus != State.WAITING_TO_RECONNECT) {
                                break;
                            }
                            mStatus = State.CONNECTING;
                        }
                        connectSocket(true);
                    }
                }
            }).start();
        }
    }

    @Override
    public void onError(Exception ex) {
        Log.w(TAG, "Websocket error", ex);

        if (mListener != null) {
            mListener.onError(this, ex);
        }
    }

    static class WsListener {
        WsListener() {}

        protected void onConnect(Connection conn) {
        }

        protected void onMessage(Connection conn, String message) {
        }

        protected void onDisconnect(Connection conn, boolean byServer, int code, String reason) {
        }

        protected void onError(Connection conn, Exception err) {
        }
    }

    private class SNISocketFactory extends SocketFactory {
        SocketFactory mWrapped;

        SNISocketFactory(SocketFactory parent) {
            mWrapped = parent;
        }

        @Override
        public Socket createSocket() throws IOException {
            URI uri = getURI();
            return createSocket(uri.getHost(), uri.getPort());
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket socket = mWrapped.createSocket(host, port);
            fixHostname(socket);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            Socket socket = mWrapped.createSocket(host, port, localHost, localPort);
            fixHostname(socket);
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port) throws IOException {
            Socket socket = mWrapped.createSocket(address, port);
            fixHostname(socket);
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            Socket socket = mWrapped.createSocket(address, port, localAddress, localPort);
            fixHostname(socket);
            return socket;
        }

        // SNI hack for earlier versions of Android
        private void fixHostname(Socket socket) {
            try {
                // We don't know the actual class of the socket. Using reflection.
                Method method = socket.getClass().getMethod("setHostname", String.class);
                method.invoke(socket, getURI().getHost());
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException ex) {
                Log.w(TAG, "SNI configuration failed", ex);
            }
        }
    }
}
