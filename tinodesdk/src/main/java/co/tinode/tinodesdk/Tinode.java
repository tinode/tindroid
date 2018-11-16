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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.ClientMessage;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.Description;
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
    private static final String TAG = "tinodesdk.Tinode";

    public static final String TOPIC_NEW = "new";
    public static final String TOPIC_ME = "me";
    public static final String TOPIC_FND = "fnd";
    public static final String TOPIC_GRP_PREFIX = "grp";
    public static final String TOPIC_USR_PREFIX = "usr";

    protected static final String NOTE_KP = "kp";
    protected static final String NOTE_READ = "read";
    protected static final String NOTE_RECV = "recv";

    // Delay in milliseconds between sending two key press notifications on the
    // same topic.
    private static final long NOTE_KP_DELAY = 3000L;

    // Delay in milliseconds before recv notification is sent
    private static final long NOTE_RECV_DELAY = 300L;

    private static final String PROTOVERSION = "0";
    private static final String VERSION = "0.15";
    private static final String LIBRARY = "tindroid/" + VERSION;

    private static ObjectMapper sJsonMapper;
    protected static TypeFactory sTypeFactory;

    private JavaType mDefaultTypeOfMetaPacket = null;
    private HashMap<Topic.TopicType, JavaType> mTypeOfMetaPacket;

    private MimeTypeResolver mMimeResolver = null;

    protected static SimpleDateFormat sDateFormat;

    private Storage mStore = null;

    private String mApiKey = null;
    private String mServerHost = null;
    private boolean mUseTLS;

    private String mDeviceToken = null;
    private String mLanguage = null;

    private String mAppName = null;
    private String mOsVersion = null;

    private Connection mConnection = null;
    // True is connection is authenticated
    private boolean mConnAuth = false;

    private String mServerVersion = null;
    private String mServerBuild = null;

    private boolean mAutologin = false;
    private LoginCredentials mLoginCredentials = null;

    private String mMyUid = null;
    private String mAuthToken = null;
    private Date mAuthTokenExpires = null;

    private int mMsgId;
    private int mPacketCount;

    private EventListener mListener;

    private ConcurrentMap<String, PromisedReply<ServerMessage>> mFutures;
    private HashMap<String, Topic> mTopics;
    private HashMap<String, User> mUsers;

    private transient int mNameCounter = 0;
    private boolean mTopicsLoaded = false;

    // The difference between server time and local time.
    private long mTimeAdjustment = 0;

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
        mListener = listener;

        mTypeOfMetaPacket = new HashMap<>();

        mFutures = new ConcurrentHashMap<>(16, 0.75f, 4);

        mTopics = new HashMap<>();
        mUsers = new HashMap<>();

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

    @SuppressWarnings("UnusedReturnValue")
    public EventListener setListener(EventListener listener) {
        EventListener oldListener = mListener;
        mListener = listener;
        return oldListener;
    }

    /** Set non-default version of OS string for User-Agent */
    public void setOsString(String os) {
        mOsVersion = os;
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean loadTopics() {
        if (mStore != null && mStore.isReady() && !mTopicsLoaded) {
            Topic[] topics = mStore.topicGetAll(this);
            if (topics != null) {
                for (Topic tt : topics) {
                    tt.setStorage(mStore);
                    mTopics.put(tt.getName(), tt);
                }
                mTopicsLoaded = true;
            }
        }
        return mTopicsLoaded;
    }

    /**
     * Open a websocket connection to the server and process handshake exchange.
     *
     * @param hostName address of the server to connect to
     * @param tls use transport layer security (wss)
     * @return returns promise which will be resolved when the connection sequence is completed.
     * @throws URISyntaxException if hostName is not a valid internet address
     * @throws IOException if connection call has failed
     */
    public PromisedReply<ServerMessage> connect(String hostName, boolean tls) throws URISyntaxException, IOException {
        mUseTLS = tls;

        if (mConnection != null && mConnection.isConnected()) {
            // If the connection is live, return a resolved promise
            return new PromisedReply<>((ServerMessage) null);
        }

        // Set up a new connection and a new promise
        mServerHost = hostName;
        mMsgId = 0xFFFF + (int) (Math.random() * 0xFFFF);

        final PromisedReply<ServerMessage> connected = new PromisedReply<>();
        mConnection = new Connection(
                new URI((tls ? "wss://" : "ws://") + mServerHost + "/v" + PROTOVERSION + "/"),
                mApiKey, new Connection.WsListener() {

            @Override
            protected void onConnect(final boolean autoreconnected) {
                try {
                    // Connection established, send handshake, inform listener on success
                    PromisedReply<ServerMessage> future = hello().thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                                    // If this is an auto-reconnect, the promise is already resolved.
                                    if (!connected.isDone()) {
                                        connected.resolve(pkt);
                                    }

                                    mTimeAdjustment = pkt.ctrl.ts.getTime() - new Date().getTime();
                                    if (mStore != null) {
                                        mStore.setTimeAdjustment(mTimeAdjustment);
                                    }

                                    if (mListener != null) {
                                        mListener.onConnect(pkt.ctrl.code, pkt.ctrl.text, pkt.ctrl.params);
                                    }

                                    return null;
                                }
                            }, null);
                    if (mAutologin && mLoginCredentials != null) {
                        future.thenApply(
                                new PromisedReply.SuccessListener<ServerMessage>() {
                                    @Override
                                    public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                                        login(mLoginCredentials.scheme, mLoginCredentials.secret, null);
                                        return null;
                                    }
                                }, null);
                    }
                } catch (NotConnectedException ignored) {
                    Log.d(TAG, "NotConnectedException in autoreconnect");
                    // Do nothing
                } catch (Exception e) {
                    Log.e(TAG, "Exception in Connection.onConnect: ", e);
                }
            }

            @Override
            protected void onMessage(String message) {
                try {
                    dispatchPacket(message);
                } catch (Exception e) {
                    Log.e(TAG, "Exception in dispatchPacket: ", e);
                }
            }

            @Override
            protected void onDisconnect(boolean byServer, int code, String reason) {
                handleDisconnect(byServer, -code, reason);
            }

            @Override
            protected void onError(Exception err) {
                handleDisconnect(true, 0, err.getMessage());
                // If the promise is waiting, reject. Otherwise it's not our problem.
                if (!connected.isDone()) {
                    try {
                        connected.reject(err);
                    } catch (Exception ignored) {
                        // There is no rejection handler ths there should not be an exception
                    }
                }
            }
        });

        // true means autoreconnect
        mConnection.connect(true);

        return connected;
    }

    /**
     * Get configured server address as an URL.
     * @return Server URL.
     * @throws MalformedURLException thrown if server address is not yet configured.
     */
    public URL getBaseUrl() throws MalformedURLException {
        return new URL((mUseTLS ? "https://" : "http://") + mServerHost + "/v" + PROTOVERSION + "/");
    }

    /**
     * If websocket is valid and not connected, force an immediate reconnect attempt.
     * If it's not initialized or already connected do nothing.
     *
     * @return true if it actually attempted to reconnect, false otherwise.
     * @throws IOException thrown from {@link Connection#connect(boolean)}
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean reconnectNow() throws IOException {
        if (mConnection == null || mConnection.isConnected()) {
            // If the connection is live, return a resolved promise
            return false;
        }

        mConnection.connect(true);

        return true;
    }

    private void handleDisconnect(boolean byServer, int code, String reason) {
        mFutures.clear();

        mConnAuth = false;

        // TODO(gene): should this be cleared?
        mServerBuild = null;
        mServerVersion = null;

        for (Topic topic : mTopics.values()) {
            topic.topicLeft(false, 503, "disconnected");
        }
        if (mListener != null) {
            mListener.onDisconnect(byServer, code, reason);
        }
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

        if (mListener != null) {
            mListener.onRawMessage(message);
        }

        ServerMessage pkt = parseServerMessageFromJson(message);
        if (pkt == null) {
            Log.i(TAG, "Failed to parse packet");
            return;
        }

        if (mListener != null) {
            mListener.onMessage(pkt);
        }

        if (pkt.ctrl != null) {
            if (mListener != null) {
                mListener.onCtrlMessage(pkt.ctrl);
            }

            if (pkt.ctrl.id != null) {
                PromisedReply<ServerMessage> r = mFutures.remove(pkt.ctrl.id);
                if (r != null) {
                    if (pkt.ctrl.code >= 200 && pkt.ctrl.code < 400) {
                        r.resolve(pkt);
                    } else {
                        r.reject(new ServerResponseException(pkt.ctrl.code, pkt.ctrl.text,
                                pkt.ctrl.getStringParam("what")));
                    }
                }
            }

            if ("data".equals(pkt.ctrl.getStringParam("what"))) {
                Topic topic = getTopic(pkt.ctrl.topic);
                if (topic != null) {
                    topic.allMessagesReceived(pkt.ctrl.getIntParam("count"));
                }
            }
        } else if (pkt.meta != null) {
            Topic topic = getTopic(pkt.meta.topic);
            if (topic != null) {
                topic.routeMeta(pkt.meta);
            } else {
                maybeCreateTopic(pkt.meta);
            }

            if (mListener != null) {
                mListener.onMetaMessage(pkt.meta);
            }

            resolveWithPacket(pkt.meta.id, pkt);

        } else if (pkt.data != null) {
            Topic topic = getTopic(pkt.data.topic);
            if (topic != null) {
                topic.routeData(pkt.data);
            }

            if (mListener != null) {
                mListener.onDataMessage(pkt.data);
            }

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

            if (mListener != null) {
                mListener.onPresMessage(pkt.pres);
            }
        } else if (pkt.info != null) {
            Topic topic = getTopic(pkt.info.topic);
            if (topic != null) {
                topic.routeInfo(pkt.info);
            }

            if (mListener != null) {
                mListener.onInfoMessage(pkt.info);
            }
        }

        // TODO(gene): decide what to do on unknown message type
    }

    private void resolveWithPacket(String id, ServerMessage pkt) throws Exception {
        if (id != null) {
            PromisedReply<ServerMessage> r = mFutures.remove(id);
            if (r != null && !r.isDone()) {
                r.resolve(pkt);
            }
        }
    }

    public String getApiKey() {
        return mApiKey;
    }

    public String getServerHost() {
        return mServerHost;
    }

    public String getMyId() {
         return mMyUid;
    }
    public boolean isMe(String uid) {
        return mMyUid != null && mMyUid.equals(uid);
    }

    public String getAuthToken() {
        return mAuthToken;
    }

    public Date getAuthTokenExpiration() {
        return mAuthTokenExpires;
    }

    public boolean isAuthenticated() {
        return mConnAuth;
    }

    public String getServerVersion() {
        return mServerVersion;
    }

    public String getServerBuild() {
        return mServerBuild;
    }

    public boolean isConnected() {
        return mConnection != null && mConnection.isConnected();
    }

    @SuppressWarnings("WeakerAccess")
    public static TypeFactory getTypeFactory() {
        return sTypeFactory;
    }

    @SuppressWarnings("WeakerAccess")
    public static ObjectMapper getJsonMapper() {
        return sJsonMapper;
    }

    // FIXME(gene): this is broken. Figure out how to handle nulls
    public static boolean isNull(Object obj) {
        // Del control character
        return (obj instanceof String) && obj.equals("\u2421");
    }

    public static String jsonSerialize(Object o) throws JsonProcessingException {
        return sJsonMapper.writeValueAsString(o);
    }

    public static <T> T jsonDeserialize(String input, String canonicalName) {
        try {
            return sJsonMapper.readValue(input, sTypeFactory.constructFromCanonical(canonicalName));
        } catch (IOException e) {
            return null;
        }
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

    @SuppressWarnings("WeakerAccess")
    private JavaType getDefaultTypeOfMetaPacket() {
        return mDefaultTypeOfMetaPacket;
    }

    /**
     * Assign types of generic parameters to topic type. Needed for packet deserialization.
     *
     * @param topicName  - name of the topic to assign type values for.
     * @param typeOfDescPublic  - type of public values
     * @param typeOfDescPrivate - type of private values
     * @param typeOfSubPublic  - type of public values
     * @param typeOfSubPrivate - type of private values
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

    public LargeFileHelper getFileUploader() {
        URL url = null;
        try {
            url = new URL(getBaseUrl(), "./file/u/");
        } catch (MalformedURLException ignored) {}
        return new LargeFileHelper(url, getApiKey(), getAuthToken(), makeUserAgent());
    }
    /**
     * Set device token for push notifications
     * @param token device token
     */
    public void setDeviceToken(String token) {
        if (token == null) {
            return;
        }

        if (mDeviceToken == null || !mDeviceToken.equals(token)) {
            // Token changed
            mDeviceToken = token;
            if (isConnected() && isAuthenticated()) {
                ClientMessage msg = new ClientMessage(new MsgClientHi(null, null,null,
                        mDeviceToken, null));
                try {
                    send(Tinode.getJsonMapper().writeValueAsString(msg));
                } catch (JsonProcessingException ignored) {}
            }
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
     * @throws IOException if there is no connection
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> hello() throws Exception {
        ClientMessage msg = new ClientMessage(new MsgClientHi(getNextId(), VERSION,
                makeUserAgent(), mDeviceToken, mLanguage));
        try {
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.hi.id, future);
            future = future.thenApply(
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
                    }, null);
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            return future;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Create new account. Connection must be established prior to calling this method.
     *
     * @param uid uid of the user to affect
     * @param scheme authentication scheme to use
     * @param secret authentication secret for the chosen scheme
     * @param loginNow use the new account to login immediately
     * @param desc default access parameters for this account
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("WeakerAccess")
    protected <Pu,Pr> PromisedReply<ServerMessage> account(String uid, String scheme, String secret,
                                                           boolean loginNow, String[] tags, MetaSetDesc<Pu,Pr> desc,
                                                           Credential[] cred) throws Exception {
        ClientMessage msg = new ClientMessage<>(
                new MsgClientAcc<>(getNextId(), uid, scheme, secret, loginNow, desc));
        try {
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
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.acc.id, future);
            if (loginNow) {
                future = future.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) {
                        try {
                            loginSuccessful(pkt.ctrl);
                        } catch (Exception ex) {
                            Log.d(TAG, "Exception while parsing server response", ex);
                        }
                        return null;
                    }
                }, null);
            }
            return future;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Create account using a single basic authentication scheme. A connection must be established
     * prior to calling this method.
     *
     * @param uname    user name
     * @param password password
     * @param login use the new account for authentication
     * @param desc account parameters, such as full name etc.
     *
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu,Pr> PromisedReply<ServerMessage> createAccountBasic(
            String uname, String password, boolean login, MetaSetDesc<Pu,Pr> desc) throws Exception {
        return account(null, AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password),
                login, null, desc, null);
    }

    /**
     * Create account using a single basic authentication scheme. A connection must be established
     * prior to calling this method.
     *
     * @param uname    user name
     * @param password password
     * @param login use the new account for authentication
     * @param tags discovery tags
     * @param desc account parameters, such as full name etc.
     *
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu,Pr> PromisedReply<ServerMessage> createAccountBasic(
            String uname, String password, boolean login, String []tags, MetaSetDesc<Pu,Pr> desc) throws Exception {
        return account(null, AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password),
                login, tags, desc, null);
    }

    /**
     * Create account using a single basic authentication scheme. A connection must be established
     * prior to calling this method.
     *
     * @param uname    user name
     * @param password password
     * @param login use the new account for authentication
     * @param tags discovery tags
     * @param desc account parameters, such as full name etc.
     * @param cred account credential, such as email or phone
     *
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu,Pr> PromisedReply<ServerMessage> createAccountBasic(
            String uname, String password, boolean login, String []tags, MetaSetDesc<Pu,Pr> desc, Credential[] cred) throws Exception {
        return account(null, AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password),
                login, tags, desc, cred);
    }

    @SuppressWarnings("unchecked")
    protected PromisedReply<ServerMessage> updateAccountSecret(String uid, String scheme, String secret) throws Exception {
        return account(uid, scheme, secret, false, null, null, null);
    }

    public PromisedReply<ServerMessage> updateAccountBasic(String uid, String uname, String password) throws Exception {
        return updateAccountSecret(uid, AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password));
    }

    /**
     * Send a basic login packet to the server. A connection must be established prior to calling
     * this method. Success or failure will be reported through {@link EventListener#onLogin(int, String)}
     *
     * @param uname    user name
     * @param password password
     * @return PromisedReply of the reply ctrl message
     * @throws Exception if there is no connection
     */
    public PromisedReply<ServerMessage> loginBasic(String uname, String password) throws Exception {
        return login(AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password), null);
    }

    /**
     * Send a basic login packet to the server. A connection must be established prior to calling
     * this method. Success or failure will be reported through {@link EventListener#onLogin(int, String)}
     *
     * @param token server-provided security token
     * @param creds validation credentials.
     * @return PromisedReply of the reply ctrl message
     * @throws Exception if there is no connection
     */
    public PromisedReply<ServerMessage> loginToken(String token, Credential[] creds) throws Exception {
        return login(AuthScheme.LOGIN_TOKEN, token, creds);
    }

    /**
     * Send a basic login packet to the server. A connection must be established prior to calling
     * this method. Success or failure will be reported through {@link EventListener#onLogin(int, String)}
     *
     * @param token   server-provided security token
     * @return PromisedReply of the reply ctrl message
     * @throws Exception if there is no connection
     */
    public PromisedReply<ServerMessage> loginToken(String token) throws Exception {
        return loginToken(token, null);
    }

    public PromisedReply<ServerMessage> requestResetSecret(String scheme, String method, String value) throws Exception {
        return login(AuthScheme.LOGIN_RESET, AuthScheme.encodeResetSecret(scheme, method, value), null);
    }

    protected PromisedReply<ServerMessage> login(String combined) throws Exception {
        AuthScheme auth = AuthScheme.parse(combined);
        if (auth != null) {
            return login(auth.scheme, auth.secret, null);
        }
        throw new IllegalArgumentException();
    }

    private void loginSuccessful(final MsgServerCtrl ctrl) throws InvalidObjectException, ParseException {
        if (ctrl == null) {
            throw new InvalidObjectException("Unexpected type of reply packet");
        }

        String newUid = ctrl.getStringParam("user");
        if (mMyUid != null && !mMyUid.equals(newUid)) {
            logout();
            if (mListener != null) {
                mListener.onLogin(400, "UID mismatch");
            }
            return;
        }

        mMyUid = newUid;

        if (mStore != null) {
            mStore.setMyUid(mMyUid);
        }
        // If topics were not loaded earlier, load them now.
        loadTopics();
        mAuthToken = ctrl.getStringParam("token");
        mAuthTokenExpires = sDateFormat.parse(ctrl.getStringParam("expires"));
        if (ctrl.code < 300) {
            mConnAuth = true;
            if (mListener != null) {
                mListener.onLogin(ctrl.code, ctrl.text);
            }
        }
    }

    protected PromisedReply<ServerMessage> login(String scheme, String secret, Credential[] creds) throws Exception {
        if (mAutologin) {
            mLoginCredentials = new LoginCredentials(scheme, secret);
        }

        if (isAuthenticated()) {
            // Don't try to login again if we are logged in.
            return new PromisedReply<>((ServerMessage) null);
        }

        ClientMessage msg = new ClientMessage(new MsgClientLogin(getNextId(), scheme, secret));
        if (creds != null) {
            for (Credential c : creds) {
                msg.login.addCred(c);
            }
        }

        send(Tinode.getJsonMapper().writeValueAsString(msg));
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.login.id, future);
        future = future.thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                        loginSuccessful(pkt.ctrl);
                        return null;
                    }
                },
                new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        if (err instanceof ServerResponseException) {
                            ServerResponseException sre = (ServerResponseException) err;
                            final int code = sre.getCode();
                            if (code >= 400 && code < 500) {
                                mLoginCredentials = null;
                                mAuthToken = null;
                                mAuthTokenExpires = null;
                            }

                            mConnAuth = false;

                            if (mListener != null) {
                                mListener.onLogin(sre.getCode(), sre.getMessage());
                            }
                        }
                        // The next handler is rejected as well.
                        return new PromisedReply<>(err);
                    }
                });
        return future;
    }

    public void setAutologin(boolean state) {
        mAutologin = state;
    }

    public void disconnect() {
        setAutologin(false);
        if (mConnection != null) {
            mConnection.disconnect();
        }
    }

    public void logout() {
        disconnect();
        mMyUid = null;

        if (mStore != null) {
            mStore.logout();
        }
    }

    /**
     * Low-level subscription request. The subsequent messages on this topic will not
     * be automatically dispatched. A {@link Topic#subscribe()} should be normally used instead.
     *
     * @param topicName name of the topic to subscribe to
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu,Pr,T> PromisedReply<ServerMessage> subscribe(String topicName,
                                                              MsgSetMeta<Pu,Pr> set,
                                                              MsgGetMeta get) {
        ClientMessage msg = new ClientMessage(new MsgClientSub<>(getNextId(), topicName, set, get));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
        } catch (JsonProcessingException ignored) { }
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.sub.id, future);
        return future;
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
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
        } catch (JsonProcessingException ignored) { }
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.leave.id, future);
        return future;
    }


    /**
     * Low-level request to publish data. A {@link Topic#publish} should be normally
     * used instead.
     *
     * @param topicName name of the topic to publish to
     * @param data      payload to publish to topic
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("unchecked, WeakerAccess")
    public PromisedReply<ServerMessage> publish(String topicName, Object data) {
        ClientMessage msg = new ClientMessage(new MsgClientPub(getNextId(), topicName, true, data));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
        } catch (JsonProcessingException ignored) { }
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.pub.id, future);
        return future;
    }

    /**
     * Low-level request to query topic for metadata. A {@link Topic#getMeta} should be normally
     * used instead.
     *
     * @param topicName name of the topic to publish to
     * @param query metadata query
     * @return PromisedReply of the reply ctrl or meta message
     */
    public PromisedReply<ServerMessage> getMeta(final String topicName, final MsgGetMeta query) {
        ClientMessage msg = new ClientMessage(new MsgClientGet(getNextId(), topicName, query));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
        } catch (JsonProcessingException ignored) {}
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.get.id, future);
        return future;
    }

    /**
     * Low-level request to update topic metadata. A {@link Topic#setMeta} should be normally
     * used instead.
     *
     * @param topicName name of the topic to publish to
     * @param meta metadata to assign
     * @return PromisedReply of the reply ctrl or meta message
     */
    @SuppressWarnings("WeakerAccess")
    public <Pu,Pr,T> PromisedReply<ServerMessage> setMeta(final String topicName,
                                                            final MsgSetMeta<Pu,Pr> meta) {
        ClientMessage msg = new ClientMessage(new MsgClientSet<>(getNextId(), topicName, meta));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
        } catch (JsonProcessingException ignored) {}
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.set.id, future);
        return future;
    }

    private PromisedReply<ServerMessage> sendDeleteMessage(ClientMessage msg) {
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
        } catch (JsonProcessingException ignored) {}
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.del.id, future);
        return future;
    }

    /**
     * Low-level request to delete all messages from the topic with ids in the given range.
     * Use {@link Topic#delMessages(int, int, boolean)} instead.
     *
     * @param topicName name of the topic to inform
     * @param fromId minimum ID to delete, inclusive (closed)
     * @param toId maximum ID to delete, exclusive (open)
     * @return PromisedReply of the reply ctrl or meta message
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> delMessage(final String topicName, final int fromId,
                                                   final int toId, final boolean hard) {
        return sendDeleteMessage(new ClientMessage(new MsgClientDel(getNextId(), topicName, fromId, toId, hard)));
    }

    /**
     * Low-level request to delete messages from a topic. Use {@link Topic#delMessages(List, boolean)} instead.
     *
     * @param topicName name of the topic to inform
     * @param list delete all messages with ids in this list
     * @return PromisedReply of the reply ctrl or meta message
     */
    public PromisedReply<ServerMessage> delMessage(final String topicName, final List<Integer> list, final boolean hard) {
        return sendDeleteMessage(new ClientMessage(new MsgClientDel(getNextId(), topicName, list, hard)));
    }

    /**
     * Low-level request to delete one message from a topic. Use {@link Topic#delMessages(List, boolean)} instead.
     *
     * @param topicName name of the topic to inform
     * @param seqId seqID of the message to delete.
     * @return PromisedReply of the reply ctrl or meta message
     */
    public PromisedReply<ServerMessage> delMessage(final String topicName, final int seqId, final boolean hard) {
        return sendDeleteMessage(new ClientMessage(new MsgClientDel(getNextId(), topicName, seqId, hard)));
    }

    /**
     * Low-level request to delete topic. Use {@link Topic#delete()} instead.
     *
     * @param topicName name of the topic to inform
     * @return PromisedReply of the reply ctrl or meta message
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> delTopic(final String topicName) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId(), topicName));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
        } catch (JsonProcessingException ignored) {}
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.del.id, future);
        return future;
    }

    /**
     * Low-level request to delete a subscription. Use {@link Topic#eject(String, boolean)} ()} instead.
     *
     * @param topicName name of the topic
     * @param user user ID to unsubscribe
     * @return PromisedReply of the reply ctrl or meta message
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> delSubscription(final String topicName, final String user) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId(), topicName, user));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
        } catch (JsonProcessingException ignored) { }
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        mFutures.put(msg.del.id, future);
        return future;
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
            send(Tinode.getJsonMapper().writeValueAsString(
                    new ClientMessage(new MsgClientNote(topicName, what, seq))));
        } catch (JsonProcessingException ignored) { }
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
     * @param seq id of the message being acknowledged
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
     * @param seq id of the message being acknowledged
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
    @SuppressWarnings("WeakerAccess")
    protected void send(String message) {
        if (mConnection == null || !mConnection.isConnected()) {
            throw new NotConnectedException("No connection");
        }
        Log.d(TAG, "out: " + message);
        mConnection.send(message);
    }

    /**
     * Instantiate topic of an appropriate class given the name.
     *
     * @param name name of the topic to create
     * @param l event listener; could be null
     * @return topic of an appropriate class
     */
    @SuppressWarnings("unchecked")
    public Topic newTopic(String name, Topic.Listener l) {
        if (TOPIC_ME.equals(name)) {
            return new MeTopic(this, l);
        } else if (TOPIC_FND.equals(name)) {
            return new FndTopic(this, l);
        }
        return new ComTopic(this, name, l);
    }

    @SuppressWarnings("unchecked")
    Topic newTopic(Subscription sub) {
        if (TOPIC_ME.equals(sub.topic)) {
            return new MeTopic(this, (MeTopic.MeListener)null);
        } else if (TOPIC_FND.equals(sub.topic)) {
            return new FndTopic(this, null);
        }
        return new ComTopic(this, sub);
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

        registerTopic(topic);
        return topic;
    }

    /**
     * Obtain a 'me' topic ({@link MeTopic}).
     *
     * @return 'me' topic or null if 'me' has never been subscribed to
     */
    public MeTopic getMeTopic() {
        return (MeTopic) getTopic(TOPIC_ME);
    }

    /**
     * Obtain a 'fnd' topic ({@link FndTopic}).
     *
     * @return 'fnd' topic or null if 'fnd' has never been subscribed to
     */
    public FndTopic getFndTopic() {
        // Either I or Java really has problems with generics.
        return (FndTopic) getTopic(TOPIC_FND);
    }
    /**
     * Obtain an existing topic by name
     *
     * @return a {@link Collection} of topics
     */
    @SuppressWarnings("WeakerAccess")
    public List<Topic> getTopics() {
        return new ArrayList<>(mTopics.values());
    }

    /**
     * Return a collection of topics which satisfy the filters.
     *
     * @param type type of topics to return.
     * @param updated return topics with update timestamp after this
     */
    @SuppressWarnings("unchecked")
    public <T extends Topic> List<T> getFilteredTopics(Topic.TopicType type, Date updated) {
        if (type == Topic.TopicType.ANY && updated == null) {
            return (List<T>) getTopics();
        }
        if (type == Topic.TopicType.UNKNOWN) {
            return null;
        }
        ArrayList<T> result = new ArrayList<>();
        for (T t : (Collection<T>) mTopics.values()) {
            if (t.getTopicType().compare(type) &&
                    (updated == null || updated.before(t.getUpdated()))) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Get topic by index.
     *
     * @param name name of the topic to find
     * @return existing topic or null if no such topic was found
     */
    @SuppressWarnings("unchecked")
    public Topic getTopic(String name) {
        if (name == null) {
            return null;
        }
        return mTopics.get(name);
    }

    /**
     * Start tracking topic.
     */
    void registerTopic(Topic topic) {
        if (mStore != null) {
            if (!topic.isPersisted()) {
                mStore.topicAdd(topic);
            }
            topic.setStorage(mStore);
        }
        mTopics.put(topic.getName(), topic);
    }

    /**
     * Stop tracking the topic, delete it from local storage.
     */
    void unregisterTopic(String topicName) {
        Topic topic = mTopics.remove(topicName);
        if (topic != null && mStore != null) {
            topic.setStorage(null);
            mStore.topicDelete(topic);
        }
    }

    /**
     * Topic is cached by name, update the name used to cache the topic.
     *
     * @param topic topic being updated
     * @param oldName old name of the topic (e.g. "newXYZ" or "usrZYX")
     * @return true if topic was found by the old name
     */
    @SuppressWarnings("UnusedReturnValue")
    synchronized boolean changeTopicName(Topic topic, String oldName) {
        boolean found = mTopics.remove(oldName) != null;
        mTopics.put(topic.getName(), topic);
        return found;
    }

    @SuppressWarnings("unchecked")
    User getUser(String uid) {
        User user = mUsers.get(uid);
        if (user == null && mStore != null) {
            user = mStore.userGet(uid);
            if (user != null) {
                mUsers.put(uid, user);
            }
        }
        return user;
    }

    void addUser(String uid) {
        User user = new User(uid);
        mUsers.put(uid, user);
        if (mStore != null) {
            mStore.userAdd(user);
        }
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
                            Log.d(TAG, "parse meta - FAILED");
                        }
                        break;
                    default:  // Unrecognized field, ignore
                        Log.i(TAG, "Unknown field in packet: '" + name + "'");
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

    /**
     * Get minimum delay between two subsequent key press notifications.
     */
    @SuppressWarnings("WeakerAccess")
    protected static long getKeyPressDelay() {
        return NOTE_KP_DELAY;
    }

    synchronized String nextUniqueString() {
        ++ mNameCounter;
        return Long.toString(((new Date().getTime() - 1414213562373L) << 16) + (mNameCounter & 0xFFFF), 32);
    }

    long getTimeAdjustment() {
        return mTimeAdjustment;
    }

    /**
     * Callback interface called by Connection when it receives events from the websocket.
     */
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
         * @param code a numeric value between 200 and 2999 on success, 400 or higher on failure
         * @param text "OK" on success or error message
         */
        @SuppressWarnings("unused, WeakerAccess")
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

    private static class LoginCredentials {
        String scheme;
        String secret;

        LoginCredentials(String scheme, String secret) {
            this.scheme = scheme;
            this.secret = secret;
        }
    }

    public interface MimeTypeResolver {
        JavaType resolve(String mimeType);
    }

    /**
     * Scheduler for sending delayed recv notifications.
     */
    class HeartBeat extends Timer {
        public static final String TAG = "HeartBeat";

        private ConcurrentHashMap<String,Integer> recvQueue;

        public HeartBeat() {
            super(TAG, true);

            recvQueue = new ConcurrentHashMap<>();

            schedule(new TimerTask() {
                @Override
                public void run() {
                    Set<String> keyset = recvQueue.keySet();
                    for (String topic : keyset) {
                        int recv = recvQueue.remove(topic);
                        Tinode.this.noteRecv(topic, recv);
                    }
                }
            }, NOTE_RECV_DELAY / 2, NOTE_RECV_DELAY);
        }

        public void post(String topic, int recv) {
            recvQueue.put(topic, recv);
        }
    }
}