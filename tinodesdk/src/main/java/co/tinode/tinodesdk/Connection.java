package co.tinode.tinodesdk;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

/**
 * A thinly wrapped websocket connection.
 */
public class Connection {
    private static final String TAG = "tinodesdk.Connection";
    private static int CONNECTION_TIMEOUT = 3000; // in milliseconds

    private WebSocketClient mWsClient;
    private WsListener mListener;

    private URI mEndpoint;
    private String mApiKey;

    private boolean useTls;

    private Boolean reconnecting;
    private boolean autoreconnect;

    // Exponential backoff/reconnecting
    final private ExpBackoff backoff = new ExpBackoff();

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

    private void connectSocket() {
        Map<String,String> headers = new HashMap<>();
        headers.put("X-Tinode-APIKey", mApiKey);
        final WebSocketClient ws = new TinodeWSClient(mEndpoint, headers, CONNECTION_TIMEOUT);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (useTls) {
                        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                        Socket s = factory.createSocket(mEndpoint.getHost(), mEndpoint.getPort());
                        s.setSoTimeout(CONNECTION_TIMEOUT);
                        ws.setSocket(s);
                    }
                    ws.connect();
                    mWsClient = ws;

                } catch (IOException e) {
                    mListener.onError(e);
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
     * @param autoreconnect if connection is dropped, reconnect automatically
     * @return true if a new attempt to open a connection was performed, false if connection already exists
     */
    public boolean connect(boolean autoreconnect) throws IOException {
        this.autoreconnect = autoreconnect;

        if (autoreconnect && reconnecting) {
            // If we are waiting to reconnect, do it now.
            backoff.wakeUp();
        } else {
            // Create new socket and try to connect it.
            connectSocket();
        }

        return true;
    }

    /**
     * Gracefully close websocket connection
     *
     */
    public void disconnect() {
        if (autoreconnect) {
            autoreconnect = false;
            // Make sure we are not waiting to reconnect
            backoff.wakeUp();
        }

        // Actually close the socket
        mWsClient.close();
    }

    /**
     * Check if the socket is OPEN.
     *
     * @return true if the socket is OPEN, false otherwise;
     */
    public boolean isConnected() {
        return mWsClient != null && mWsClient.isOpen();
    }

    public void send(String message) {
        mWsClient.send(message);
    }

    private class TinodeWSClient extends WebSocketClient {

        TinodeWSClient(URI endpoint, Map<String,String> headers, int timeout) {
            super(endpoint, new Draft_6455(), headers, timeout);
            Log.d(TAG, "constructor " + endpoint);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            Log.d(TAG, "onOpen");
            backoff.reset();

            if (mListener != null) {
                mListener.onConnect(reconnecting);
            }

            reconnecting = false;
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
            Log.d(TAG, "onDisconnected for '" + reason + "' (" + code + "); reconnecting=" + reconnecting);

            // Avoid infinite recursion
            if (reconnecting) {
                return;
            } else {
                reconnecting = autoreconnect;
            }

            mListener.onDisconnect(remote, code, reason);

            // Don't try to reconnect of code is -1.
            if (autoreconnect && code != -1) {
                while (!isConnected()) {
                    backoff.doSleep();

                    // Check if an explicit disconnect has been requested.
                    if (!autoreconnect) {
                        reconnecting = false;
                        break;
                    }

                    Log.d(TAG, "Connection: autoreconnecting " + backoff.getAttemptCount());
                    connectSocket();
                }
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
