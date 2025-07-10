package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.ClientMessage;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.MsgClientAcc;
import co.tinode.tinodesdk.model.MsgClientDel;
import co.tinode.tinodesdk.model.MsgClientExtra;
import co.tinode.tinodesdk.model.MsgClientGet;
import co.tinode.tinodesdk.model.MsgClientHi;
import co.tinode.tinodesdk.model.MsgClientLeave;
import co.tinode.tinodesdk.model.MsgClientLogin;
import co.tinode.tinodesdk.model.MsgClientNote;
import co.tinode.tinodesdk.model.MsgClientPub;
import co.tinode.tinodesdk.model.MsgClientSet;
import co.tinode.tinodesdk.model.MsgClientSetSerializer;
import co.tinode.tinodesdk.model.MsgClientSub;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgRange;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.MsgSetMetaSerializer;
import co.tinode.tinodesdk.model.Pair;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

@SuppressWarnings("WeakerAccess")
public class Tinode {
    private static final String TAG = "Tinode";

    private static final String PROTOVERSION = "0";
    private static final String VERSION = "0.24";
    private static final String LIBRARY = "tindroid/" + BuildConfig.VERSION_NAME;

    public static final String USER_NEW = "new";
    public static final String TOPIC_NEW = "new";
    public static final String CHANNEL_NEW = "nch";
    public static final String TOPIC_ME = "me";
    public static final String TOPIC_FND = "fnd";
    public static final String TOPIC_SYS = "sys";
    public static final String TOPIC_SLF = "slf";

    public static final String TOPIC_GRP_PREFIX = "grp";
    public static final String TOPIC_CHN_PREFIX = "chn";
    public static final String TOPIC_USR_PREFIX = "usr";

    // Names of server-provided numeric limits and parameters.
    public static final String MAX_MESSAGE_SIZE = "maxMessageSize";
    public static final String MAX_SUBSCRIBER_COUNT = "maxSubscriberCount";
    public static final String MAX_TAG_LENGTH = "maxTagLength";
    public static final String MIN_TAG_LENGTH = "minTagLength";
    public static final String MAX_TAG_COUNT = "maxTagCount";
    public static final String MAX_FILE_UPLOAD_SIZE = "maxFileUploadSize";
    public static final String MSG_DELETE_AGE = "msgDelAge";

    private static final String[] SERVER_LIMITS = new String[]{
            MAX_MESSAGE_SIZE, MAX_SUBSCRIBER_COUNT, MAX_TAG_LENGTH, MIN_TAG_LENGTH,
            MAX_TAG_COUNT, MAX_FILE_UPLOAD_SIZE};

    private static final String UPLOAD_PATH = "/file/u/";

    // Value interpreted as 'content deleted', unicode 0x2421.
    public static final String NULL_VALUE = "␡";

    // Notifications {note}.
    protected static final String NOTE_CALL = "call";
    protected static final String NOTE_KP = "kp";
    protected static final String NOTE_REC_AUDIO = "kpa";
    protected static final String NOTE_REC_VIDEO = "kpv";
    protected static final String NOTE_READ = "read";
    protected static final String NOTE_RECV = "recv";

    // Audio call is audio-only.
    public static final String CALL_AUDIO_ONLY = "aonly";

    // Delay in milliseconds between sending two key press notifications on the
    // same topic.
    private static final long NOTE_KP_DELAY = 3000L;

    // Reject unresolved futures after this many milliseconds.
    private static final long EXPIRE_FUTURES_TIMEOUT = 5_000L;
    // Periodicity of garbage collection of unresolved futures.
    private static final long EXPIRE_FUTURES_PERIOD = 1_000L;

    private static final ObjectMapper sJsonMapper;
    protected static final TypeFactory sTypeFactory;
    protected static final SimpleDateFormat sDateFormat;

    protected static final int MAX_PINNED_COUNT = 5;

    static final int DEFAULT_MESSAGE_PAGE = 24;

    public static final String TAG_EMAIL = "email:";
    public static final String TAG_PHONE = "tel:";
    public static final String TAG_ALIAS = "alias:";
    private static final Pattern ALIAS_REGEX = Pattern.compile("^[a-z0-9_\\-]{4,24}$", Pattern.CASE_INSENSITIVE);

    private static final int DEFAULT_MAX_TAG_COUNT = 16;
    private static final int DEFAULT_MAX_TAG_LENGTH = 96;
    private static final int DEFAULT_MIN_TAG_LENGTH = 2;

    static {
        sJsonMapper = new ObjectMapper();
        // Silently ignore unknown properties
        sJsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // Skip null fields from serialization
        sJsonMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        // Add handler for various deserialization problems
        sJsonMapper.addHandler(new NullingDeserializationProblemHandler());

        // (De)Serialize dates as RFC3339. The default does not cut it because
        // it represents the time zone as '+0000' instead of the expected 'Z' and
        // SimpleDateFormat cannot handle *optional* milliseconds.
        // Java 7 date parsing is retarded. Format: 2016-09-07T17:29:49.100Z
        sJsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        sDateFormat = new RFC3339Format();
        sJsonMapper.setDateFormat(sDateFormat);

        // Add custom serializer for MsgSetMeta.
        SimpleModule module = new SimpleModule();
        module.addSerializer(new MsgSetMetaSerializer());
        module.addSerializer(new MsgClientSetSerializer());
        sJsonMapper.registerModule(module);

        sTypeFactory = sJsonMapper.getTypeFactory();
    }

    // Object for connect-disconnect synchronization.
    private final Object mConnLock = new Object();
    private final HashMap<Topic.TopicType, JavaType> mTypeOfMetaPacket;
    private final Storage mStore;
    private final String mApiKey;
    private final String mAppName;
    private final ListenerNotifier mNotifier;
    private final ConcurrentMap<String, FutureHolder> mFutures;
    private final ConcurrentHashMap<String, Pair<Topic, Storage.Message>> mTopics;
    private final ConcurrentHashMap<String, User> mUsers;

    private JavaType mDefaultTypeOfMetaPacket = null;
    private URI mServerURI = null;
    private String mServerVersion = null;
    private String mServerBuild = null;
    private String mDeviceToken = null;
    private String mLanguage = null;
    private String mOsVersion;
    // Counter for the active background connections.
    private int mBkgConnCounter = 0;
    // Indicator of active foreground connection.
    private boolean mFgConnection = false;
    // Connector object.
    private Connection mConnection = null;
    // Listener of connection events.
    private ConnectedWsListener mConnectionListener = null;
    // True is connection is authenticated
    private boolean mConnAuth = false;
    // True if Tinode should use mLoginCredentials to automatically log in after connecting.
    private boolean mAutologin = false;
    private LoginCredentials mLoginCredentials = null;
    // Server provided list of credential methods to validate e.g. ["email", "tel", ...].
    private List<String> mCredToValidate = null;
    private String mMyUid = null;
    private String mAuthToken = null;
    private Date mAuthTokenExpires = null;
    private int mMsgId = 0;
    private transient int mNameCounter = 0;
    private boolean mTopicsLoaded = false;
    // Timestamp of the latest topic desc update.
    private Date mTopicsUpdated = null;
    // The difference between server time and local time.
    private long mTimeAdjustment = 0;
    // Indicator that login is in progress
    private Boolean mLoginInProgress = false;

    private Map<String, Object> mServerParams = null;

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
                for (Map.Entry<String, FutureHolder> entry : mFutures.entrySet()) {
                    FutureHolder fh = entry.getValue();
                    if (fh.timestamp.before(expiration)) {
                        mFutures.remove(entry.getKey());
                        try {
                            fh.future.reject(new ServerResponseException(504, "timeout id=" + entry.getKey()));
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

    /**
     * Compares object to a string which signifies "null" to the server.
     */
    public static boolean isNull(Object obj) {
        // Del control character
        return (obj instanceof String) && obj.equals(NULL_VALUE);
    }

    /**
     * Parse comma separated list of possible quoted strings into an array.
     */
    public String[] parseTags(final String tagList) {
        if (tagList == null || tagList.isEmpty()) {
            return null;
        }

        ArrayList<String> tags = new ArrayList<>();
        int start = 0;
        final long maxTagCount = getServerLimit(Tinode.MAX_TAG_COUNT, DEFAULT_MAX_TAG_COUNT);
        final long maxTagLength = getServerLimit(Tinode.MAX_TAG_LENGTH, DEFAULT_MAX_TAG_LENGTH);
        final long minTagLength = getServerLimit(Tinode.MIN_TAG_LENGTH, DEFAULT_MIN_TAG_LENGTH);

        final int length = tagList.length();
        boolean quoted = false;
        for (int idx = 0; idx < length && tags.size() < maxTagCount; idx++) {
            if (tagList.charAt(idx) == '\"') {
                // Toggle 'inside of quotes' state.
                quoted = !quoted;
            }

            String tag;
            if (tagList.charAt(idx) == ',' && !quoted) {
                tag = tagList.substring(start, idx);
                start = idx + 1;
            } else if (idx == length - 1) {
                // Last char
                tag = tagList.substring(start);
            } else {
                continue;
            }

            tag = tag.trim();
            // Remove possible quotes.
            if (tag.length() > 1 && tag.charAt(0) == '\"' && tag.charAt(tag.length() - 1) == '\"') {
                tag = tag.substring(1, tag.length() - 1).trim();
            }
            if (tag.length() >= minTagLength && tag.length() <= maxTagLength) {
                tags.add(tag);
            }
        }

        if (tags.isEmpty()) {
            return null;
        }

        return tags.toArray(new String[]{});
    }

    // Split fully-qualified tag into prefix and value.
    public static Pair<String,String> tagSplit(@Nullable String tag) {
        if (tag == null) {
            return null;
        }

        tag = tag.trim();
        int splitAt = tag.indexOf(':');
        if (splitAt <= 0) {
            // Invalid syntax.
            return null;
        }

        String value = tag.substring(splitAt + 1);
        if (value.isEmpty()) {
            return null;
        }
        return new Pair<>(tag.substring(0, splitAt), value);
    }

    /**
     * Set a unique namespace tag.
     * If the tag with this namespace is already present then it's replaced with the new tag.
     * @param uniqueTag tag to add, must be fully-qualified; if null or empty, no action is taken.
     */
    public static String[] setUniqueTag(String[] tags, @NotNull String uniqueTag) {
        if (tags == null || tags.length == 0) {
            // No tags, just add the new one.
            return new String[]{uniqueTag};
        }

        Pair<String, String> parts = Tinode.tagSplit(uniqueTag);
        if (parts == null) {
            // Invalid tag.
            return null;
        }

        // Remove the old tag with the same prefix.
        Stream<String> tt = Arrays.stream(tags)
                .filter(tag -> (tag != null && !tag.startsWith(parts.first)));
        // Add the new tag and convert to array.
        return Stream.concat(tt, Stream.of(uniqueTag)).toArray(String[]::new);
    }

    /**
     * Remove a unique tag with the given prefix.
     * @param prefix prefix to remove
     */
    public static String[] clearTagPrefix(String[] tags, @NotNull String prefix) {
        if (tags == null || tags.length == 0) {
            return null;
        }
        return Arrays.stream(tags).filter(tag -> (tag != null && !tag.startsWith(prefix))).toArray(String[]::new);
    }

    /**
     * Check if the given tag value is syntactically valid.
     * @param tag tag value to check.
     * @return true if the tag value is valid, false otherwise.
     */
    public static boolean isValidTagValueFormat(String tag) {
        if (tag == null || tag.isEmpty()) {
            return true;
        }

        Matcher matcher = ALIAS_REGEX.matcher(tag);
        return matcher.matches();
    }

    /**
     * Find the first tag with the given prefix.
     * @param prefix prefix to search for.
     * @return tag if found or null.
     */
    @Nullable
    public static String tagByPrefix(String[] tags, @NotNull String prefix) {
        if (tags == null) {
            return null;
        }

        for (String tag : tags) {
            if (tag != null && tag.startsWith(prefix)) {
                return tag;
            }
        }
        return null;
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
        } catch (Error | Exception e) {
            Log.w(TAG, "Failed to deserialize saved '" + input +
                    "' into '" + canonicalName + "'", e);
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
     * Headers for a reply message.
     *
     * @param seq message ID being replied to.
     * @return headers as map "key : value"
     */
    public static Map<String, Object> headersForReply(final int seq) {
        Map<String, Object> head = new HashMap<>();
        head.put("reply", Integer.toString(seq));
        return head;
    }

    /**
     * Headers for a replacement message.
     *
     * @param seq message ID being replaced.
     * @return headers as map "key : value"
     */
    public static Map<String, Object> headersForReplacement(final int seq) {
        Map<String, Object> head = new HashMap<>();
        head.put("replace", ":" + seq);
        return head;
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

    private <ML extends Iterator<Storage.Message> & Closeable> void loadTopics() {
        if (mStore != null && mStore.isReady() && !mTopicsLoaded) {
            Topic[] topics = mStore.topicGetAll(this);
            if (topics != null) {
                for (Topic tt : topics) {
                    tt.setStorage(mStore);
                    mTopics.put(tt.getName(), new Pair<>(tt, null));
                    setTopicsUpdated(tt.getUpdated());
                }
            }
            // Load last message for each topic.
            ML latest = mStore.getLatestMessagePreviews();
            if (latest != null) {
                while (latest.hasNext()) {
                    Storage.Message msg = latest.next();
                    String topic = msg.getTopic();
                    if (topic != null) {
                        Pair<Topic, Storage.Message> pair = mTopics.get(topic);
                        if (pair != null) {
                            pair.second = msg;
                        }
                    }
                }

                try {
                    latest.close();
                } catch (IOException ignored) {}
            }
            mTopicsLoaded = true;
        }
    }

    /**
     * Open a websocket connection to the server, process handshake exchange then optionally login.
     *
     * @param serverURI     address of the server to connect to.
     * @param background    this is a background connection: the server will delay user's online announcement for 5 sec.
     * @return PromisedReply to be resolved or rejected when the connection is completed.
     */
    protected PromisedReply<ServerMessage> connect(@Nullable URI serverURI, boolean background) {
        synchronized (mConnLock) {
            if (serverURI == null && mServerURI == null) {
                return new PromisedReply<>(new IllegalArgumentException("No host to connect to"));
            }

            boolean sameHost = serverURI != null && serverURI.equals(mServerURI);
            // Connection already exists and connected.
            if (mConnection != null) {
                if (mConnection.isConnected() && sameHost) {
                    if (background) {
                        mBkgConnCounter ++;
                    } else {
                        mFgConnection = true;
                    }
                    // If the connection is live and the server address has not changed, return a resolved promise.
                    return new PromisedReply<>((ServerMessage) null);
                }

                if (!sameHost) {
                    // Clear auto-login because saved credentials won't work with the new server.
                    setAutoLogin(null, null);
                    // Stop exponential backoff timer if it's running.
                    mConnection.disconnect();
                    mConnection = null;
                    mBkgConnCounter = 0;
                    mFgConnection = false;
                }
            }

            mMsgId = 0xFFFF + (int) (Math.random() * 0xFFFF);
            mServerURI = serverURI;

            PromisedReply<ServerMessage> completion = new PromisedReply<>();

            if (mConnectionListener == null) {
                mConnectionListener = new ConnectedWsListener();
            }
            mConnectionListener.addPromise(completion);

            if (mConnection == null) {
                mConnection = new Connection(mServerURI, mApiKey, mConnectionListener);
            }
            mConnection.connect(true, background);

            return completion;
        }
    }

    /**
     * Open a websocket connection to the server, process handshake exchange then optionally login.
     *
     * @param hostName address of the server to connect to; if hostName is null a saved address will be used.
     * @param tls      use transport layer security (wss); ignored if hostName is null.
     * @return PromisedReply to be resolved or rejected when the connection is completed.
     */
    public PromisedReply<ServerMessage> connect(@Nullable String hostName, boolean tls, boolean background) {
        URI connectTo = mServerURI;
        if (hostName != null) {
            try {
                connectTo = createWebsocketURI(hostName, tls);
            } catch (URISyntaxException ex) {
                return new PromisedReply<>(ex);
            }
        }
        if (connectTo == null && mStore != null) {
            String savedUri = mStore.getServerURI();
            if (savedUri != null) {
                connectTo = URI.create(mStore.getServerURI());
            }
        }
        return connect(connectTo, background);
    }

    /**
     * Make sure connection is either already established or being established:
     * - If connection is already established do nothing
     * - If connection does not exist, create
     * - If not connected and waiting for backoff timer, wake it up.
     *
     * @param interactive set to true if user directly requested a reconnect.
     * @param reset       if true drop connection and reconnect; happens when cluster is reconfigured.
     */
    public void reconnectNow(boolean interactive, boolean reset, boolean background) {
        synchronized (mConnLock) {
            if (mConnection == null) {
                // New connection using saved parameters.
                connect(null, false, background);
                return;
            }

            if (mConnection.isConnected()) {
                if (!reset) {
                    // If the connection is live and reset is not requested, all is fine.
                    return;
                }
                // Forcing a new connection.
                mConnection.disconnect();
                mBkgConnCounter = 0;
                mFgConnection = false;
                interactive = true;
            }

            // Connection exists but not connected. Try to connect immediately only if requested or if
            // autoreconnect is not enabled.
            if (interactive || !mConnection.isWaitingToReconnect()) {
                mConnection.connect(true, background);
            }
        }
    }

    // Mark connection as foreground-connected.
    private void pinConnectionToFg() {
        synchronized (mConnLock) {
            mFgConnection = true;
        }
    }

    /**
     * Decrement connection counters and disconnect from server if counters permit.
     *
     * @param fromBkg request to disconnect background connection.
     */
    public void maybeDisconnect(boolean fromBkg) {
        synchronized (mConnLock) {
            if (fromBkg) {
                mBkgConnCounter--;
                if (mBkgConnCounter < 0) {
                    mBkgConnCounter = 0;
                }
            } else {
                mFgConnection = false;
                setAutoLogin(null, null);
            }

            if (mBkgConnCounter > 0 || mFgConnection) {
                return;
            }

            mConnAuth = false;
            if (mConnection != null) {
                mConnection.disconnect();
            }
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
     * Get configured server address as an HTTP(S) URL.
     *
     * @return Server URL.
     * @throws MalformedURLException thrown if server address is not yet configured.
     */
    public @NotNull URL getBaseUrl() throws MalformedURLException {
        String base = getHttpOrigin();
        if (base == null) {
            throw new MalformedURLException("server URL not configured");
        }
        return new URL(base + "/v" + PROTOVERSION + "/");
    }

    /**
     * Get server address suitable for use as an Origin: header for CORS compliance.
     *
     * @return server internet address
     */
    public @Nullable String getHttpOrigin() {
        if (mServerURI == null) {
            return null;
        }

        boolean tls = mServerURI.getScheme().equals("wss");
        try {
            return new URL(tls ? "https" : "http", mServerURI.getHost(), mServerURI.getPort(), "").toString();
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private static URI createWebsocketURI(@NotNull String hostName, boolean tls) throws URISyntaxException {
        return new URI((tls ? "wss://" : "ws://") + hostName + "/v" + PROTOVERSION + "/");
    }

    private void handleDisconnect(boolean byServer, int code, String reason) {
        Log.d(TAG, "Disconnected for '" + reason + "' (code: " + code + ", remote: " +
                byServer + ");");

        mConnAuth = false;

        mServerBuild = null;
        mServerVersion = null;

        mFgConnection = false;
        mBkgConnCounter = 0;

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
        for (Pair<Topic, ?> pair : mTopics.values()) {
            pair.first.topicLeft(false, 503, "disconnected");
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
        if (message == null || message.isEmpty())
            return;

        Log.d(TAG, "in: " + message);

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
            FutureHolder fh = mFutures.remove(pkt.meta.id);
            if (fh != null) {
                fh.future.resolve(pkt);
            }

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
        } else if (pkt.data != null) {
            Topic topic = getTopic(pkt.data.topic);
            if (topic != null) {
                topic.routeData(pkt.data);
            }

            mNotifier.onDataMessage(pkt.data);
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

        // Unknown message type is silently ignored.
    }

    /**
     * Out of band notification handling. Called externally by the FCM push service.
     * Must not be called on the UI thread.
     *
     * @param data FCM payload.
     * @param authToken authentication token to use in case login is needed.
     * @param keepConnection if <code>true</code> do not terminate new connection.
     */
    public void oobNotification(Map<String, String> data, String authToken, boolean keepConnection) {
        // This log entry is permanent, not just temporary for debugging.
        Log.d(TAG, "oob: " + data);

        String what = data.get("what");
        String topicName = data.get("topic");
        Integer seq = null;
        try {
            // noinspection ConstantConditions: null value is acceptable here.
            seq = Integer.parseInt(data.get("seq"));
        } catch (NumberFormatException ignored) {}

        Topic topic = getTopic(topicName);
        // noinspection ConstantConditions
        switch (what) {
            case "msg":
                // Check and maybe download new messages right away.
                if (seq == null) {
                    break;
                }

                if (topic != null && topic.isAttached()) {
                    // No need to fetch: topic is already subscribed and got data through normal channel.
                    // Assuming that data was available.
                    break;
                }

                Topic.MetaGetBuilder builder;
                if (topic == null) {
                    // New topic. Create it.
                    topic = newTopic(topicName, null);
                    builder = topic.getMetaGetBuilder().withDesc().withSub();
                } else {
                    // Existing topic.
                    builder = topic.getMetaGetBuilder();
                }

                if (topic.getSeq() < seq) {
                    if (!syncLogin(authToken)) {
                        // Failed to connect or login.
                        break;
                    }

                    String senderId = data.get("xfrom");
                    if (senderId != null && getUser(senderId) == null) {
                        // If sender is not found, try to fetch description from the server.
                        // OK to send without subscription.
                        getMeta(senderId, MsgGetMeta.desc());
                    }

                    // Check again if topic has attached while we tried to connect. It does not guarantee that there
                    // is no race condition to subscribe.
                    if (!topic.isAttached()) {
                        try {
                            // noinspection unchecked
                            topic.subscribe(null, builder.withLaterDel(DEFAULT_MESSAGE_PAGE).build()).getResult();
                            // Wait for the messages to download.
                            topic.getMeta(builder.reset().withLaterData(DEFAULT_MESSAGE_PAGE).build()).getResult();

                            // Notify the server than the message was received.
                            topic.noteRecv();
                            if (!keepConnection) {
                                // Leave the topic before disconnecting.
                                topic.leave().getResult();
                            }
                        } catch (Exception ignored) {}
                    }

                    if (keepConnection) {
                        pinConnectionToFg();
                    }
                    maybeDisconnect(true);
                }
                break;
            case "read":
                if (seq == null || topic == null) {
                    // Ignore 'read' notifications for an unknown topic or with invalid seq.
                    break;
                }
                if (topic.getRead() < seq) {
                    topic.setRead(seq);
                    if (mStore != null) {
                        mStore.setRead(topic, seq);
                    }
                }
                break;
            case "sub":
                if (topic == null) {
                    if (!syncLogin(authToken)) {
                        // Failed to connect or login.
                        break;
                    }
                    // New topic subscription, fetch topic description.
                    try {
                        getMeta(topicName, MsgGetMeta.desc()).getResult();
                    } catch (Exception ignored) {}

                    String senderId = data.get("xfrom");
                    if (senderId != null && getUser(senderId) == null) {
                        // If sender is not found, try to fetch description from the server.
                        // OK to send without subscription.
                        getMeta(senderId, MsgGetMeta.desc());
                    }

                    if (keepConnection) {
                        pinConnectionToFg();
                    }
                    maybeDisconnect(true);
                }
                break;
            default:
                break;
        }
    }

    // Synchronous (blocking) token login using stored parameters.
    // Returns true if a connection was established, false if failed to connect.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean syncLogin(String authToken) {
        if (mStore == null) {
            return false;
        }

        try {
            URI connectTo = new URI(mStore.getServerURI());
            connect(connectTo, true).getResult();
            loginToken(authToken).getResult();
        } catch (Exception ignored) {
            return false;
        }
        return true;
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
    public boolean isMe(@Nullable String uid) {
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
     * Get server-provided limit.
     *
     * @param key          name of the limit.
     * @param defaultValue default value if limit is missing.
     * @return limit or default value.
     */
    public long getServerLimit(@NotNull String key, long defaultValue) {
        Object val = mServerParams != null ? mServerParams.get(key) : null;
        if (val instanceof Long) {
            return (Long) val;
        }
        return defaultValue;
    }

    /**
     * Get generic server-provided named parameter.
     *
     * @param key name of the parameter.
     * @return parameter value or null.
     */
    @Nullable
    public Object getServerParam(@NotNull String key) {
        return mServerParams != null ? mServerParams.get(key) : null;
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

    /**
     * Assign type of generic Public parameter to 'me' topic. Needed for packet deserialization.
     *
     * @param typeOfDescPublic - type of public values
     */
    public void setMeTypeOfMetaPacket(JavaType typeOfDescPublic) {
        JavaType priv = sTypeFactory.constructType(PrivateType.class);
        mTypeOfMetaPacket.put(Topic.TopicType.ME, sTypeFactory
                .constructParametricType(MsgServerMeta.class, typeOfDescPublic, priv, typeOfDescPublic, priv));
    }

    /**
     * Assign type of generic Public parameter to 'me' topic. Needed for packet deserialization.
     *
     * @param typeOfDescPublic - type of public values
     */
    public void setMeTypeOfMetaPacket(Class<?> typeOfDescPublic) {
        setMeTypeOfMetaPacket(sTypeFactory.constructType(typeOfDescPublic));
    }

    /**
     * Assign type of generic Public parameter of 'fnd' topic results. Needed for packet deserialization.
     *
     * @param typeOfSubPublic - type of subscription (search result) public values
     */
    public void setFndTypeOfMetaPacket(JavaType typeOfSubPublic) {
        mTypeOfMetaPacket.put(Topic.TopicType.FND, sTypeFactory
                .constructParametricType(MsgServerMeta.class,
                        sTypeFactory.constructType(String.class),
                        sTypeFactory.constructType(String.class), typeOfSubPublic,
                        sTypeFactory.constructType(String[].class)));
    }

    /**
     * Assign type of generic Public parameter of 'fnd' topic results. Needed for packet deserialization.
     *
     * @param typeOfSubPublic - type of subscription (search result) public values
     */
    public void setFndTypeOfMetaPacket(Class<?> typeOfSubPublic) {
        setFndTypeOfMetaPacket(sTypeFactory.constructType(typeOfSubPublic));
    }

    /**
     * Obtain previously assigned type of Meta packet.
     *
     * @return type of Meta packet.
     */
    @SuppressWarnings("WeakerAccess")
    protected JavaType getTypeOfMetaPacket(String topicName) {
        JavaType result = mTypeOfMetaPacket.get(Topic.getTopicTypeByName(topicName));
        return result != null ? result : getDefaultTypeOfMetaPacket();
    }

    /**
     * Compose User Agent string to be sent to the server.
     *
     * @return composed User Agent string.
     */
    @SuppressWarnings("WeakerAccess")
    protected String makeUserAgent() {
        return mAppName + " (Android " + mOsVersion + "; "
                + Locale.getDefault() + "); " + LIBRARY;
    }

    /**
     * Get {@link LargeFileHelper} object initialized for use with file uploading.
     *
     * @return LargeFileHelper object.
     */
    public LargeFileHelper getLargeFileHelper() {
        URL url = null;
        try {
            url = new URL(getBaseUrl(), "." + UPLOAD_PATH);
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
                    token, null, null));
            return sendWithPromise(msg, msg.hi.id).thenCatch(new PromisedReply.FailureListener<>() {
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
     * @param background indicator that this session should be treated as a service request,
     *                   i.e. presence notifications will be delayed.
     * @return PromisedReply of the reply ctrl message.
     */
    @SuppressWarnings("WeakerAccess")
    public PromisedReply<ServerMessage> hello(Boolean background) {
        ClientMessage msg = new ClientMessage(new MsgClientHi(getNextId(), VERSION, makeUserAgent(),
                mDeviceToken, mLanguage, background));
        return sendWithPromise(msg, msg.hi.id).thenApply(
                new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                        if (pkt.ctrl == null) {
                            throw new InvalidObjectException("Unexpected type of reply packet to hello");
                        }
                        Map<String, Object> params = pkt.ctrl.params;
                        if (params != null) {
                            mServerVersion = (String) params.get("ver");
                            mServerBuild = (String) params.get("build");
                            mServerParams = new HashMap<>(params);
                            // Convert some parameters to Long values.
                            for (String key : SERVER_LIMITS) {
                                try {
                                    Number val = ((Number) mServerParams.get(key));
                                    if (val != null) {
                                        mServerParams.put(key, val.longValue());
                                    } else {
                                        Log.w(TAG, "Server limit '" + key + "' is missing");
                                    }
                                } catch (ClassCastException ex) {
                                    Log.e(TAG, "Failed to obtain server limit '" + key + "'", ex);
                                }
                            }
                        }
                        return null;
                    }
                });
    }

    /**
     * Create new account. Connection must be established prior to calling this method.
     *
     * @param uid      uid of the user to affect
     * @param tmpScheme auth scheme to use for temporary authentication.
     * @param tmpSecret auth secret to use for temporary authentication.
     * @param scheme   authentication scheme to use
     * @param secret   authentication secret for the chosen scheme
     * @param loginNow use the new account to login immediately
     * @param desc     default access parameters for this account
     * @return PromisedReply of the reply ctrl message
     */
    protected <Pu, Pr> PromisedReply<ServerMessage> account(String uid,
                                                            String tmpScheme, String tmpSecret,
                                                            String scheme, String secret,
                                                            boolean loginNow, String[] tags, MetaSetDesc<Pu, Pr> desc,
                                                            Credential[] cred) {
        ClientMessage msg = new ClientMessage<>(
                new MsgClientAcc<>(getNextId(), uid, scheme, secret, loginNow, desc));
        if (desc != null && desc.attachments != null && desc.attachments.length > 0) {
            msg.extra = new MsgClientExtra(desc.attachments);
        }

        // Assign temp auth.
        msg.acc.setTempAuth(tmpScheme, tmpSecret);

        // Add tags and credentials.
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
            future = future.thenApply(new PromisedReply.SuccessListener<>() {
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
     * @param tags     discovery tags
     * @param desc     account parameters, such as full name etc.
     * @param cred     account credential, such as email or phone
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu, Pr> PromisedReply<ServerMessage> createAccountBasic(
            String uname, String password, boolean login, String[] tags, MetaSetDesc<Pu, Pr> desc, Credential[] cred) {
        return account(USER_NEW, null, null, AuthScheme.LOGIN_BASIC,
                AuthScheme.encodeBasicToken(uname, password),
                login, tags, desc, cred);
    }

    protected PromisedReply<ServerMessage> updateAccountSecret(String uid,
                                                               String tmpScheme, String tmpSecret,
                                                               @SuppressWarnings("SameParameterValue") String scheme,
                                                               String secret) {
        return account(uid, tmpScheme, tmpSecret, scheme, secret, false, null, null, null);
    }

    /**
     * Change user name and password for accounts using Basic auth scheme.
     *
     * @param uid      user ID being updated or null if temporary authentication params are provided.
     * @param uname    new login or null to keep the old login.
     * @param password new password.
     * @return PromisedReply of the reply ctrl message.
     */
    public PromisedReply<ServerMessage> updateAccountBasic(String uid, String uname, String password) {
        return updateAccountSecret(uid, null, null, AuthScheme.LOGIN_BASIC,
                AuthScheme.encodeBasicToken(uname, password));
    }

    /**
     * Change user name and password for accounts using Basic auth scheme with temporary auth params.
     *
     * @param auth scheme:secret pair to use for temporary authentication of this action.
     * @param uname new login or null to keep the old login.
     * @param password new password.
     * @return PromisedReply of the reply ctrl message.
     */
    public PromisedReply<ServerMessage> updateAccountBasic(AuthScheme auth, String uname, String password) {
        return updateAccountSecret(null, auth.scheme(), auth.secret(), AuthScheme.LOGIN_BASIC,
                AuthScheme.encodeBasicToken(uname, password));
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

    /**
     * Reset authentication secret, such as password.
     *
     * @param scheme authentication scheme being reset.
     * @param method validation method to use, such as 'email' or 'tel'.
     * @param value  address to send validation request to using the method above, e.g. 'jdoe@example.com'.
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> requestResetSecret(String scheme, String method, String value) {
        return login(AuthScheme.LOGIN_RESET, AuthScheme.encodeResetSecret(scheme, method, value), null);
    }

    protected PromisedReply<ServerMessage> login(String combined) {
        AuthScheme auth = AuthScheme.parse(combined);
        if (auth != null) {
            return login(auth.scheme(), auth.secret(), null);
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
            mStore.setMyUid(mMyUid, mServerURI.toString());
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
                    mStore.setMyUid(mMyUid, mServerURI.toString());
                    mStore.updateCredentials(mCredToValidate.toArray(new String[]{}));
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
                new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                        mLoginInProgress = false;
                        loginSuccessful(pkt.ctrl);
                        return null;
                    }
                },
                new PromisedReply.FailureListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        mLoginInProgress = false;
                        if (err instanceof ServerResponseException sre) {
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
     * Log out current user.
     */
    public void logout() {
        mMyUid = null;
        mServerParams = null;
        setAutoLoginToken(null);

        if (mStore != null) {
            // Clear token here, because of logout setDeviceToken will not be able to clear it.
            mStore.saveDeviceToken(null);
            mStore.logout();
        }

        // Best effort to clear device token on logout.
        // The app logs out even if the token request has failed.
        setDeviceToken(NULL_VALUE).thenFinally(new PromisedReply.FinalListener() {
            @Override
            public void onFinally() {
                mFgConnection = false;
                mBkgConnCounter = 0;
                maybeDisconnect(false);
            }
        });
    }

    /**
     * Low-level subscription request. The subsequent messages on this topic will not
     * be automatically dispatched. A {@link Topic#subscribe()} should be normally used instead.
     *
     * @param topicName name of the topic to subscribe to
     * @param set       values to be assign to topic on success.
     * @param get       query for topic values.
     * @return PromisedReply of the reply ctrl message
     */
    public <Pu, Pr> PromisedReply<ServerMessage> subscribe(String topicName, MsgSetMeta<Pu, Pr> set, MsgGetMeta get) {
        ClientMessage msg = new ClientMessage(new MsgClientSub<>(getNextId(), topicName, set, get));
        if (set != null && set.desc != null && set.desc.attachments != null) {
            msg.extra = new MsgClientExtra(set.desc.attachments);
        }
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
     * Low-level request to publish data. A {@link Topic#publish} should be normally
     * used instead.
     *
     * @param topicName     name of the topic to publish to
     * @param data          payload to publish to topic
     * @param head          message header
     * @param attachments   URLs of out-of-band attachments contained in the message.
     * @return PromisedReply of the reply ctrl message
     */
    public PromisedReply<ServerMessage> publish(String topicName, Object data, Map<String, Object> head, String[] attachments) {
        ClientMessage msg = new ClientMessage(new MsgClientPub(getNextId(), topicName, true, data, head));
        if (attachments != null && attachments.length > 0) {
            msg.extra = new MsgClientExtra(attachments);
        }
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
    public <Pu, Pr> PromisedReply<ServerMessage> setMeta(final String topicName,
                                                         final MsgSetMeta<Pu, Pr> meta) {
        ClientMessage msg = new ClientMessage(new MsgClientSet<>(getNextId(), topicName, meta));
        if (meta.desc != null && meta.desc.attachments != null && meta.desc.attachments.length > 0) {
            msg.extra = new MsgClientExtra(meta.desc.attachments);
        }
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
     * @param hard      hard-delete topic.
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
     * @param cred credential to delete.
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
        return sendWithPromise(msg, msg.del.id).thenApply(new PromisedReply.SuccessListener<>() {
            @Override
            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                maybeDisconnect(false);
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
     * Send notification to all other topic subscribers that the user is recording a message.
     * This method does not return a PromisedReply because the server does not acknowledge {note} packets.
     *
     * @param topicName name of the topic to inform
     * @param audioOnly if the message is audio-only, false if it's a video message.
     */
    @SuppressWarnings("WeakerAccess")
    public void noteRecording(String topicName, boolean audioOnly) {
        note(topicName, audioOnly ? NOTE_REC_AUDIO : NOTE_REC_VIDEO, 0);
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
     * Send a video call notification to server.
     * @param topicName specifies the call topic.
     * @param seq call message ID.
     * @param event is a video call event to notify the other call party about (e.g. "accept" or "hang-up").
     * @param payload is a JSON payload associated with the event.
     */
    public void videoCall(String topicName, int seq, String event, Object payload) {
        try {
            send(new ClientMessage(new MsgClientNote(topicName, NOTE_CALL, seq, event, payload)));
        } catch (JsonProcessingException | NotConnectedException ignored) {}
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

    /**
     * Takes {@link ClientMessage}, converts it to string writes to websocket.
     *
     * @param message string to write to websocket.
     * @param id      string used to identify message response so the promise can be resolved.
     * @return PromisedReply of the reply ctrl message
     */
    protected PromisedReply<ServerMessage> sendWithPromise(ClientMessage message, String id) {
        PromisedReply<ServerMessage> future = new PromisedReply<>();
        try {
            send(message);
            mFutures.put(id, new FutureHolder(future, new Date()));
        } catch (Exception ex1) {
            try {
                future.reject(ex1);
            } catch (Exception ex2) {
                Log.d(TAG, "Exception while rejecting the promise", ex2);
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

    /**
     * Instantiate topic from subscription.
     *
     * @param sub subscription to use for instantiation.
     * @return new topic instance.
     */
    @SuppressWarnings("unchecked")
    Topic newTopic(Subscription sub) {
        if (TOPIC_ME.equals(sub.topic)) {
            return new MeTopic(this, (MeTopic.MeListener) null);
        } else if (TOPIC_FND.equals(sub.topic)) {
            return new FndTopic(this, null);
        }
        return new ComTopic(this, sub);
    }

    /**
     * Get 'me' topic from cache. If missing, instantiate it.
     *
     * @param <DP> type of Public value.
     * @return 'me' topic.
     */
    public <DP> MeTopic<DP> getOrCreateMeTopic() {
        MeTopic<DP> me = getMeTopic();
        if (me == null) {
            me = new MeTopic<>(this, (MeTopic.MeListener<DP>) null);
        }
        return me;
    }

    /**
     * Get 'fnd' topic from cache. If missing, instantiate it.
     *
     * @param <DP> type of Public value.
     * @return 'fnd' topic.
     */
    public <DP> FndTopic<DP> getOrCreateFndTopic() {
        FndTopic<DP> fnd = getFndTopic();
        if (fnd == null) {
            fnd = new FndTopic<>(this, null);
        }
        return fnd;
    }

    /**
     * Instantiate topic from {meta} packet using meta.desc.
     *
     * @return new topic or null if meta.desc is null.
     */
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
        List<Topic> result = new ArrayList<>(mTopics.size());
        for (Pair<Topic, Storage.Message> p : mTopics.values()) {
            result.add(p.first);
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Get the most recent timestamp of update to any topic.
     *
     * @return timestamp of the last update to any topic.
     */
    public Date getTopicsUpdated() {
        return mTopicsUpdated;
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
        for (Pair<Topic, Storage.Message> p : mTopics.values()) {
            if (filter.isIncluded(p.first)) {
                result.add((T) p.first);
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
    public Topic getTopic(@Nullable String name) {
        if (name == null) {
            return null;
        }
        Pair<Topic, ?> p = mTopics.get(name);
        return p != null? p.first : null;
    }

    /**
     * Get topic by name ensuring it's of ComTopic type.
     *
     * @param name name of the topic to find.
     * @return existing topic or null if no such topic was found or if it's not a ComTopic.
     */
    public ComTopic getComTopic(@Nullable String name) {
        Topic t = getTopic(name);
        if (!(t instanceof ComTopic)) {
            return null;
        }
        return (ComTopic) t;
    }

    /**
     * Start tracking topic: add it to in-memory cache.
     */
    void startTrackingTopic(final @NotNull Topic topic) {
        final String name = topic.getName();
        if (mTopics.containsKey(name)) {
            throw new IllegalStateException("Topic '" + name + "' is already registered");
        }
        mTopics.put(name, new Pair<>(topic, null));
        topic.setStorage(mStore);
    }

    /**
     * Stop tracking the topic: remove it from in-memory cache.
     */
    void stopTrackingTopic(@NotNull String topicName) {
        mTopics.remove(topicName);
    }

    /**
     * Get the latest cached message in the given topic.
     * @param topicName name of the topic to get message for.
     * @return last cached message or null.
     */
    public Storage.Message getLastMessage(@Nullable String topicName) {
        if (topicName == null) {
            return null;
        }
        Pair<?, Storage.Message> p = mTopics.get(topicName);
        return p != null? p.second : null;
    }

    void setLastMessage(@Nullable String topicName, @Nullable Storage.Message msg) {
        if (topicName == null || msg == null) {
            return;
        }
        Pair<?, Storage.Message> p = mTopics.get(topicName);
        if (p != null) {
            if (p.second == null ||
                    (p.second.isPending() && !msg.isPending()) ||
                    p.second.getSeqId() < msg.getSeqId()) {
                p.second = msg;
            }
        }
    }

    /**
     * Topic is cached by name, update the name used to cache the topic.
     *
     * @param topic   topic being updated
     * @param oldName old name of the topic (e.g. "newXYZ")
     * @return true if topic was found by the old name
     */
    @SuppressWarnings("UnusedReturnValue")
    synchronized boolean changeTopicName(@NotNull Topic topic, @NotNull String oldName) {
        boolean found = mTopics.remove(oldName) != null;
        mTopics.put(topic.getName(), new Pair<>(topic, null));
        if (mStore != null) {
            mStore.topicUpdate(topic);
        }
        return found;
    }

    /**
     * Look up user in a local cache: first in memory, then in persistent storage.
     *
     * @param uid ID of the user to find.
     * @return {@link User} object or {@code null} if no such user is found in local cache.
     */
    @SuppressWarnings("unchecked")
    public <SP> User<SP> getUser(@NotNull String uid) {
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
     * @param desc description of the new user.
     * @return {@link User} created user.
     */
    @SuppressWarnings("unchecked")
    User addUser(String uid, Description desc) {
        User user = new User(uid, desc);
        mUsers.put(uid, user);
        if (mStore != null) {
            mStore.userAdd(user);
        }
        return user;
    }

    @SuppressWarnings("unchecked")
    void updateUser(@NotNull Subscription sub) {
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
    void updateUser(@NotNull String uid, Description desc) {
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
     * @return ServerMessage or {@code null}
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
                        parser.currentLocation());
            }
            // Iterate over object fields:
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.currentName();
                parser.nextToken();
                JsonNode node = mapper.readTree(parser);
                try {
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
                } catch (Exception e) {
                    Log.w(TAG, "Failed to deserialize network message", e);
                }
            }
            parser.close(); // important to close both parser and underlying reader
        } catch (IOException e) {
            Log.w(TAG, "Failed to parse message", e);
        }

        return msg.isValid() ? msg : null;
    }


    /**
     * Checks if URL is a relative url, i.e. has no 'scheme://', including the case of missing scheme '//'.
     * The scheme is expected to be RFC-compliant, e.g. [a-z][a-z0-9+.-]*
     * example.html - ok
     * https:example.com - not ok.
     * http:/example.com - not ok.
     * ↲ https://example.com' - not ok. (↲ means carriage return)
     */
    public static boolean isUrlRelative(@NotNull String url) {
        Pattern re = Pattern.compile("^\\s*([a-z][a-z0-9+.-]*:|//)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        return !re.matcher(url).matches();
    }

    /**
     * Convert relative URL to absolute URL using Tinode server address as base.
     * If the URL is already absolute it's left unchanged.
     *
     * @param origUrl possibly relative URL to convert to absolute.
     * @return absolute URL or {@code null} if origUrl is invalid.
     */
    public @Nullable URL toAbsoluteURL(@NotNull String origUrl) {
        URL url = null;
        try {
            url = new URL(getBaseUrl(), origUrl);
        } catch (MalformedURLException ignored) {
        }
        return url;
    }

    /**
     * Check if the given URL is trusted: points to Tinode server using HTTP or HTTPS protocol.
     *
     * @param url URL to check.
     * @return true if the URL is trusted, false otherwise.
     */
    public boolean isTrustedURL(@NotNull URL url) {
        return mServerURI != null && ((url.getProtocol().equals("http") || url.getProtocol().equals("https"))
                && url.getAuthority().equals(mServerURI.getAuthority()));
    }

    /**
     * Get map with HTTP request parameters suitable for requests to Tinode server.
     *
     * @return Map with API key, authentication headers and User agent.
     */
    public @NotNull Map<String, String> getRequestHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        if (mApiKey != null) {
            headers.put("X-Tinode-APIKey", mApiKey);
        }
        if (mAuthToken != null) {
            headers.put("X-Tinode-Auth", "Token " + mAuthToken);
        }
        headers.put("User-Agent", makeUserAgent());
        return headers;
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
    public interface EventListener {
        /**
         * Connection established successfully, handshakes exchanged. The connection is ready for
         * login.
         *
         * @param code   should be always 201
         * @param reason should be always "Created"
         * @param params server parameters, such as protocol version
         */
        default void onConnect(int code, String reason, Map<String, Object> params) {
        }

        /**
         * Connection was dropped
         *
         * @param byServer true if connection was closed by server
         * @param code     numeric code of the error which caused connection to drop
         * @param reason   error message
         */
        default void onDisconnect(boolean byServer, int code, String reason) {
        }

        /**
         * Result of successful or unsuccessful {@link #login} attempt.
         *
         * @param code a numeric value between 200 and 299 on success, 400 or higher on failure
         * @param text "OK" on success or error message
         */
        default void onLogin(int code, String text) {
        }

        /**
         * Handle generic server message.
         *
         * @param msg message to be processed
         */
        default void onMessage(@SuppressWarnings("unused") ServerMessage msg) {
        }

        /**
         * Handle unparsed message. Default handler calls {@code #dispatchPacket(...)} on a
         * websocket thread.
         * A subclassed listener may wish to call {@code dispatchPacket()} on a UI thread
         *
         * @param msg message to be processed
         */
        default void onRawMessage(@SuppressWarnings("unused") String msg) {
        }

        /**
         * Handle control message
         *
         * @param ctrl control message to process
         */
        default void onCtrlMessage(@SuppressWarnings("unused") MsgServerCtrl ctrl) {
        }

        /**
         * Handle data message
         *
         * @param data control message to process
         */
        default void onDataMessage(MsgServerData data) {
        }

        /**
         * Handle info message
         *
         * @param info info message to process
         */
        default void onInfoMessage(MsgServerInfo info) {
        }

        /**
         * Handle meta message
         *
         * @param meta meta message to process
         */
        default void onMetaMessage(@SuppressWarnings("unused") MsgServerMeta meta) {
        }

        /**
         * Handle presence message
         *
         * @param pres control message to process
         */
        default void onPresMessage(@SuppressWarnings("unused") MsgServerPres pres) {
        }
    }

    // Helper class which calls given method of all added EventListener(s).
    private static class ListenerNotifier {
        private final Vector<EventListener> listeners;

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

    private record LoginCredentials(String scheme, String secret) {
    }

    // Container for storing unresolved futures.
        private record FutureHolder(PromisedReply<ServerMessage> future, Date timestamp) {
    }

    // Class which listens for websocket to connect.
    private class ConnectedWsListener implements Connection.WsListener {
        final Vector<PromisedReply<ServerMessage>> mCompletionPromises;

        ConnectedWsListener() {
            mCompletionPromises = new Vector<>();
        }

        void addPromise(PromisedReply<ServerMessage> promise) {
            mCompletionPromises.add(promise);
        }

        @Override
        public void onConnect(final Connection conn, final boolean background) {
            // Connection established, send handshake, inform listener on success
            hello(background).thenApply(
                    new PromisedReply.SuccessListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage pkt) throws Exception {
                            boolean doLogin = mAutologin && mLoginCredentials != null;

                            // Success. Reset backoff counter.
                            conn.backoffReset();

                            mTimeAdjustment = pkt.ctrl.ts.getTime() - new Date().getTime();
                            if (mStore != null) {
                                mStore.setTimeAdjustment(mTimeAdjustment);
                            }

                            synchronized (mConnLock) {
                                if (background) {
                                    mBkgConnCounter++;
                                } else {
                                    mFgConnection = true;
                                }
                            }

                            mNotifier.onConnect(pkt.ctrl.code, pkt.ctrl.text, pkt.ctrl.params);

                            // Resolve outstanding promises;
                            if (!doLogin) {
                                resolvePromises(pkt);
                            }

                            // Login automatically if it's enabled.
                            if (doLogin) {
                                return login(mLoginCredentials.scheme, mLoginCredentials.secret, null)
                                        .thenApply(new PromisedReply.SuccessListener<>() {
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
        public void onMessage(Connection conn, String message) {
            try {
                dispatchPacket(message);
            } catch (Exception ex) {
                Log.w(TAG, "Exception in dispatchPacket: ", ex);
            }
        }

        @Override
        public void onDisconnect(Connection conn, boolean byServer, int code, String reason) {
            handleDisconnect(byServer, -code, reason);
            // Promises may have already been rejected if onError was called first.
            try {
                rejectPromises(new ServerResponseException(503, "disconnected"));
            } catch (Exception ignored) {
                // Don't throw an exception as no one can catch it.
            }
        }

        @Override
        public void onError(Connection conn, Exception err) {
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

    // Use nulls instead of throwing an exception when Jackson is unable to parse input.
    private static class NullingDeserializationProblemHandler extends DeserializationProblemHandler {
        @Override
        public Object handleUnexpectedToken(DeserializationContext ctxt, JavaType targetType, JsonToken t,
                                            JsonParser p, String failureMsg) {
            Log.w(TAG, "Unexpected token:" + t.name());
            return null;
        }

        @Override
        public Object handleWeirdKey(DeserializationContext ctxt, Class<?> rawKeyType,
                                     String keyValue, String failureMsg) {
            Log.w(TAG, "Weird key: '" + keyValue + "'");
            return  null;
        }

        @Override
        public Object handleWeirdNativeValue(DeserializationContext ctxt, JavaType targetType,
                                             Object valueToConvert, JsonParser p) {
            Log.w(TAG, "Weird native value: '" + valueToConvert + "'");
            return  null;
        }

        @Override
        public Object handleWeirdNumberValue(DeserializationContext ctxt, Class<?> targetType,
                                             Number valueToConvert, String failureMsg) {
            Log.w(TAG, "Weird number value: '" + valueToConvert + "'");
            return  null;
        }

        @Override
        public Object handleWeirdStringValue(DeserializationContext ctxt, Class<?> targetType,
                                             String valueToConvert, String failureMsg) {
            Log.w(TAG, "Weird string value: '" + valueToConvert + "'");
            return  null;
        }
    }
}