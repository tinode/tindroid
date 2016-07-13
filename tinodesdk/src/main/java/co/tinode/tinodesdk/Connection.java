/**
 * Created by gene on 01/02/16.
 */
package co.tinode.tinodesdk;

import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A thinly wrapped websocket connection.
 *
 * Created by gene on 2/12/14.
 */
public class Connection {
    private static final String TAG = "tinodesdk.Connection";
    private static WebSocketFactory sWSFactory = new WebSocketFactory();

    private WebSocket mWsClient;
    private WsListener mListener;

    // Exponential backoff/reconnecting
    // TODO(gene): implement autoreconnect
    private boolean autoreconnect;
    private ExpBackoff backoff;

    protected Connection(URI endpoint, String apikey, WsListener listener) throws IOException {

        String path = endpoint.getPath();
        if (path.equals("")) {
            path = "/";
        } else if (path.lastIndexOf("/") != path.length() - 1) {
            path += "/";
        }
        path += "channels"; // ws://www.example.com:12345/v0/channels

        URI uri;
        try {
            uri = new URI(endpoint.getScheme(),
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

        mWsClient = sWSFactory.createSocket(uri, 3000);
        mWsClient.addHeader("X-Tinode-APIKey", apikey);
        mWsClient.addListener(new WebSocketAdapter() {
            @Override
            public void onConnected(WebSocket ws, Map<String, List<String>> headers) {
                Log.d(TAG, "Websocket connected!");
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
                Log.i(TAG, "binary message received (should not happen)");
            }

            @Override
            public void onDisconnected(WebSocket ws,
                                       WebSocketFrame serverCloseFrame,
                                       WebSocketFrame clientCloseFrame,
                                       final boolean closedByServer) {
                Log.d(TAG, "Disconnected :(");
                // Reset packet counter

                WebSocketFrame frame = closedByServer ? serverCloseFrame : clientCloseFrame;
                mListener.onDisconnect(closedByServer, frame.getCloseCode(), frame.getCloseReason());

                if (autoreconnect) {
                    // TODO(gene): add autoreconnect
                }
            }
            @Override
            public void onConnectError(WebSocket ws, final WebSocketException error) {
                Log.i(TAG, "Connection error", error);
                mListener.onError(error);
            }

            @Override
            public void onError(WebSocket ws, final WebSocketException error) {
                Log.i(TAG, "Generic error", error);
                mListener.onError(error);
            }
        });
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
    public boolean connect(boolean autoreconnect) {
        // TODO(gene): implement autoreconnect
        this.autoreconnect = autoreconnect;

        mWsClient.connectAsynchronously();
        return true;
    }

    /**
     * Gracefully close websocket connection
     *
     */
    public void disconnect() {
        mWsClient.disconnect();
    }

    /**
     * Check if the socket is OPEN.
     *
     * @return true if the socket is OPEN, false otherwise;
     */
    public boolean isConnected() {
        return mWsClient.isOpen();
    }

    public void send(String message) {
        mWsClient.sendText(message);
    }

    public static class WsListener {
        protected void onConnect() {
        }

        protected void onMessage(String message) {
        }

        protected void onDisconnect(boolean byServer, int code, String reason) {
        }

        protected void onError(Exception err) {
        }
    }

    /**
     * TODO(gene): implement autoreconnect with exponential backoff
     */
    private class ExpBackoff {
        private int mRetryCount = 0;
        final private long SLEEP_TIME_MILLIS = 500; // 500 ms
        final private long MAX_DELAY = 1800000; // 30 min
        private Random random = new Random();

        void reset() {
            mRetryCount = 0;
        }

        /**
         *
         * @return reconnect timeout in milliseconds
         */
        long getSleepTimeMillis() {
            int attempt = mRetryCount;
            return Math.min(SLEEP_TIME_MILLIS * (random.nextInt(1 << attempt) + (1 << (attempt+1))),
                    MAX_DELAY);
        }
    }
}
