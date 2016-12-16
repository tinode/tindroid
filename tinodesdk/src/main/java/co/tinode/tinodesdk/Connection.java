package co.tinode.tinodesdk;

import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketState;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A thinly wrapped websocket connection.
 */
public class Connection {
    private static final String TAG = "tinodesdk.Connection";
    private static WebSocketFactory sWSFactory = new WebSocketFactory();
    private static int CONNECTION_TIMEOUT = 3000; // in milliseconds

    private WebSocket mWsClient;
    private WsListener mListener;

    private URI mEndpoint;
    private String mApiKey;

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

        try {
            mEndpoint = new URI(endpoint.getScheme(),
                    endpoint.getUserInfo(),
                    endpoint.getHost(),
                    endpoint.getPort(),
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

    protected WebSocket createSocket() throws IOException {

        WebSocket ws = sWSFactory.createSocket(mEndpoint, CONNECTION_TIMEOUT);
        ws.addHeader("X-Tinode-APIKey", mApiKey);
        ws.addListener(new WebSocketAdapter() {
            @Override
            public void onConnected(WebSocket ws, Map<String, List<String>> headers) {
                if (backoff != null) {
                    backoff.reset();
                }

                if (mListener != null) {
                    mListener.onConnect();
                }
            }

            @Override
            public void onTextMessage(WebSocket ws, final String message) {
                mListener.onMessage(message);
            }

            @Override
            public void onBinaryMessage(WebSocket ws, byte[] data) {
                // do nothing, server does not send binary frames
                Log.e(TAG, "binary message received (should not happen)");
            }

            @Override
            public void onDisconnected(WebSocket ws,
                                       WebSocketFrame serverCloseFrame,
                                       WebSocketFrame clientCloseFrame,
                                       final boolean closedByServer) {
                Log.d(TAG, "Connection failed :(");

                // Avoid infinite recursion
                if (reconnecting) {
                    return;
                }

                WebSocketFrame frame = closedByServer ? serverCloseFrame : clientCloseFrame;
                mListener.onDisconnect(closedByServer, frame.getCloseCode(), frame.getCloseReason());

                while (autoreconnect) {
                    reconnecting = true;

                    backoff.doSleep();
                    Log.d(TAG, "Connection: autoreconnecting " + backoff.getAttemptCount());
                    try {
                        mWsClient = createSocket();
                        mWsClient.connect();
                    } catch (WebSocketException | IOException e) {
                        Log.d(TAG, "Autoreconnect failed " + e.getMessage());
                    }

                    reconnecting = false;
                }
            }

            /*
            // No need to override it. generic onError will be called anyway
            @Override
            public void onConnectError(WebSocket ws, final WebSocketException error) {
                Log.i(TAG, "Connection error", error);
                mListener.onError(error);
            }
            */

            @Override
            public void onError(WebSocket ws, final WebSocketException error) {
                mListener.onError(error);
            }
        });

        return ws;
    }

    /**
     * Establish a connection with the server. It opens a websocket in a separate
     * thread.
     *
     * This is a non-blocking call.
     *
     * @param autoreconnect not implemented yet
     * @return true if a new attempt to open a connection was performed, false if connection already exists
     */
    public boolean connect(boolean autoreconnect) throws IOException {
        this.autoreconnect = autoreconnect;

        if (mWsClient == null || mWsClient.getState() != WebSocketState.CREATED) {
            mWsClient = createSocket();
        }
        mWsClient.connectAsynchronously();
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
        mWsClient.disconnect();
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
        mWsClient.sendText(message);
    }

    static class WsListener {
        WsListener() {}

        protected void onConnect() {
        }

        protected void onMessage(String message) {
        }

        protected void onDisconnect(boolean byServer, int code, String reason) {
        }

        protected void onError(Exception err) {
        }
    }
}
