package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.ClientMessage;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.MsgClientAcc;
import co.tinode.tinodesdk.model.MsgClientDel;
import co.tinode.tinodesdk.model.MsgClientGet;
import co.tinode.tinodesdk.model.MsgClientHi;
import co.tinode.tinodesdk.model.MsgClientLeave;
import co.tinode.tinodesdk.model.MsgClientLogin;
import co.tinode.tinodesdk.model.MsgClientNote;
import co.tinode.tinodesdk.model.MsgClientPub;
import co.tinode.tinodesdk.model.MsgClientSet;
import co.tinode.tinodesdk.model.MsgClientSub;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgRange;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

@SuppressWarnings("unused, WeakerAccess")
public class Tinode {
    public static final String USER_NEW = "new";
    public static final String TOPIC_NEW = "new";
    public static final String TOPIC_ME = "me";
    public static final String TOPIC_FND = "fnd";
    public static final String TOPIC_SYS = "sys";

    public static final String TOPIC_GRP_PREFIX = "grp";
    public static final String TOPIC_USR_PREFIX = "usr";
    public static final String NULL_VALUE = "\u2421";
    protected static final String NOTE_KP = "kp";
    protected static final String NOTE_READ = "read";
    protected static final String NOTE_RECV = "recv";
    private static final String TAG = "Tinode";
    // Delay in milliseconds between sending two key press notifications on the
    // same topic.
    private static final long NOTE_KP_DELAY = 3000L;

    // Delay in milliseconds before recv notification is sent
    private static final long NOTE_RECV_DELAY = 300L;

    private static final String PROTOVERSION = "0";
    private static final String VERSION = "0.16";
    private static final String LIBRARY = "tindroid/" + BuildConfig.VERSION_NAME;

    // Reject unresolved futures after this many milliseconds.
    private static final long EXPIRE_FUTURES_TIMEOUT = 5000L;
    // Periodicity of garbage collection of unresolved futures.
    private static final long EXPIRE_FUTURES_PERIOD = 1000L;

    protected static TypeFactory sTypeFactory;
    protected static SimpleDateFormat sDateFormat;
    private static ObjectMapper sJsonMapper;

    static {
        sJsonMapper = new ObjectMapper();
        // Silently ignore unknown properties
        sJsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // Skip null fields from serialization
        sJsonMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        // (De)Serialize dates as RFC3339. The default does not cut it because
        // it represents the time zone as '+0000' instead of the expected 'Z' and
        // SimpleDateFormat cannot handle *optional* milliseconds.
        // Java 7 date parsing is retarded. Format: 2016-09-07T17:29:49.100Z
        sJsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        sDateFormat = new RFC3339Format();
        sJsonMapper.setDateFormat(sDateFormat);

        sTypeFactory = sJsonMapper.getTypeFactory();
    }

    private JavaType mDefaultTypeOfMetaPacket = null;
    private HashMap<Topic.TopicType, JavaType> mTypeOfMetaPacket;
    private MimeTypeResolver mMimeResolver = null;
    private Storage mStore;

    private String mApiKey;
    private String mServerHost = null;
    private boolean mUseTLS;
    private String mServerVersion = null;
    private String mServerBuild = null;

    private String mDeviceToken = null;
    private String mLanguage = null;
    private String mAppName;
    private String mOsVersion;

    private Connection mConnection = null;
    private ConnectedWsListener mConnectionListener = null;

    // True is connection is authenticated
    private boolean mConnAuth = false;
    // True if Tinode should use mLoginCredentials to automatically log in after connecting.
    private boolean mAutologin = false;
    private LoginCredentials mLoginCredentials = null;
    // Server provided a list of credential methods to validate e.g. ["email", "tel", ...].
    private List<String> mCredToValidate = null;

    private String mMyUid = null;
    private String mAuthToken = null;
    private Date mAuthTokenExpires = null;

    private int mMsgId = 0;
    private int mPacketCount;
    private ListenerNotifier mNotifier;
    private ConcurrentMap<String, FutureHolder> mFutures;
    private ConcurrentHashMap<String, Topic> mTopics;
    private ConcurrentHashMap<String, User> mUsers;
    private transient int mNameCounter = 0;
    private boolean mTopicsLoaded = false;
    // Timestamp of the latest topic desc update.
    private Date mTopicsUpdated = null;
    // The difference between server time and local time.
    private long mTimeAdjustment = 0;
    // Indicator that login is in progress
    private Boolean mLoginInProgress = false;

    /**
     * Initialize Tinode package
     *
     * @param appname  name of the calling application to be included in User Agent on handshake.
     * @param apikey   API key generated by key-gen utility
     * @param store    persistence
     * @param listener EventListener which will receive notifications
     */
    public Tinode(String appname, String apikey, Storage store, EventListener listener) {
        mAppName = appname;
        mOsVersion = System.getProperty("os.version");

        mApiKey = apikey;
        mNotifier = new ListenerNotifier();
        if (listener != null) {
            mNotifier.addListener(listener);
        }

        mTypeOfMetaPacket = new HashMap<>();

        mFutures = new ConcurrentHashMap<>(16, 0.75f, 4);
        Timer futuresExpirer = new Timer("futures_expirer");
        futuresExpirer.schedule(new TimerTask() {
            @Override
            public void run() {
                Date expiration = new Date(new Date().getTime() - EXPIRE_FUTURES_TIMEOUT);
                ServerResponseException ex = new ServerResponseException(504, "timeout");
                for (Map.Entry<String,FutureHolder> entry : mFutures.entrySet()) {
                    FutureHolder fh = entry.getValue();
                    if (fh.timestamp.before(expiration)) {
                        mFutures.remove(entry.getKey());
                        try {
                            fh.future.reject(ex);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }, EXPIRE_FUTURES_TIMEOUT, EXPIRE_FUTURES_PERIOD);
        mTopics = new ConcurrentHashMap<>();
        mUsers = new ConcurrentHashMap<>();

        mStore = store;
        if (mStore != null) {
            mMyUid = mStore.getMyUid();
            mDeviceToken = mStore.getDeviceToken();
        }
        // If mStore is fully initialized, this will load topics, otherwise noop
        loadTopics();
    }

    /**
     * Initialize Tinode package
     *
     * @param appname  name of the calling application to be included in User Agent on handshake.
     * @param apikey   API key generated by key-gen utility
     * @param listener EventListener which will receive notifications
     */
    public Tinode(String appname, String apikey, EventListener listener) {
        this(appname, apikey, null, listener);
    }

    /**
     * Initialize Tinode package
     *
     * @param appname name of the calling application to be included in User Agent on handshake.
     * @param apikey  API key generated by key-gen utility
     */
    public Tinode(String appname, String apikey) {
        this(appname, apikey, null);
    }

    @SuppressWarnings("WeakerAccess")
    public static TypeFactory getTypeFactory() {
        return sTypeFactory;
    }

    @SuppressWarnings("WeakerAccess")
    public static ObjectMapper getJsonMapper() {
        return sJsonMapper;
    }

    // Compares object to a string which signifies "null" to the server.
    public static boolean isNull(Object obj) {
        // Del control character
        return (obj instanceof String) && obj.equals(NULL_VALUE);
    }

    /**
     * Convert object to JSON string. Exported for convenience.
     *
     * @param o object to convert
     * @return JSON as string.
     * @throws JsonProcessingException if object cannot be converted
     */
    public static String jsonSerialize(Object o) throws JsonProcessingException {
        return sJsonMapper.writeValueAsString(o);
    }

    /**
     * Convert JSON to an object. Exported for convenience.
     *
     * @param input         JSON string to parse
     * @param canonicalName name of the class to generate from JSON.
     * @return converted object.
     */
    public static <T> T jsonDeserialize(String input, String canonicalName) {
        try {
            return sJsonMapper.readValue(input, sTypeFactory.constructFromCanonical(canonicalName));
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }

    /**
     * Convert JSON to an array of objects. Exported for convenience.
     *
     * @param input         JSON string to parse
     * @param canonicalName name of the base class to use as elements of array.
     * @return converted array of objects.
     */
    public static <T> T[] jsonDeserializeArray(String input, String canonicalName) {
        try {
            return sJsonMapper.readValue(input, sTypeFactory.constructArrayType(
                            sTypeFactory.constructFromCanonical(canonicalName)));
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }

    /**
     * Get minimum delay between two subsequent key press notifications.
     */
    @SuppressWarnings("WeakerAccess")
    protected static long getKeyPressDelay() {
        return NOTE_KP_DELAY;
    }

    /**
     * Instantiate topic of an appropriate class given the name.
     *
     * @param tinode instance of core Tinode to attach topic to
     * @param name   name of the topic to create
     * @param l      event listener; could be null
     * @return topic of an appropriate class
     */
    @SuppressWarnings("unchecked")
    public static Topic newTopic(final Tinode tinode, final String name, final Topic.Listener l) {
        if (TOPIC_ME.equals(name)) {
            return new MeTopic(tinode, l);
        } else if (TOPIC_FND.equals(name)) {
            return new FndTopic(tinode, l);
        }
        return new ComTopic(tinode, name, l);
    }

    /**
     * Add listener which will receive event notifications.
     *
     * @param listener event listener to be notified. Should not be null.
     */
    public void addListener(EventListener listener) {
        mNotifier.addListener(listener);
    }

    /**
     * Remove listener.
     *
     * @param listener event listener to be removed. Should not be null.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean removeListener(EventListener listener) {
        return mNotifier.delListener(listener);
    }

    /**
     * Set non-default version of OS string for User-Agent
     */
    public void setOsString(String os) {
        mOsVersion = os;
    }

    private void loadTopics() {
        if (mStore != null && mStore.isReady() && !mTopicsLoaded) {
            Topic[] topics = mStore.topicGetAll(this);
            if (topics != null) {
                for (Topic tt : topics) {
                    tt.setStorage(mStore);
                    mTopics.put(tt.getName(), tt);
                    setTopicsUpdated(tt.getUpdated());
                }
            }
            mTopicsLoaded = true;
        }
    }

    private void setTopicsUpdated(Date date) {
        if (date == null) {
            return;
        }

        if (mTopicsUpdated == null || mTopicsUpdated.before(date)) {
            mTopicsUpdated = date;
        }
    }

    /**
     * Get the most recent timestamp of update to any topic.
     *
     * @return timestamp of the last update to any topic.
     */
    public Date getTopicsUpdated() {
        return mTopicsUpdated;
    }

    /**
     * Set server address and TLS status to be used in subsequent connections.
     */
    public void setServer(String host, boolean tls) {
        mServerHost = host != null ? host.toLowerCase() : null;
        mUseTLS = tls;
    }

    /**
     * Open a websocket connection to the server, process handshake exchange then optionally login.
     *
     * @param hostName address of the server to connect to; if hostName is null a saved address will be used.
     * @param tls      use transport layer security (wss); ignored if hostName is null.
     *
     * @return PromisedReply to be resolved or rejected when the connection is completed.
     */
    synchronized public PromisedReply<ServerMessage> connect(String hostName, boolean tls) {
        boolean newHost = false;
        if (hostName != null) {
            // Convert to lowercase to ensure correct comparison.
            hostName = hostName.toLowerCase();
            // Check if host address has changed.
            newHost = !hostName.equals(mServerHost) || tls != mUseTLS;
            // Save updated host name & TLS setting.
            mServerHost = hostName;
            mUseTLS = tls;
        }

        // Connection already exists and connected.
        if (mConnection != null && mConnection.isConnected()) {
            // If the connection is live and the server address has not changed, return a resolved promise.
            if (!newHost) {
                return new PromisedReply<>((ServerMessage) null);
            } else {
                // Clear auto-login because saved credentials won't work with the new server.
                setAutoLogin(null, null);
                // Stop exponential backoff timer if it's running.
                mConnection.disconnect();
                mConnection = null;
            }
        }

        mMsgId = 0xFFFF + (int) (Math.random() * 0xFFFF);

        URI connectTo;
        try {
            connectTo = new URI((mUseTLS ? "wss://" : "ws://") + mServerHost + "/v" + PROTOVERSION + "/");
        } catch (URISyntaxException ex) {
            return new PromisedReply<>(ex);
        }

        PromisedReply<ServerMessage> completion = new PromisedReply<>();

        if (mConnectionListener == null) {
            mConnectionListener = new ConnectedWsListener();
        }
        mConnectionListener.addPromise(completion);

        if (mConnection == null) {
            mConnection = new Connection(connectTo, mApiKey, mConnectionListener);
        }
        mConnection.connect(true);

        return completion;
    }

    // Class which listens for websocket to connect.
    private class ConnectedWsListener extends Connection.WsListener {
        final Vector<PromisedReply<ServerMessage>> mCompletionPromises;

        ConnectedWsListener() {
            mCompletionPromises = new Vector<>();
        }

        void addPromise(PromisedReply<ServerMessage> promise) {
            mCompletionPromises.add(promise);
        }

        @Override
        protected void onConnect(final Connection conn) {
            // Connection established, send handshake, inform listener on success
            hello().thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                            boolean doLogin = mAutologin && mLoginCredentials != null;

                            // Resolve outstanding promises;
                            if (!doLogin) {
                                resolvePromises(pkt);
                            }

                            // Success. Reset backoff counter.
                            conn.backoffReset();

                            mTimeAdjustment = pkt.ctrl.ts.getTime() - new Date().getTime();
                            if (mStore != null) {
                                mStore.setTimeAdjustment(mTimeAdjustment);
                            }

                            mNotifier.onConnect(pkt.ctrl.code, pkt.ctrl.text, pkt.ctrl.params);

                            // Login automatically if it's enabled.
                            if (doLogin) {
                                return login(mLoginCredentials.scheme, mLoginCredentials.secret, null)
                                        .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                                            @Override
                                            public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                                                resolvePromises(pkt);
                                                return null;
                                            }
                                        });
                            } else {
                                return null;
                            }
                        }
                    }
            );
        }

        @Override
        protected void onMessage(Connection conn, String message) {
            try {
                dispatchPacket(message);
            } catch (Exception ex) {
                Log.w(TAG, "Exception in dispatchPacket: ", ex);
            }
        }

        @Override
        protected void onDisconnect(Connection conn, boolean byServer, int code, String reason) {
            handleDisconnect(byServer, -code, reason);
        }

        @Override
        protected void onError(Connection conn, Exception err) {
            // No need to call handleDisconnect here. It will be called from onDisconnect().

            // If the promise is waiting, reject. Otherwise it's not our problem.
            try {
                rejectPromises(err);
            } catch (Exception ignored) {
                // Don't throw an exception as no one can catch it.
            }
        }

        private void completePromises(ServerMessage pkt, Exception ex) throws Exception {
            PromisedReply<ServerMessage>[] promises;
            synchronized (mCompletionPromises) {
                //noinspection unchecked
                promises = mCompletionPromises.toArray(new PromisedReply[]{});
                mCompletionPromises.removeAllElements();
            }

            for (int i = promises.length - 1; i >= 0; i--) {
                if (!promises[i].isDone()) {
                    if (ex != null) {
                        promises[i].reject(ex);
                    } else {
                        promises[i].resolve(pkt);
                    }
                }
            }
        }
        private void resolvePromises(ServerMessage pkt) throws Exception {
            completePromises(pkt, null);
        }

        private void rejectPromises(Exception ex) throws Exception {
            completePromises(null, ex);
        }
    }

    /**
     * Make sure connection is either already established or being established:
     *  - If connection is already established do nothing
     *  - If connection does not exist, create
     *  - If not connected and waiting for backoff timer, wake it up.
     *
     * @param interactive set to true if user directly requested a reconnect.
     * @param reset if true drop connection and reconnect; happens when cluster is reconfigured.
     */
    synchronized public void reconnectNow(boolean interactive, boolean reset) {
        if (mConnection == null) {
            // New connection using saved parameters.
            connect(null, false);
        }

        if (mConnection.isConnected()) {
            if (!reset) {
                // If the connection is live and reset is not requested, all is fine.
                return;
            }
            // Forcing a new connection.
            mConnection.disconnect();
            interactive = true;
        }

        // Connection exists but not connected. Try to connect immediately only if requested or if
        // autoreconnect is not enabled.
        if (interactive || !mConnection.isWaitingToReconnect()) {
            mConnection.connect(true);
        }
    }

    /**
     * Probe connection to the server by sending a test packet.
     * It does not check connection for validity before sending. Use {@link #isConnected} first.
     */
    public void networkProbe() {
        mConnection.send("1");
    }

    /**
     * Get configured server address as an URL.
     *
     * @return Server URL.
     * @throws MalformedURLException thrown if server address is not yet configured.
     */
    public URL getBaseUrl() throws MalformedURLException {
        return new URL((mUseTLS ? "https://" : "http://") + mServerHost + "/v" + PROTOVERSION + "/");
    }

    private void handleDisconnect(boolean byServer, int code, String reason) {
        Log.d(TAG, "Disconnected for '" + reason + "' (code: " + code + ", remote: " +
                byServer + ");");

        mConnAuth = false;

        // TODO(gene): should this be cleared?
        mServerBuild = null;
        mServerVersion = null;

        // Reject all pending promises.
        ServerResponseException ex = new ServerResponseException(503, "disconnected");
        for (FutureHolder fh : mFutures.values()) {
            try {
                fh.future.reject(ex);
            } catch (Exception ignored) {
            }
        }

        mFutures.clear();

        // Mark all topics as un-attached.
        for (Topic topic : mTopics.values()) {
            topic.topicLeft(false, 503, "disconnected");
        }

        mNotifier.onDisconnect(byServer, code, reason);
    }

    /**
     * Finds topic for the packet and calls topic's appropriate routeXXX method.
     * This method can be safely called from the UI thread after overriding
     * {@link Connection.WsListener#onMessage(String)}
     * *
     *
     * @param message message to be parsed dispatched
     */
    @SuppressWarnings("unchecked")
    private void dispatchPacket(String message) throws Exception {
        if (message == null || message.equals(""))
            return;

        Log.d(TAG, "in: " + message);

        mPacketCount++;

        mNotifier.onRawMessage(message);

        if (message.length() == 1 && message.charAt(0) == '0') {
            // This is a network probe. No further processing is necessary.
            return;
        }

        ServerMessage pkt = parseServerMessageFromJson(message);
        if (pkt == null) {
            Log.w(TAG, "Failed to parse packet");
            return;
        }

        mNotifier.onMessage(pkt);

        if (pkt.ctrl != null) {
            mNotifier.onCtrlMessage(pkt.ctrl);

            if (pkt.ctrl.id != null) {
                FutureHolder fh = mFutures.remove(pkt.ctrl.id);
                if (fh != null) {
                    if (pkt.ctrl.code >= ServerMessage.STATUS_OK &&
                            pkt.ctrl.code < ServerMessage.STATUS_BAD_REQUEST) {
                        fh.future.resolve(pkt);
                    } else {
                        fh.future.reject(new ServerResponseException(pkt.ctrl.code, pkt.ctrl.text,
                                pkt.ctrl.getStringParam("what", null)));
                    }
                }
            }
            Topic topic = getTopic(pkt.ctrl.topic);
            if (topic != null) {
                if (pkt.ctrl.code == ServerMessage.STATUS_RESET_CONTENT
                        && "evicted".equals(pkt.ctrl.text)) {
                    boolean unsub = pkt.ctrl.getBoolParam("unsub", false);
                    topic.topicLeft(unsub, pkt.ctrl.code, pkt.ctrl.text);
                } else {
                    String what = pkt.ctrl.getStringParam("what", null);
                    if (what != null) {
                        if ("data".equals(what)) {
                            // All data has been delivered.
                            topic.allMessagesReceived(pkt.ctrl.getIntParam("count", 0));
                        } else if ("sub".equals(what)) {
                            // The topic has no subscriptions. Trigger Listener.onSubsUpdated.
                            topic.allSubsReceived();
                        }
                    }
                }
            }
        } else if (pkt.meta != null) {
            Topic topic = getTopic(pkt.meta.topic);
            if (topic == null) {
                topic = maybeCreateTopic(pkt.meta);
            }

            if (topic != null) {
                topic.routeMeta(pkt.meta);
                if (!topic.isFndType() && !topic.isMeType()) {
                    setTopicsUpdated(topic.getUpdated());
                }
            }

            mNotifier.onMetaMessage(pkt.meta);

            resolveWithPacket(pkt.meta.id, pkt);

        } else if (pkt.data != null) {
            Topic topic = getTopic(pkt.data.topic);
            if (topic != null) {
                topic.routeData(pkt.data);
            }

            mNotifier.onDataMessage(pkt.data);

            resolveWithPacket(pkt.data.id, pkt);

        } else if (pkt.pres != null) {
            Topic topic = getTopic(pkt.pres.topic);
            if (topic != null) {
                topic.routePres(pkt.pres);
                // For P2P topics presence is addressed to 'me' only. Forward it to the actual topic, if it's found.
                if (TOPIC_ME.equals(pkt.pres.topic) && Topic.getTopicTypeByName(pkt.pres.src) == Topic.TopicType.P2P) {
                    Topic forwardTo = getTopic(pkt.pres.src);
                    if (forwardTo != null) {
                        forwardTo.routePres(pkt.pres);
                    }
                }
            }

            mNotifier.onPresMessage(pkt.pres);
        } else if (pkt.info != null) {
            Topic topic = getTopic(pkt.info.topic);
            if (topic != null) {
                topic.routeInfo(pkt.info);
            }

            mNotifier.onInfoMessage(pkt.info);
        }

        // TODO(gene): decide what to do on unknown message type
    }

    private void resolveWithPacket(String id, ServerMessage pkt) throws Exception {
        if (id != null) {
            FutureHolder fh = mFutures.remove(id);
            if (fh != null && !fh.future.isDone()) {
                fh.future.resolve(pkt);
            }
        }
    }

    /**
     * Get API key that was used for configuring this Tinode instance.
     *
     * @return API key
     */
    public String getApiKey() {
        return mApiKey;
    }

    /**
     * Get internet address of the server that this Tinode instance was configured with.
     *
     * @return server internet address
     */
    public String getServerHost() {
        return mServerHost;
    }

    /**
     * Get server address suitable for use as an Origin: header for CORS compliance.
     *
     * @return server internet address
     */
    public String getHttpOrigin() {
        return (mUseTLS ? "https://" : "http://") + mServerHost;
    }

    /**
     * Get ID of the current logged in user.
     *
     * @return user ID of the current user.
     */
    public String getMyId() {
        return mMyUid;
    }

    /**
     * Check if the given user ID belong to the current logged in user.
     *
     * @param uid ID of the user to check.
     * @return true if the ID belong to the current user, false otherwise.
     */
    public boolean isMe(String uid) {
        return mMyUid != null && mMyUid.equals(uid);
    }

    /**
     * Get server-provided authentication token.
     *
     * @return authentication token
     */
    public String getAuthToken() {
        return mAuthToken;
    }

    /**
     * Get expiration time of the authentication token, see {@link #getAuthToken()}
     *
     * @return time when the token expires or null.
     */
    public Date getAuthTokenExpiration() {
        return mAuthTokenExpires;
    }

    /**
     * Check if the current session is authenticated.
     *
     * @return true if the session is authenticated, false otherwise.
     */
    public boolean isAuthenticated() {
        return mConnAuth;
    }

    /**
     * Get the protocol version of the server that was reported at the last connection.
     *
     * @return server protocol version.
     */
    public String getServerVersion() {
        return mServerVersion;
    }

    /**
     * Get server build stamp reported at the last connection
     *
     * @return server build stamp.
     */
    public String getServerBuild() {
        return mServerBuild;
    }

    /**
     * Check if connection is in a connected state.
     * Does not check if the network is actually alive.
     *
     * @return true if connection is initialized and in connected state, false otherwise.
     */
    public boolean isConnected() {
        return mConnection != null && mConnection.isConnected();
    }

    /**
     * Assign default types of generic parameters. Needed for packet deserialization.
     *
     * @param typeOfPublic  - type of public values in Desc and Subscription.
     * @param typeOfPrivate - type of private values in Desc and Subscription.
     */
    public void setDefaultTypeOfMetaPacket(JavaType typeOfPublic, JavaType typeOfPrivate) {
        mDefaultTypeOfMetaPacket = sTypeFactory
                .constructParametricType(MsgServerMeta.class, typeOfPublic, typeOfPrivate, typeOfPublic, typeOfPrivate);
    }

    /**
     * Assign default types of generic parameters. Needed for packet deserialization.
     *
     * @param typeOfPublic  - type of public values
     * @param typeOfPrivate - type of private values
     */
    public void setDefaultTypeOfMetaPacket(Class<?> typeOfPublic,
                                           Class<?> typeOfPrivate) {
        setDefaultTypeOfMetaPacket(sTypeFactory.constructType(typeOfPublic),
                sTypeFactory.constructType(typeOfPrivate));
    }

    private JavaType getDefaultTypeOfMetaPacket() {
        return mDefaultTypeOfMetaPacket;
    }

    /**
     * Assign types of generic parameters to topic type. Needed for packet deserialization.
     *
     * @param topicName         - name of the topic to assign type values for.
     * @param typeOfDescPublic  - type of public values
     * @param typeOfDescPrivate - type of private values
     * @param typeOfSubPublic   - type of public values
     * @param typeOfSubPrivate  - type of private values
     */
    public void setTypeOfMetaPacket(String topicName, JavaType typeOfDescPublic, JavaType typeOfDescPrivate,
                                    JavaType typeOfSubPublic, JavaType typeOfSubPrivate) {
        mTypeOfMetaPacket.put(Topic.getTopicTypeByName(topicName), sTypeFactory
                .constructParametricType(MsgServerMeta.class, typeOfDescPublic,
                        typeOfDescPrivate, typeOfSubPublic, typeOfSubPrivate));
    }

    public void setMeTypeOfMetaPacket(JavaType typeOfDescPublic) {
        JavaType priv = sTypeFactory.constructType(PrivateType.class);
        mTypeOfMetaPacket.put(Topic.TopicType.ME, sTypeFactory
                .constructParametricType(MsgServerMeta.class, typeOfDescPublic, priv, typeOfDescPublic, priv));
    }

    public void setMeTypeOfMetaPacket(Class<?> typeOfDescPublic) {
        setMeTypeOfMetaPacket(sTypeFactory.constructType(typeOfDescPublic));
    }

    public void setFndTypeOfMetaPacket(JavaType typeOfSubPublic) {
        mTypeOfMetaPacket.put(Topic.TopicType.FND, sTypeFactory
                .constructParametricType(MsgServerMeta.class,
                        sTypeFactory.constructType(String.class),
                        sTypeFactory.constructType(String.class), typeOfSubPublic,
                        sTypeFactory.constructType(String[].class)));
    }

    public void setFndTypeOfMetaPacket(Class<?> typeOfSubPublic) {
        setFndTypeOfMetaPacket(sTypeFactory.constructType(typeOfSubPublic));
    }

    @SuppressWarnings("WeakerAccess")
    protected JavaType getTypeOfMetaPacket(String topicName) {
        JavaType result = mTypeOfMetaPacket.get(Topic.getTopicTypeByName(topicName));
        return result != null ? result : getDefaultTypeOfMetaPacket();
    }

    protected JavaType resolveMimeType(String mimeType) {
        JavaType type = null;
        if (mMimeResolver != null) {
            type = mMimeResolver.resolve(mimeType);
        }
        if (type == null) {
            if (mimeType == null) {
                // Default mime type = text/plain -> String
                type = sTypeFactory.constructType(String.class);
            } else {
                // All other mime-types convert to byte array.
                type = sTypeFactory.constructType(Byte[].class);
            }
        }
        return type;
    }

    @SuppressWarnings("WeakerAccess")
    protected String makeUserAgent() {
        return mAppName + " (Android " + mOsVersion + "; "
                + Locale.getDefault().toString() + "); " + LIBRARY;
    }

    /**
     * Get {@link LargeFileHelper} object initialized for use with file uploading.
     *
     * @return LargeFileHelper object.
     */
    public LargeFileHelper getFileUploader() {
        URL url = null;
        try {
            url = new URL(getBaseUrl(), "./file/u/");
        } catch (MalformedURLException ignored) {
        }
        return new LargeFileHelper(url, getApiKey(), getAuthToken(), makeUserAgent());
    }

    /**
     * Set device token for push notifications
     *
     * @param token device token; to delete token pass NULL_VALUE
     */
    public PromisedReply<ServerMessage> setDeviceToken(final String token) {
        if (!isAuthenticated()) {
            // Don't send a message if the client is not logged in.
            return new PromisedReply<>(new AuthenticationRequiredException());
        }
        // If token is not initialized, try to read one from storage.
        if (mDeviceToken == null && mStore != null) {
            mDeviceToken = mStore.getDeviceToken();
        }
        // Check if token has changed
        if (mDeviceToken == null || !mDeviceToken.equals(token)) {
            // Cache token here assuming the call to server does not fail. If it fails clear the cached token.
            // This prevents multiple unnecessary calls to the server with the same token.
            mDeviceToken = NULL_VALUE.equals(token) ? null : token;
            if (mStore != null) {
                mStore.saveDeviceToken(mDeviceToken);
            }

            ClientMessage msg = new ClientMessage(new MsgClientHi(getNextId(), null, null,
                    token, null));
            return sendWithPromise(msg, msg.hi.id).thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        // Clear cached value on failure to allow for retries.
                        mDeviceToken = null;
                        if (mStore != null) {
                            mStore.saveDeviceToken(null);
                        }
                        return null;
                    }
                });

        } else {
            // No change: return resolved promise.
            return new PromisedReply<>((ServerMessage) null);
        }
    }

    /**
     * Set device language
     *
     * @param lang ISO 639-1 code for language
     */
    public void setLanguage(String lang) {
        mLanguage = lang;
    }

    /**
     * Send a handshake packet to the server. A connection must be established prior to calling
     * this method.
     *
     * @return PromisedReply of the reply ctrl message.
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> hello() {
        ClientMessage msg = new ClientMessage(new MsgClientHi(getNextId(), VERSION, makeUserAgent(), mDeviceToken, mLanguage));
        return sendWithPromise(msg, msg.hi.id).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                        if (pkt.ctrl == null) {
                            throw new InvalidObjectException("Unexpected type of reply packet to hello");
                        }
                        if (pkt.ctrl.params != null) {
                            mServerVersion = (String) pkt.ctrl.params.get("ver");
                            mServerBuild = (String) pkt.ctrl.params.get("build");
                        }
                        return null;
                    }
                });
    }

    /**
     * Create new account. Connection must be established prior to calling this method.
     *
     * @param uid      uid of the user to affect
     * @param scheme   authentication scheme to use
     * @param secret   authentication secret for the chosen scheme
     * @param loginNow use the new account to login immediately
     * @param desc     default access parameters for this account
     * @return PromisedReply of the reply ctrl message
     */
    protected <Pu, Pr> PromisedReply<ServerMessage> account(String uid, String scheme, String secret,
                                                            boolean loginNow, String[] tags, MetaSetDesc<Pu, Pr> desc,
                                                            Credential[] cred) {
        ClientMessage msg = new ClientMessage<>(
                new MsgClientAcc<>(getNextId(), uid, scheme, secret, loginNow, desc));
        // Add tags and credentials
        if (tags != null) {
            for (String tag : tags) {
                msg.acc.addTag(tag);
            }
        }
        if (cred != null) {
            for (Credential c : cred) {
                msg.acc.addCred(c);
            }
        }

        PromisedReply<ServerMessage> future = sendWithPromise(msg, msg.acc.id);
        if (loginNow) {
            future = future.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) {
                    try {
                        loginSuccessful(pkt.ctrl);
                    } catch (Exception ex) {
                        Log.w(TAG, "Failed to parse server response", ex);
                    }
                    return null;
                }
            });
        }
        return future;
    }

    /**
     * Create account using a single basic authentication scheme. A connection must be established
     * prior to calling this method.
     *
     * @param uname    user name
     * @param password password
     * @param login    use the new account for authentication
     * @param desc     account parameters, such as full name etc.
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu, Pr> PromisedReply<ServerMessage> createAccountBasic(
            String uname, String password, boolean login, MetaSetDesc<Pu, Pr> desc) {
        return account(USER_NEW, AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password),
                login, null, desc, null);
    }

    /**
     * Create account using a single basic authentication scheme. A connection must be established
     * prior to calling this method.
     *
     * @param uname    user name
     * @param password password
     * @param login    use the new account for authentication
     * @param tags     discovery tags
     * @param desc     account parameters, such as full name etc.
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu, Pr> PromisedReply<ServerMessage> createAccountBasic(
            String uname, String password, boolean login, String[] tags, MetaSetDesc<Pu, Pr> desc) {
        return account(USER_NEW, AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password),
                login, tags, desc, null);
    }

    /**
     * Create account using a single basic authentication scheme. A connection must be established
     * prior to calling this method.
     *
     * @param uname    user name
     * @param password password
     * @param login    use the new account for authentication
     * @param tags     discovery tags
     * @param desc     account parameters, such as full name etc.
     * @param cred     account credential, such as email or phone
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu, Pr> PromisedReply<ServerMessage> createAccountBasic(
            String uname, String password, boolean login, String[] tags, MetaSetDesc<Pu, Pr> desc, Credential[] cred) {
        return account(USER_NEW, AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password),
                login, tags, desc, cred);
    }

    protected PromisedReply<ServerMessage> updateAccountSecret(String uid,
                                                               @SuppressWarnings("SameParameterValue") String scheme,
                                                               String secret) {
        return account(uid, scheme, secret, false, null, null, null);
    }

    public PromisedReply<ServerMessage> updateAccountBasic(String uid, String uname, String password) {
        return updateAccountSecret(uid, AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password));
    }

    /**
     * Send a basic login packet to the server. A connection must be established prior to calling
     * this method. Success or failure will be reported through {@link EventListener#onLogin(int, String)}
     *
     * @param uname    user name
     * @param password password
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> loginBasic(String uname, String password) {
        return login(AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password), null);
    }

    /**
     * Send a basic login packet to the server. A connection must be established prior to calling
     * this method. Success or failure will be reported through {@link EventListener#onLogin(int, String)}
     *
     * @param token server-provided security token
     * @param creds validation credentials.
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> loginToken(String token, Credential[] creds) {
        return login(AuthScheme.LOGIN_TOKEN, token, creds);
    }

    /**
     * Send a basic login packet to the server. A connection must be established prior to calling
     * this method. Success or failure will be reported through {@link EventListener#onLogin(int, String)}
     *
     * @param token server-provided security token
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> loginToken(String token) {
        return loginToken(token, null);
    }

    public PromisedReply<ServerMessage> requestResetSecret(String scheme, String method, String value) {
        return login(AuthScheme.LOGIN_RESET, AuthScheme.encodeResetSecret(scheme, method, value), null);
    }

    protected PromisedReply<ServerMessage> login(String combined) {
        AuthScheme auth = AuthScheme.parse(combined);
        if (auth != null) {
            return login(auth.scheme, auth.secret, null);
        }

        return new PromisedReply<>(new IllegalArgumentException());
    }

    // This may be called when login is indeed successful or when password reset is successful.
    private void loginSuccessful(final MsgServerCtrl ctrl) throws IllegalStateException,
            InvalidObjectException, ParseException {
        if (ctrl == null) {
            throw new InvalidObjectException("Unexpected type of reply packet");
        }

        String newUid = ctrl.getStringParam("user", null);
        if (mMyUid != null && !mMyUid.equals(newUid)) {
            // logout() clears mMyUid. Save it for the exception below;
            String oldMyUid = mMyUid;
            logout();
            mNotifier.onLogin(ServerMessage.STATUS_BAD_REQUEST, "UID mismatch");

            throw new IllegalStateException("UID mismatch: received '" + newUid + "', expected '" + oldMyUid + "'");
        }

        mMyUid = newUid;

        if (mStore != null) {
            // FIXME: pass expiration time too.
            mStore.setMyUid(mMyUid);
        }

        // If topics were not loaded earlier, load them now.
        loadTopics();

        mAuthToken = ctrl.getStringParam("token", null);
        if (mAuthToken != null) {
            mAuthTokenExpires = sDateFormat.parse(ctrl.getStringParam("expires", ""));
        } else {
            mAuthTokenExpires = null;
        }

        if (ctrl.code < ServerMessage.STATUS_MULTIPLE_CHOICES) {
            mConnAuth = true;
            setAutoLoginToken(mAuthToken);
            mNotifier.onLogin(ctrl.code, ctrl.text);
        } else {
            // Maybe we got request to enter validation code.
            Iterator<String> it = ctrl.getStringIteratorParam("cred");
            if (it != null) {
                if (mCredToValidate == null) {
                    mCredToValidate = new LinkedList<>();
                }
                while (it.hasNext()) {
                    mCredToValidate.add(it.next());
                }

                if (mStore != null) {
                    // FIXME: pass expiration time too.
                    mStore.setMyUid(mMyUid, mCredToValidate.toArray(new String[]{}));
                }
            }
        }
    }

    /**
     * @param scheme authentication scheme
     * @param secret base64-encoded authentication secret
     * @param creds  credentials for validation
     * @return {@link PromisedReply} resolved or rejected on completion.
     */
    protected synchronized PromisedReply<ServerMessage> login(String scheme, String secret, Credential[] creds) {
        if (mAutologin) {
            // Update credentials.
            mLoginCredentials = new LoginCredentials(scheme, secret);
        }

        if (isAuthenticated()) {
            // Don't try to login again if we are logged in.
            return new PromisedReply<>((ServerMessage) null);
        }

        if (mLoginInProgress) {
            return new PromisedReply<>(new InProgressException());
        }

        mLoginInProgress = true;

        ClientMessage msg = new ClientMessage(new MsgClientLogin(getNextId(), scheme, secret));
        if (creds != null) {
            for (Credential c : creds) {
                msg.login.addCred(c);
            }
        }

        return sendWithPromise(msg, msg.login.id).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                        mLoginInProgress = false;
                        loginSuccessful(pkt.ctrl);
                        return null;
                    }
                },
                new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        mLoginInProgress = false;
                        if (err instanceof ServerResponseException) {
                            ServerResponseException sre = (ServerResponseException) err;
                            final int code = sre.getCode();
                            if (code == ServerMessage.STATUS_UNAUTHORIZED) {
                                mLoginCredentials = null;
                                mAuthToken = null;
                                mAuthTokenExpires = null;
                            }

                            mConnAuth = false;
                            mNotifier.onLogin(sre.getCode(), sre.getMessage());
                        }
                        // The next handler is rejected as well.
                        return new PromisedReply<>(err);
                    }
                });
    }

    /**
     * Tell Tinode to automatically login after connecting.
     *
     * @param scheme authentication scheme to use
     * @param secret authentication secret
     */
    public void setAutoLogin(String scheme, String secret) {
        if (scheme != null) {
            mAutologin = true;
            mLoginCredentials = new LoginCredentials(scheme, secret);
        } else {
            mAutologin = false;
            mLoginCredentials = null;
        }
    }

    /**
     * Tell Tinode to automatically login after connecting using token authentication scheme.
     *
     * @param token auth token to use or null to disable auth-login.
     */
    public void setAutoLoginToken(String token) {
        if (token != null) {
            setAutoLogin(AuthScheme.LOGIN_TOKEN, token);
        } else {
            setAutoLogin(null, null);
        }
    }

    /**
     * Tell Tinode to automatically login after connecting using basic authentication scheme.
     *
     * @param uname    user name
     * @param password password
     */
    public void setAutoLoginBasic(String uname, String password) {
        if (uname != null && password != null) {
            setAutoLogin(AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password));
        } else {
            setAutoLogin(null, null);
        }
    }

    public void disconnect() {
        setAutoLogin(null, null);
        if (mConnection != null) {
            mConnection.disconnect();
        }
    }

    public void logout() {
        // Best effort to clear device token on logout.
        // The app logs out even if the token request has failed.
        setDeviceToken(NULL_VALUE).thenFinally(new PromisedReply.FinalListener() {
            @Override
            public void onFinally() {
                disconnect();
                mMyUid = null;

                if (mStore != null) {
                    mStore.logout();
                }
            }
        });
    }

    /**
     * Low-level subscription request. The subsequent messages on this topic will not
     * be automatically dispatched. A {@link Topic#subscribe()} should be normally used instead.
     *
     * @param topicName name of the topic to subscribe to
     * @param set values to be assign to topic on success.
     * @param get query for topic values.
     * @param background indicator that this request should be treated as a service request,
     *                   i.e. presence notifications will be delayed.
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu, Pr, T> PromisedReply<ServerMessage> subscribe(String topicName,
                                                              MsgSetMeta<Pu, Pr> set,
                                                              MsgGetMeta get, boolean background) {
        ClientMessage msg = new ClientMessage(new MsgClientSub<>(getNextId(), topicName, set, get, background));
        return sendWithPromise(msg, msg.sub.id);
    }

    /**
     * Low-level request to unsubscribe topic. A {@link Topic#leave(boolean)} should be normally
     * used instead.
     *
     * @param topicName name of the topic to subscribe to
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> leave(final String topicName, boolean unsub) {
        ClientMessage msg = new ClientMessage(new MsgClientLeave(getNextId(), topicName, unsub));
        return sendWithPromise(msg, msg.leave.id);
    }

    /**
     * Headers to be sent with an outgoing message.
     *
     * @param content message content
     * @return headers in as map "header key : header value"
     */
    public static Map<String, Object> draftyHeadersFor(final Drafty content) {
        Map<String, Object> head = new HashMap<>();
        head.put("mime", Drafty.MIME_TYPE);
        String[] refs = content.getEntReferences();
        if (refs != null) {
            head.put("attachments", refs);
        }
        return head;
    }

    /**
     * Low-level request to publish data. A {@link Topic#publish} should be normally
     * used instead.
     *
     * @param topicName name of the topic to publish to
     * @param data      payload to publish to topic
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> publish(String topicName, Object data, Map<String, Object> head) {
        ClientMessage msg = new ClientMessage(new MsgClientPub(getNextId(), topicName, true, data, head));
        return sendWithPromise(msg, msg.pub.id);
    }

    /**
     * Low-level request to query topic for metadata. A {@link Topic#getMeta} should be normally
     * used instead.
     *
     * @param topicName name of the topic to query.
     * @param query     metadata query
     * @return PromisedReply of the reply ctrl or meta message
     */
    public PromisedReply<ServerMessage> getMeta(final String topicName, final MsgGetMeta query) {
        ClientMessage msg = new ClientMessage(new MsgClientGet(getNextId(), topicName, query));
        return sendWithPromise(msg, msg.get.id);
    }

    /**
     * Low-level request to update topic metadata. A {@link Topic#setMeta} should be normally
     * used instead.
     *
     * @param topicName name of the topic to publish to
     * @param meta      metadata to assign
     * @return PromisedReply of the reply ctrl or meta message
     */
    public <Pu, Pr, T> PromisedReply<ServerMessage> setMeta(final String topicName,
                                                            final MsgSetMeta<Pu, Pr> meta) {
        ClientMessage msg = new ClientMessage(new MsgClientSet<>(getNextId(), topicName, meta));
        return sendWithPromise(msg, msg.set.id);
    }

    private PromisedReply<ServerMessage> sendDeleteMessage(ClientMessage msg) {
        return sendWithPromise(msg, msg.del.id);
    }

    /**
     * Low-level request to delete all messages from the topic with ids in the given range.
     * Use {@link Topic#delMessages(int, int, boolean)} instead.
     *
     * @param topicName name of the topic to inform
     * @param fromId    minimum ID to delete, inclusive (closed)
     * @param toId      maximum ID to delete, exclusive (open)
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> delMessage(final String topicName, final int fromId,
                                                   final int toId, final boolean hard) {
        return sendDeleteMessage(new ClientMessage(new MsgClientDel(getNextId(), topicName, fromId, toId, hard)));
    }

    /**
     * Low-level request to delete messages from a topic. Use {@link Topic#delMessages(MsgRange[], boolean)} instead.
     *
     * @param topicName name of the topic to inform
     * @param ranges    delete all messages with ids these ranges
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> delMessage(final String topicName, final MsgRange[] ranges, final boolean hard) {
        return sendDeleteMessage(new ClientMessage(new MsgClientDel(getNextId(), topicName, ranges, hard)));
    }

    /**
     * Low-level request to delete one message from a topic. Use {@link Topic#delMessages(MsgRange[], boolean)} instead.
     *
     * @param topicName name of the topic to inform
     * @param seqId     seqID of the message to delete.
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> delMessage(final String topicName, final int seqId, final boolean hard) {
        return sendDeleteMessage(new ClientMessage(new MsgClientDel(getNextId(), topicName, seqId, hard)));
    }

    /**
     * Low-level request to delete topic. Use {@link Topic#delete(boolean)} instead.
     *
     * @param topicName name of the topic to delete
     * @param hard hard-delete topic.
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> delTopic(final String topicName, boolean hard) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId(), topicName));
        msg.del.hard = hard;
        return sendWithPromise(msg, msg.del.id);
    }

    /**
     * Low-level request to delete a subscription. Use {@link Topic#eject(String, boolean)} ()} instead.
     *
     * @param topicName name of the topic
     * @param user      user ID to unsubscribe
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> delSubscription(final String topicName, final String user) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId(), topicName, user));
        return sendWithPromise(msg, msg.del.id);
    }

    /**
     * Low-level request to delete a credential. Use {@link MeTopic#delCredential(String, String)} ()} instead.
     *
     * @param cred  credential to delete.
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> delCredential(final Credential cred) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId(), cred));
        return sendWithPromise(msg, msg.del.id);
    }

    /**
     * Request to delete account of the current user.
     *
     * @param hard hard-delete
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("UnusedReturnValue")
    public PromisedReply<ServerMessage> delCurrentUser(boolean hard) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId()));
        msg.del.hard = hard;
        return sendWithPromise(msg, msg.del.id).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
            @Override
            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                disconnect();
                if (mStore != null) {
                    mStore.deleteAccount(mMyUid);
                }
                mMyUid = null;
                return null;
            }
        });
    }

    /**
     * Inform all other topic subscribers of activity, such as receiving/reading a message or a
     * typing notification.
     * This method does not return a PromisedReply because the server does not acknowledge {note}
     * packets.
     *
     * @param topicName name of the topic to inform
     * @param what      one or "read", "recv", "kp"
     * @param seq       id of the message being acknowledged
     */
    @SuppressWarnings("WeakerAccess")
    protected void note(String topicName, String what, int seq) {
        try {
            send(new ClientMessage(new MsgClientNote(topicName, what, seq)));
        } catch (JsonProcessingException | NotConnectedException ignored) {
        }
    }

    /**
     * Send typing notification to all other topic subscribers.
     * This method does not return a PromisedReply because the server does not acknowledge {note} packets.
     *
     * @param topicName name of the topic to inform
     */
    @SuppressWarnings("WeakerAccess")
    public void noteKeyPress(String topicName) {
        note(topicName, NOTE_KP, 0);
    }

    /**
     * Read receipt.
     * This method does not return a PromisedReply because the server does not acknowledge {note} packets.
     *
     * @param topicName name of the topic to inform
     * @param seq       id of the message being acknowledged
     */
    @SuppressWarnings("WeakerAccess")
    public void noteRead(String topicName, int seq) {
        note(topicName, NOTE_READ, seq);
    }

    /**
     * Received receipt.
     * This method does not return a PromisedReply because the server does not acknowledge {note} packets.
     *
     * @param topicName name of the topic to inform
     * @param seq       id of the message being acknowledged
     */
    @SuppressWarnings("WeakerAccess")
    public void noteRecv(String topicName, int seq) {
        note(topicName, NOTE_RECV, seq);
    }

    /**
     * Writes a string to websocket.
     *
     * @param message string to write to websocket
     */
    protected void send(String message) {
        if (mConnection == null || !mConnection.isConnected()) {
            throw new NotConnectedException("No connection");
        }
        Log.d(TAG, "out: " + message);
        mConnection.send(message);
    }

    /**
     * Takes {@link ClientMessage}, converts it to string writes to websocket.
     *
     * @param message string to write to websocket
     */
    protected void send(ClientMessage message) throws JsonProcessingException {
        send(Tinode.getJsonMapper().writeValueAsString(message));
    }

    protected PromisedReply<ServerMessage> sendWithPromise(ClientMessage message, String id) {
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        try {
            send(message);
            mFutures.put(id, new FutureHolder(future, new Date()));
        } catch (Exception ex1) {
            try {
                future.reject(ex1);
            } catch (Exception ex2) {
                Log.i(TAG, "Exception while rejecting the promise", ex2);
            }
        }
        return future;
    }

    /**
     * Instantiate topic of an appropriate class given the name.
     *
     * @param name name of the topic to create
     * @param l    event listener; could be null
     * @return topic of an appropriate class
     */
    public Topic newTopic(final String name, final Topic.Listener l) {
        return Tinode.newTopic(this, name, l);
    }

    @SuppressWarnings("unchecked")
    Topic newTopic(Subscription sub) {
        if (TOPIC_ME.equals(sub.topic)) {
            return new MeTopic(this, (MeTopic.MeListener) null);
        } else if (TOPIC_FND.equals(sub.topic)) {
            return new FndTopic(this, null);
        }
        return new ComTopic(this, sub);
    }

    public <DP> MeTopic<DP> getOrCreateMeTopic() {
        MeTopic<DP> me = getMeTopic();
        if (me == null) {
            me = new MeTopic<>(this, (MeTopic.MeListener<DP>) null);
        }
        return me;
    }

    public <DP> FndTopic<DP> getOrCreateFndTopic() {
        FndTopic<DP> fnd = getFndTopic();
        if (fnd == null) {
            fnd = new FndTopic<>(this, null);
        }
        return fnd;
    }

    @SuppressWarnings("unchecked, UnusedReturnValue")
    protected Topic maybeCreateTopic(MsgServerMeta meta) {
        if (meta.desc == null) {
            return null;
        }

        Topic topic;
        if (TOPIC_ME.equals(meta.topic)) {
            topic = new MeTopic(this, meta.desc);
        } else if (TOPIC_FND.equals(meta.topic)) {
            topic = new FndTopic(this, null);
        } else {
            topic = new ComTopic(this, meta.topic, meta.desc);
        }

        return topic;
    }

    /**
     * Obtain a 'me' topic ({@link MeTopic}).
     *
     * @return 'me' topic or null if 'me' has never been subscribed to
     */
    @SuppressWarnings("unchecked")
    public <DP> MeTopic<DP> getMeTopic() {
        return (MeTopic<DP>) getTopic(TOPIC_ME);
    }

    /**
     * Obtain a 'fnd' topic ({@link FndTopic}).
     *
     * @return 'fnd' topic or null if 'fnd' has never been subscribed to
     */
    @SuppressWarnings("unchecked")
    public <DP> FndTopic<DP> getFndTopic() {
        // Either I or Java really has problems with generics.
        return (FndTopic<DP>) getTopic(TOPIC_FND);
    }

    /**
     * Return a list of topics sorted by Topic.touched in descending order.
     *
     * @return a {@link List} of topics
     */
    @SuppressWarnings("unchecked")
    public Collection<Topic> getTopics() {
        List<Topic> result = new ArrayList<>(mTopics.values());
        Collections.sort(result);
        return result;
    }

    /**
     * Return a list of topics which satisfy the filters. Topics are sorted by
     * Topic.touched in descending order.
     *
     * @param filter filter object to select topics.
     * @return a {@link List} of topics
     */
    @SuppressWarnings("unchecked")
    public <T extends Topic> Collection<T> getFilteredTopics(TopicFilter filter) {
        if (filter == null) {
            return (Collection<T>) getTopics();
        }
        ArrayList<T> result = new ArrayList<>();
        for (T t : (Collection<T>) mTopics.values()) {
            if (filter.isIncluded(t)) {
                result.add(t);
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Get topic by name.
     *
     * @param name name of the topic to find.
     * @return existing topic or null if no such topic was found
     */
    public Topic<?, ?, ?, ?> getTopic(String name) {
        if (name == null) {
            return null;
        }
        return mTopics.get(name);
    }

    /**
     * Start tracking topic: add it to in-memory cache.
     */
    void startTrackingTopic(final Topic topic) {
        final String name = topic.getName();
        if (mTopics.containsKey(name)) {
            throw new IllegalStateException("Topic '" + name + "' is already registered");
        }
        mTopics.put(name, topic);
        topic.setStorage(mStore);
    }

    /**
     * Stop tracking the topic: remove it from in-memory cache.
     */
    void stopTrackingTopic(String topicName) {
        mTopics.remove(topicName);
    }

    /**
     * Check if topic is being tracked.
     */
    boolean isTopicTracked(String topicName) {
        return mTopics.containsKey(topicName);
    }

    /**
     * Topic is cached by name, update the name used to cache the topic.
     *
     * @param topic   topic being updated
     * @param oldName old name of the topic (e.g. "newXYZ" or "usrZYX")
     * @return true if topic was found by the old name
     */
    @SuppressWarnings("UnusedReturnValue")
    synchronized boolean changeTopicName(Topic topic, String oldName) {
        boolean found = mTopics.remove(oldName) != null;
        mTopics.put(topic.getName(), topic);
        if (mStore != null) {
            mStore.topicUpdate(topic);
        }
        return found;
    }

    /**
     * Look up user in a local cache: first in memory, then in persistent storage.
     *
     * @param uid ID of the user to find.
     * @return {@link User} object or null if no such user is found in local cache.
     */
    @SuppressWarnings("unchecked")
    public <SP> User<SP> getUser(String uid) {
        User<SP> user = mUsers.get(uid);
        if (user == null && mStore != null) {
            user = mStore.userGet(uid);
            if (user != null) {
                mUsers.put(uid, user);
            }
        }
        return user;
    }

    /**
     * Create blank user in cache: in memory and in persistent storage.
     *
     * @param uid ID of the user to create.
     * @return {@link User} created user.
     */
    <SP> User<SP> addUser(String uid) {
        User<SP> user = new User<>(uid);
        mUsers.put(uid, user);
        if (mStore != null) {
            mStore.userAdd(user);
        }
        return user;
    }

    @SuppressWarnings("unchecked")
    void updateUser(Subscription sub) {
        User user = mUsers.get(sub.user);
        if (user == null) {
            user = new User(sub);
            mUsers.put(sub.user, user);
        } else {
            user.merge(sub);
        }
        if (mStore != null) {
            mStore.userUpdate(user);
        }
    }

    @SuppressWarnings("unchecked")
    void updateUser(String uid, Description desc) {
        User user = mUsers.get(uid);
        if (user == null) {
            user = new User(uid, desc);
            mUsers.put(uid, user);
        } else {
            user.merge(desc);
        }
        if (mStore != null) {
            mStore.userUpdate(user);
        }
    }

    /**
     * Parse JSON received from the server into {@link ServerMessage}
     *
     * @param jsonMessage message to parse
     * @return ServerMessage or null
     */
    @SuppressWarnings("WeakerAccess")
    protected ServerMessage parseServerMessageFromJson(String jsonMessage) {
        ServerMessage msg = new ServerMessage();
        try {
            ObjectMapper mapper = Tinode.getJsonMapper();
            JsonParser parser = mapper.getFactory().createParser(jsonMessage);

            // Sanity check: verify that we got "Json Object":
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new JsonParseException(parser, "Packet must start with an object",
                        parser.getCurrentLocation());
            }
            // Iterate over object fields:
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.getCurrentName();
                parser.nextToken();
                JsonNode node = mapper.readTree(parser);
                switch (name) {
                    case "ctrl":
                        msg.ctrl = mapper.readValue(node.traverse(), MsgServerCtrl.class);
                        break;
                    case "pres":
                        msg.pres = mapper.readValue(node.traverse(), MsgServerPres.class);
                        break;
                    case "info":
                        msg.info = mapper.readValue(node.traverse(), MsgServerInfo.class);
                        break;
                    case "data":
                        msg.data = mapper.readValue(node.traverse(), MsgServerData.class);
                        break;
                    case "meta":
                        if (node.has("topic")) {
                            msg.meta = mapper.readValue(node.traverse(),
                                    getTypeOfMetaPacket(node.get("topic").asText()));
                        } else {
                            Log.w(TAG, "Failed to parse {meta}: missing topic name");
                        }
                        break;
                    default:  // Unrecognized field, ignore
                        Log.w(TAG, "Unknown field in packet: '" + name + "'");
                        break;
                }
            }
            parser.close(); // important to close both parser and underlying reader
        } catch (IOException e) {
            e.printStackTrace();
        }

        return msg.isValid() ? msg : null;
    }

    /**
     * Get a string representation of a unique number, to be used as a message id.
     *
     * @return unique message id
     */
    synchronized private String getNextId() {
        return String.valueOf(++mMsgId);
    }

    synchronized String nextUniqueString() {
        ++mNameCounter;
        return Long.toString(((new Date().getTime() - 1414213562373L) << 16) + (mNameCounter & 0xFFFF), 32);
    }

    long getTimeAdjustment() {
        return mTimeAdjustment;
    }

    public interface MimeTypeResolver {
        JavaType resolve(String mimeType);
    }

    /**
     * Interface to be implemented by those clients which want to fetch topics
     * using {@link Tinode#getFilteredTopics}
     */
    public interface TopicFilter<T extends Topic> {
        boolean isIncluded(T t);
    }

    /**
     * Callback interface called by Connection when it receives events from the websocket.
     * Default no-op method implementations are provided for convenience.
     */
    @SuppressWarnings("EmptyMethod")
    public static class EventListener {
        /**
         * Connection established successfully, handshakes exchanged. The connection is ready for
         * login.
         *
         * @param code   should be always 201
         * @param reason should be always "Created"
         * @param params server parameters, such as protocol version
         */
        public void onConnect(int code, String reason, Map<String, Object> params) {
        }

        /**
         * Connection was dropped
         *
         * @param byServer true if connection was closed by server
         * @param code     numeric code of the error which caused connection to drop
         * @param reason   error message
         */
        public void onDisconnect(boolean byServer, int code, String reason) {
        }

        /**
         * Result of successful or unsuccessful {@link #login} attempt.
         *
         * @param code a numeric value between 200 and 299 on success, 400 or higher on failure
         * @param text "OK" on success or error message
         */
        @SuppressWarnings("unused")
        public void onLogin(int code, String text) {
        }

        /**
         * Handle generic server message.
         *
         * @param msg message to be processed
         */
        @SuppressWarnings("unused, WeakerAccess")
        public void onMessage(ServerMessage msg) {
        }

        /**
         * Handle unparsed message. Default handler calls {@code #dispatchPacket(...)} on a
         * websocket thread.
         * A subclassed listener may wish to call {@code dispatchPacket()} on a UI thread
         *
         * @param msg message to be processed
         */
        @SuppressWarnings("unused, WeakerAccess")
        public void onRawMessage(String msg) {
        }

        /**
         * Handle control message
         *
         * @param ctrl control message to process
         */
        @SuppressWarnings("unused, WeakerAccess")
        public void onCtrlMessage(MsgServerCtrl ctrl) {
        }

        /**
         * Handle data message
         *
         * @param data control message to process
         */
        @SuppressWarnings("unused, WeakerAccess")
        public void onDataMessage(MsgServerData data) {
        }

        /**
         * Handle info message
         *
         * @param info info message to process
         */
        @SuppressWarnings("unused, WeakerAccess")
        public void onInfoMessage(MsgServerInfo info) {
        }

        /**
         * Handle meta message
         *
         * @param meta meta message to process
         */
        @SuppressWarnings("unused, WeakerAccess")
        public void onMetaMessage(MsgServerMeta meta) {
        }

        /**
         * Handle presence message
         *
         * @param pres control message to process
         */
        @SuppressWarnings("unused, WeakerAccess")
        public void onPresMessage(MsgServerPres pres) {
        }
    }

    // Helper class which calls given method of all added EventListener(s).
    private static class ListenerNotifier {
        private Vector<EventListener> listeners;

        ListenerNotifier() {
            listeners = new Vector<>();
        }

        synchronized void addListener(EventListener l) {
            if (!listeners.contains(l)) {
                listeners.add(l);
            }
        }
        synchronized boolean delListener(EventListener l) {
            return listeners.remove(l);
        }

        void onConnect(int code, String reason, Map<String, Object> params) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }

            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onConnect(code, reason, params);
            }
        }

        void onDisconnect(boolean byServer, int code, String reason) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }

            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onDisconnect(byServer, code, reason);
            }
        }
        void onLogin(int code, String text) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }
            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onLogin(code, text);
            }
        }
        void onMessage(ServerMessage msg) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }
            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onMessage(msg);
            }
        }
        void onRawMessage(String msg) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }
            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onRawMessage(msg);
            }
        }
        void onCtrlMessage(MsgServerCtrl ctrl) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }
            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onCtrlMessage(ctrl);
            }
        }
        void onDataMessage(MsgServerData data) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }
            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onDataMessage(data);
            }
        }
        void onInfoMessage(MsgServerInfo info) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }
            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onInfoMessage(info);
            }
        }
        void onMetaMessage(MsgServerMeta meta) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }
            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onMetaMessage(meta);
            }
        }
        void onPresMessage(MsgServerPres pres) {
            EventListener[] local;
            synchronized (this) {
                local = listeners.toArray(new EventListener[]{});
            }
            for (int i = local.length - 1; i >= 0; i--) {
                local[i].onPresMessage(pres);
            }
        }
    }

    private static class LoginCredentials {
        String scheme;
        String secret;

        LoginCredentials(String scheme, String secret) {
            this.scheme = scheme;
            this.secret = secret;
        }
    }

    /**
     * Scheduler for sending delayed recv notifications.
     */
    class HeartBeat extends Timer {
        public static final String TAG = "HeartBeat";

        private ConcurrentHashMap<String, Integer> recvQueue;

        public HeartBeat() {
            super(TAG, true);

            recvQueue = new ConcurrentHashMap<>();

            schedule(new TimerTask() {
                @Override
                public void run() {
                    Set<String> keyset = recvQueue.keySet();
                    for (String topic : keyset) {
                        @SuppressWarnings("ConstantConditions") int recv = recvQueue.remove(topic);
                        Tinode.this.noteRecv(topic, recv);
                    }
                }
            }, NOTE_RECV_DELAY / 2, NOTE_RECV_DELAY);
        }

        public void post(String topic, int recv) {
            recvQueue.put(topic, recv);
        }
    }

    private static class FutureHolder {
        PromisedReply<ServerMessage> future;
        Date timestamp;

        FutureHolder(PromisedReply<ServerMessage> future, Date timestamp) {
            this.future = future;
            this.timestamp = timestamp;
        }
    }
}