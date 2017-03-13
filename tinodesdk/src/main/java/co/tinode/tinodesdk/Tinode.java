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
import java.net.URI;
import java.net.URISyntaxException;
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
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.MetaSetDesc;

public class Tinode {
    private static final String TAG = "tinodesdk.Tinode";

    public static final String TOPIC_NEW = "new";
    public static final String TOPIC_ME = "me";

    protected static final String NOTE_KP = "kp";
    protected static final String NOTE_READ = "read";
    protected static final String NOTE_RECV = "recv";

    // Delay in milliseconds between sending two key press notifications on the
    // same topic.
    private static final long NOTE_KP_DELAY = 3000L;

    // Delay in milliseconds before recv notification is sent
    private static final long NOTE_RECV_DELAY = 300L;

    private static final String PROTOVERSION = "0";
    private static final String VERSION = "0.8";
    private static final String LIBRARY = "tindroid/" + VERSION;

    private static ObjectMapper sJsonMapper;
    private static TypeFactory sTypeFactory;

    protected JavaType mTypeOfDataPacket;
    protected JavaType mTypeOfMetaPacket;

    protected static SimpleDateFormat sDateFormat;

    private Storage mStore = null;

    private String mApiKey = null;
    private String mServerHost = null;

    private String mDeviceToken = null;
    private String mLanguage = null;

    private String mAppName = null;

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
    private boolean mTopicsLoaded = false;

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
        mApiKey = apikey;
        mListener = listener;

        mFutures = new ConcurrentHashMap<>(16, 0.75f, 4);

        mTopics = new HashMap<>();
        mStore = store;
        if (mStore != null) {
            mMyUid = mStore.getMyUid();
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

    public EventListener setListener(EventListener listener) {
        EventListener oldListener = mListener;
        mListener = listener;
        return oldListener;
    }

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
     * @return returns promise which will be resolved when the connection sequence is completed.
     * @throws URISyntaxException if hostName is not a valid internet address
     * @throws IOException if connection call has failed
     */
    public PromisedReply<ServerMessage> connect(String hostName) throws URISyntaxException, IOException {

        if (mConnection != null && mConnection.isConnected()) {
            // If the connection is live, return a resolved promise
            return new PromisedReply<>((ServerMessage) null);
        }

        // Set up a new connection and a new promise
        mServerHost = hostName;
        mMsgId = 0xFFFF + (int) (Math.random() * 0xFFFF);

        final PromisedReply<ServerMessage> connected = new PromisedReply<>();
        mConnection = new Connection(
                new URI("ws://" + mServerHost + "/v" + PROTOVERSION + "/"),
                mApiKey, new Connection.WsListener() {

            @Override
            protected void onConnect(final boolean autoreconnected) {
                try {
                    // FIXME: this is broken when autoreconnect = true
                    // Connection established, send handshake, inform listener on success
                    PromisedReply<ServerMessage> future = hello().thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                                    if (!autoreconnected) {
                                        // If this is an auto-reconnect, the promise is already resolved.
                                        connected.resolve(pkt);
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
                                        login(mLoginCredentials.scheme, mLoginCredentials.secret);
                                        return null;
                                    }
                                }, null);
                    }
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
                        Log.d(TAG, "Rejecting packet");
                        r.reject(new ServerResponseException(pkt.ctrl.code, pkt.ctrl.text));
                    }
                }
            }
        } else if (pkt.meta != null) {
            Topic topic = getTopic(pkt.meta.topic);
            if (topic != null) {
                topic.routeMeta(pkt.meta);
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
                Log.d(TAG, "Routing pres to " + topic.getName());
                topic.routePres(pkt.pres);
                // For P2P topics presence is addressed to 'me' only. Forward it to the actual topic, if it's found.
                if (TOPIC_ME.equals(pkt.pres.topic) && Topic.getTopicTypeByName(pkt.pres.src) == Topic.TopicType.P2P) {
                    Topic forwardTo = getTopic(pkt.pres.src);
                    if (forwardTo != null) {
                        Log.d(TAG, "Also forwarding pres to " + forwardTo.getName());
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

    public static TypeFactory getTypeFactory() {
        return sTypeFactory;
    }

    public static ObjectMapper getJsonMapper() {
        return sJsonMapper;
    }

    // FIXME(gene): this is broken. Figure out how to handle nulls
    public static boolean isNull(Object obj) {
        // Del control character
        return (obj instanceof String) && obj.equals("\u2421");
    }

    /**
     * Assign default types of generic parameters. Needed for packet deserialization.
     *
     * @param typeOfPublic  - type of public values
     * @param typeOfPrivate - type of private values
     * @param typeOfContent - type of content sent in {pub}/{data} messages
     */
    public void setDefaultTypes(JavaType typeOfPublic,
                                JavaType typeOfPrivate, JavaType typeOfContent) {
        mTypeOfDataPacket = sTypeFactory
                .constructParametricType(MsgServerData.class, typeOfContent);
        mTypeOfMetaPacket = sTypeFactory
                .constructParametricType(MsgServerMeta.class, typeOfPublic, typeOfPrivate);
    }

    /**
     * Assign default types of generic parameters. Needed for packet deserialization.
     *
     * @param typeOfPublic  - type of public values
     * @param typeOfPrivate - type of private values
     * @param typeOfContent - type of content sent in {pub}/{data} messages
     */
    public void setDefaultTypes(Class<?> typeOfPublic,
                                Class<?> typeOfPrivate, Class<?> typeOfContent) {
        setDefaultTypes(sTypeFactory.constructType(typeOfPublic),
                sTypeFactory.constructType(typeOfPrivate),
                sTypeFactory.constructType(typeOfContent));
    }

    protected JavaType getTypeOfDataPacket() {
        return mTypeOfDataPacket;
    }

    protected JavaType getTypeOfDataPacket(String topicName) {
        Topic topic = getTopic(topicName);
        JavaType result = (topic != null) ? topic.getTypeOfDataPacket() : null;
        return result != null ? result : mTypeOfDataPacket;
    }

    protected JavaType getTypeOfMetaPacket() {
        return mTypeOfMetaPacket;
    }

    protected JavaType getTypeOfMetaPacket(String topicName) {
        Topic topic = getTopic(topicName);
        JavaType result = (topic != null) ? topic.getTypeOfMetaPacket() : null;
        return result != null ? result : mTypeOfMetaPacket;
    }

    protected String makeUserAgent() {
        return mAppName + " (Android " + System.getProperty("os.version") + "; "
                + Locale.getDefault().toString() + ") " + LIBRARY;
    }

    /**
     * Set device token for push notifications
     * @param token
     */
    public void setDeviceToken(String token) {
        mDeviceToken = token;
    }

    /**
     * Set device langauge
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
    public PromisedReply<ServerMessage> hello() throws Exception {
        ClientMessage msg = new ClientMessage(new MsgClientHi(getNextId(), VERSION,
                makeUserAgent(), mDeviceToken, mLanguage));
        try {
            PromisedReply<ServerMessage> future = null;
            if (msg.hi.id != null) {
                future = new PromisedReply<ServerMessage>();
                mFutures.put(msg.hi.id, future);
                future = future.thenApply(
                        new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                                if (pkt.ctrl == null) {
                                    throw new InvalidObjectException("Unexpected type of reply packet to hello");
                                }
                                mServerVersion = (String) pkt.ctrl.params.get("ver");
                                mServerBuild = (String) pkt.ctrl.params.get("build");
                                return null;
                            }
                        }, null);
            }
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            return future;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Create new account. Connection must be established prior to calling this method.
     *
     * @param scheme authentication scheme to use
     * @param secret authentication secret for the chosen scheme
     * @param loginNow use the new account to login immediately
     * @param desc default access parameters for this account
     * @return PromisedReply of the reply ctrl message
     * @throws Exception if there is no connection
     */
    protected <Pu,Pr,T> PromisedReply<ServerMessage> createAccount(String scheme, String secret,
                                                         boolean loginNow,
                                                         MetaSetDesc<Pu,Pr> desc) throws Exception {
        ClientMessage msg = new ClientMessage<Pu,Pr,T>(
                new MsgClientAcc<>(getNextId(), scheme, secret, loginNow, desc));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.acc.id, future);
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
     * @return PromisedReply of the reply ctrl message
     * @throws Exception if there is no connection
     */
    public <Pu,Pr> PromisedReply<ServerMessage> createAccountBasic(
            String uname, String password, boolean login, MetaSetDesc<Pu,Pr> desc)
                throws Exception {
        return createAccount(AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password),
                login, desc);
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
        return login(AuthScheme.LOGIN_BASIC, AuthScheme.encodeBasicToken(uname, password));
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
        return login(AuthScheme.LOGIN_TOKEN, token);
    }

    protected PromisedReply<ServerMessage> login(String combined) throws Exception {
        AuthScheme auth = AuthScheme.parse(combined);
        if (auth != null) {
            return login(auth.scheme, auth.secret);
        }
        throw new IllegalArgumentException();
    }

    protected PromisedReply<ServerMessage> login(String scheme, String secret) throws Exception {
        if (mAutologin) {
            mLoginCredentials = new LoginCredentials(scheme, secret);
        }

        if (isAuthenticated()) {
            // Don't try to login again if we are logged in.
            Log.d(TAG, "Already authenticated");
            return new PromisedReply<>((ServerMessage) null);
        }

        ClientMessage msg = new ClientMessage(new MsgClientLogin(getNextId(), scheme, secret));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = null;
            if (msg.login.id != null) {
                future = new PromisedReply<>();
                mFutures.put(msg.login.id, future);
                future = future.thenApply(
                        new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                                if (pkt.ctrl == null) {
                                    throw new InvalidObjectException("Unexpected type of reply packet in login");
                                }
                                mMyUid = (String) pkt.ctrl.params.get("uid");
                                if (mStore != null) {
                                    mStore.setMyUid(mMyUid);
                                }
                                // If topics were not loaded earlier, load them now.
                                loadTopics();
                                mAuthToken = (String) pkt.ctrl.params.get("token");
                                mAuthTokenExpires = sDateFormat.parse((String) pkt.ctrl.params.get("expires"));
                                mConnAuth = true;
                                if (mListener != null) {
                                    mListener.onLogin(pkt.ctrl.code, pkt.ctrl.text);
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

    public void setAutologin(boolean state) {
        mAutologin = state;
    }

    /**
     * Low-level subscription request. The subsequent messages on this topic will not
     * be automatically dispatched. A {@link Topic#subscribe()} should be normally used instead.
     *
     * @param topicName name of the topic to subscribe to
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu,Pr,T> PromisedReply<ServerMessage> subscribe(String topicName,
                                                              MsgSetMeta<Pu,Pr,T> set,
                                                              MsgGetMeta get) {
        ClientMessage msg = new ClientMessage(new MsgClientSub<>(getNextId(), topicName, set, get));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.sub.id, future);
            return future;
        } catch (JsonProcessingException e) {
            Log.i(TAG, "Failed to serialize message", e);
            return null;
        }
    }

    /**
     * Low-level request to unsubscribe topic. A {@link Topic#leave(boolean)} should be normally
     * used instead.
     *
     * @param topicName name of the topic to subscribe to
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> leave(String topicName, boolean unsub) {
        ClientMessage msg = new ClientMessage(new MsgClientLeave(getNextId(), topicName, unsub));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.leave.id, future);
            return future;
        } catch (JsonProcessingException e) {
            return null;
        }
    }


    /**
     * Low-level request to publish data. A {@link Topic#publish} should be normally
     * used instead.
     *
     * @param topicName name of the topic to publish to
     * @param data      payload to publish to topic
     * @return PromisedReply of the reply ctrl message
     */
    @SuppressWarnings("unchecked")
    public PromisedReply<ServerMessage> publish(String topicName, Object data) {
        ClientMessage msg = new ClientMessage(new MsgClientPub<>(getNextId(), topicName, true, data));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.pub.id, future);
            return future;
        } catch (Exception e) {
            return null;
        }
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
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.get.id, future);
            return future;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Low-level request to update topic metadata. A {@link Topic#setMeta} should be normally
     * used instead.
     *
     * @param topicName name of the topic to publish to
     * @param meta metadata to assign
     * @return PromisedReply of the reply ctrl or meta message
     */
    public <Pu,Pr,T> PromisedReply<ServerMessage> setMeta(final String topicName,
                                                            final MsgSetMeta<Pu,Pr,T> meta) {
        ClientMessage msg = new ClientMessage(new MsgClientSet<>(getNextId(), topicName, meta));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.set.id, future);
            return future;
        } catch (Exception e) {
            return null;
        }
    }

    private PromisedReply<ServerMessage> sendDeleteMessage(ClientMessage msg) {
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.del.id, future);
            return future;
        } catch (Exception e) {
            return null;
        }

    }

    /**
     * Low-level request to delete messages from a topic. Use {@link Topic#delMessages(int, boolean)} instead.
     *
     * @param topicName name of the topic to inform
     * @param before delete all messages with ids below this
     * @return PromisedReply of the reply ctrl or meta message
     */
    public PromisedReply<ServerMessage> delMessage(final String topicName, final int before, final boolean hard) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId(), topicName,
                MsgClientDel.What.MSG, before, hard));
        return sendDeleteMessage(msg);
    }

    /**
     * Low-level request to delete messages from a topic. Use {@link Topic#delMessages(int, boolean)} instead.
     *
     * @param topicName name of the topic to inform
     * @param list delete all messages with ids in this list
     * @return PromisedReply of the reply ctrl or meta message
     */
    public PromisedReply<ServerMessage> delMessage(final String topicName, final int[] list, final boolean hard) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId(), topicName, list, hard));
        return sendDeleteMessage(msg);
    }

    /**
     * Low-level request to delete topic. Use {@link Topic#delete()} instead.
     *
     * @param topicName name of the topic to inform
     * @return PromisedReply of the reply ctrl or meta message
     */
    public PromisedReply<ServerMessage> delTopic(final String topicName) {
        ClientMessage msg = new ClientMessage(new MsgClientDel(getNextId(), topicName,
                MsgClientDel.What.TOPIC, 0));
        try {
            send(Tinode.getJsonMapper().writeValueAsString(msg));
            PromisedReply<ServerMessage> future = new PromisedReply<>();
            mFutures.put(msg.del.id, future);
            return future;
        } catch (Exception unused) {
            return null;
        }
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
    protected void note(String topicName, String what, int seq) {
        try {
            send(Tinode.getJsonMapper().writeValueAsString(new
                    ClientMessage(new MsgClientNote(topicName, what, seq))));
        } catch (JsonProcessingException ignored) {
        }
    }

    /**
     * Send typing notification to all other topic subscribers.
     * This method does not return a PromisedReply because the server does not acknowledge {note} packets.
     *
     * @param topicName name of the topic to inform
     */
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
    public void noteRecv(String topicName, int seq) {
        note(topicName, NOTE_RECV, seq);
    }

    /**
     * Writes a string to websocket.
     *
     * @param message string to write to websocket
     */
    protected void send(String message) {
        Log.d(TAG, "out: " + message);
        if (mConnection == null || !mConnection.isConnected()) {
            throw new NotConnectedException("No connection");
        }
        mConnection.send(message);
    }

    /**
     * Create new topic by name.
     * @param name name of the topic to create
     * @param l event listener; could be null
     * @return topic of appropriate class
     */
    @SuppressWarnings("unchecked")
    public Topic newTopic(String name, Topic.Listener l) {
        if (TOPIC_ME.equals(name)) {
            return new MeTopic(this, l);
        }
        return new Topic(this, name, l);
    }

    /**
     * Create an instance of a new unsubscribed group topic (TOPIC_NEW).
     * @param l event listener; could be null
     * @return topic of appropriate class
     */
    @SuppressWarnings("unchecked")
    public Topic newGroupTopic(Topic.Listener l) {
        return newTopic(TOPIC_NEW, l);
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
     * Obtain an existing topic by name
     *
     * @return a {@link Collection} of topics
     */
    public List<Topic> getTopics() {
        return new ArrayList<>(mTopics.values());
    }

    /**
     * Return a collection of topics which satisfy the filters.
     *
     * @param type type of topics to return.
     * @param updated return topics with update timestamp after this
     */
    public List<Topic> getFilteredTopics(Topic.TopicType type, Date updated) {
        if (type == Topic.TopicType.ANY && updated == null) {
            return getTopics();
        }
        if (type == Topic.TopicType.UNKNOWN) {
            return null;
        }
        ArrayList<Topic> result = new ArrayList<>();
        int typeVal = type.val();
        for (Topic t : mTopics.values()) {
            if (((t.getTopicType().val() & typeVal) != 0) &&
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
    public <Pu,Pr,T> Topic<Pu,Pr,T> getTopic(String name) {
        if (name == null) {
            return null;
        }
        return mTopics.get(name);
    }

    /**
     * Start tracking topic.
     */
    protected void registerTopic(Topic topic) {
        if (mStore != null) {
            if (!topic.isPersisted()) {
                mStore.topicAdd(topic);
            }
            topic.setStorage(mStore);
        }
        mTopics.put(topic.getName(), topic);
    }

    /**
     * Stop tracking the topic.
     */
    protected void unregisterTopic(Topic topic) {
        mTopics.remove(topic.getName());
        if (mStore != null) {
            topic.setStorage(null);
            mStore.topicDelete(topic);
        }
    }

    /**
     * Parse JSON received from the server into {@link ServerMessage}
     *
     * @param jsonMessage message to parse
     * @return ServerMessage or null
     */
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
                        if (node.has("topic")) {
                            msg.data = mapper.readValue(node.traverse(),
                                    getTypeOfDataPacket(node.get("topic").asText()));
                        }
                        break;
                    case "meta":
                        if (node.has("topic")) {
                            msg.meta = mapper.readValue(node.traverse(),
                                    getTypeOfMetaPacket(node.get("topic").asText()));
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
    protected static long getKeyPressDelay() {
        return NOTE_KP_DELAY;
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
        @SuppressWarnings("unused")
        public void onConnect(int code, String reason, Map<String, Object> params) {
        }

        /**
         * Connection was dropped
         *
         * @param byServer true if connection was closed by server
         * @param code     numeric code of the error which caused connection to drop
         * @param reason   error message
         */
        @SuppressWarnings("unused")
        public void onDisconnect(boolean byServer, int code, String reason) {
        }

        /**
         * Result of successful or unsuccessful {@link #login} attempt.
         *
         * @param code a numeric value between 200 and 2999 on success, 400 or higher on failure
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
        @SuppressWarnings("unused")
        public void onMessage(ServerMessage<?, ?, ?> msg) {
        }

        /**
         * Handle unparsed message. Default handler calls {@code #dispatchPacket(...)} on a
         * websocket thread.
         * A subclassed listener may wish to call {@code dispatchPacket()} on a UI thread
         *
         * @param msg message to be processed
         */
        @SuppressWarnings("unused")
        public void onRawMessage(String msg) {
        }

        /**
         * Handle control message
         *
         * @param ctrl control message to process
         */
        @SuppressWarnings("unused")
        public void onCtrlMessage(MsgServerCtrl ctrl) {
        }

        /**
         * Handle data message
         *
         * @param data control message to process
         */
        @SuppressWarnings("unused")
        public void onDataMessage(MsgServerData<?> data) {
        }

        /**
         * Handle info message
         *
         * @param info info message to process
         */
        @SuppressWarnings("unused")
        public void onInfoMessage(MsgServerInfo info) {
        }

        /**
         * Handle meta message
         *
         * @param meta meta message to process
         */
        @SuppressWarnings("unused")
        public void onMetaMessage(MsgServerMeta<?, ?> meta) {
        }

        /**
         * Handle presence message
         *
         * @param pres control message to process
         */
        @SuppressWarnings("unused")
        public void onPresMessage(MsgServerPres pres) {
        }
    }

    static class LoginCredentials {
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