package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import co.tinode.tinodesdk.model.AccessChange;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.AcsHelper;
import co.tinode.tinodesdk.model.Defacs;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.LastSeen;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.MetaSetSub;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgRange;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.Pair;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;
import co.tinode.tinodesdk.model.TrustedType;

/**
 * Class for handling communication on a single topic
 * Generic parameters:
 *
 * @param <DP> is the type of Desc.Public
 * @param <DR> is the type of Desc.Private
 * @param <SP> is the type of Subscription.Public
 * @param <SR> is the type of Subscription.Private
 */
@SuppressWarnings("WeakerAccess, unused")
public class Topic<DP, DR, SP, SR> implements LocalData, Comparable<Topic> {
    private static final String TAG = "Topic";

    protected final Tinode mTinode;
    protected String mName;
    // The bulk of topic data
    protected final Description<DP, DR> mDesc;
    // Cache of topic subscribers indexed by userID
    protected HashMap<String, Subscription<SP, SR>> mSubs = null;
    // Timestamp of the last update to subscriptions. Default: Oct 25, 2014 05:06:02 UTC, incidentally equal
    // to the first few digits of sqrt(2)
    protected Date mSubsUpdated = null;

    // Server-provided values:
    // Tags: user and topic discovery
    protected String[] mTags;
    // Auxiliary data.
    protected HashMap<String,Object> mAux;
    // The topic is subscribed/online.
    protected int mAttached = 0;
    protected Listener<DP, DR, SP, SR> mListener = null;
    // Timestamp of the last key press that the server was notified of, milliseconds
    protected long mLastKeyPress = 0;

    // ID of the last applied delete transaction. Different from 'clear' which is the highest known.
    protected int mMaxDel = 0;
    // Topic status: true if topic is deleted by remote, false otherwise.
    protected boolean mDeleted = false;
    /**
     * The mStore is set by Tinode when the topic calls {@link Tinode#startTrackingTopic(Topic)}
     */
    Storage mStore = null;
    private Payload mLocal = null;

    Topic(Tinode tinode, String name) {
        mTinode = tinode;
        setName(name);
        mDesc = new Description<>();

        // Tinode could be null if the topic does not need to be tracked, i.e.
        // loaded by Firebase in response to a push notification.
        if (mTinode != null) {
            mTinode.startTrackingTopic(this);
        }
    }

    // Create new group topic.
    Topic(Tinode tinode, boolean isChannel) {
        this(tinode, (isChannel ? Tinode.CHANNEL_NEW : Tinode.TOPIC_NEW) + tinode.nextUniqueString());
    }

    protected Topic(Tinode tinode, Subscription<SP, SR> sub) {
        this(tinode, sub.topic);
        mDesc.merge(sub);
    }

    protected Topic(Tinode tinode, String name, Description<DP, DR> desc) {
        this(tinode, name);
        mDesc.merge(desc);
    }

    /**
     * Create a named topic.
     *
     * @param tinode instance of Tinode object to communicate with the server
     * @param name   name of the topic
     * @param l      event listener, optional
     * @throws IllegalArgumentException if 'tinode' argument is null
     */
    protected Topic(Tinode tinode, String name, Listener<DP, DR, SP, SR> l) {
        this(tinode, name);
        setListener(l);
    }

    /**
     * Start a new topic.
     * <p>
     * Construct {@code }typeOfT} with one of {@code
     * com.fasterxml.jackson.databind.type.TypeFactory.constructXYZ()} methods such as
     * {@code mMyConnectionInstance.getTypeFactory().constructType(MyPayloadClass.class)}.
     * <p>
     * The actual topic name will be set after completion of a successful subscribe call
     *
     * @param tinode tinode instance
     * @param l      event listener, optional
     */
    protected Topic(Tinode tinode, Listener<DP, DR, SP, SR> l, boolean isChannel) {
        this(tinode, isChannel);
        setListener(l);
    }

    // Returns greater of two dates.
    private static Date maxDate(Date a, Date b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Get type of the topic from the given topic name.
     * @param name name to get type from.
     * @return type of the topic name.
     */
    public static TopicType getTopicTypeByName(final String name) {
        if (name != null) {
            if (name.equals(Tinode.TOPIC_ME)) {
                return TopicType.ME;
            } else if (name.equals(Tinode.TOPIC_SYS)) {
                return TopicType.SYS;
            } else if (name.equals(Tinode.TOPIC_SLF)) {
                return TopicType.SLF;
            } else if (name.equals(Tinode.TOPIC_FND)) {
                return TopicType.FND;
            } else if (name.startsWith(Tinode.TOPIC_GRP_PREFIX) || name.startsWith(Tinode.TOPIC_NEW) ||
                    name.startsWith(Tinode.TOPIC_CHN_PREFIX) || name.startsWith(Tinode.CHANNEL_NEW)) {
                return TopicType.GRP;
            } else if (name.startsWith(Tinode.TOPIC_USR_PREFIX)) {
                return TopicType.P2P;
            }
        }
        return TopicType.UNKNOWN;
    }

    /**
     * Check if the type of the given topic name is P2P.
     * @param name name of the topic to check.
     * @return <code>true</code> if the given name is P2P, <code>false</code> otherwise.
     */
    public static boolean isP2PType(final String name) {
        return getTopicTypeByName(name) == TopicType.P2P;
    }

    /**
     * Check if the type of the given topic name is Group.
     * @param name name of the topic to check.
     * @return <code>true</code> if the given name is Group, <code>false</code> otherwise.
     */
    public static boolean isGrpType(final String name) {
        return getTopicTypeByName(name) == TopicType.GRP;
    }

    /**
     * Check if the topic is a Slf (self) topic.
     * @param name name of the topic to check.
     * @return true if the topic is a Slf topic, false otherwise.
     */
    public static boolean isSlfType(String name) {
        return getTopicTypeByName(name) == TopicType.SLF;
    }

    /**
     * Checks if given topic name is a new (unsynchronized) topic.
     * @param name name to check
     * @return true if the name is a name of a new topic, false otherwise.
     */
    public static boolean isNew(String name) {
        // "newRANDOM" or "nchRANDOM" when the topic was locally initialized but not yet synced with the server.
        return name.startsWith(Tinode.TOPIC_NEW) || name.startsWith(Tinode.CHANNEL_NEW);
    }

    /**
     * Checks if given topic name is a channel.
     * @param name name to check
     * @return true if the name is a name of a channel, false otherwise.
     */
    public static boolean isChannel(String name) {
        // "cnhAbCDef123" or "nchAbCDef123".
        return name.startsWith(Tinode.TOPIC_CHN_PREFIX) || name.startsWith(Tinode.CHANNEL_NEW);
    }

    /**
     * Set custom types of payload: {data} as well as public and private content. Needed for
     * deserialization of server messages.
     *
     * @param typeOfDescPublic  type of {meta.desc.public}
     * @param typeOfDescPrivate type of {meta.desc.private}
     * @param typeOfSubPublic   type of {meta.subs[].public}
     * @param typeOfSubPrivate  type of {meta.subs[].private}
     */
    public void setTypes(JavaType typeOfDescPublic, JavaType typeOfDescPrivate,
                         JavaType typeOfSubPublic, JavaType typeOfSubPrivate) {
        mTinode.setTypeOfMetaPacket(mName, typeOfDescPublic, typeOfDescPrivate,
                typeOfSubPublic, typeOfSubPrivate);
    }

    /**
     * Set types of payload: {data} as well as public and private content. Needed for
     * deserialization of server messages.
     *
     * @param typeOfDescPublic  type of {meta.desc.public}
     * @param typeOfDescPrivate type of {meta.desc.private}
     * @param typeOfSubPublic   type of {meta.sub[].public}
     * @param typeOfSubPrivate  type of {meta.sub[].private}
     */
    public void setTypes(Class<?> typeOfDescPublic, Class<?> typeOfDescPrivate,
                         Class<?> typeOfSubPublic, Class<?> typeOfSubPrivate) {
        final TypeFactory tf = Tinode.getTypeFactory();
        setTypes(tf.constructType(typeOfDescPublic), tf.constructType(typeOfDescPrivate),
                tf.constructType(typeOfSubPublic), tf.constructType(typeOfSubPrivate));
    }

    /**
     * Set types of payload: {data} content as well as public and private fields of topic.
     * Type names must be generated by {@link JavaType#toCanonical()}
     *
     * @param typeOfDescPublic  type of {meta.desc.public}
     * @param typeOfDescPrivate type of {meta.desc.private}
     * @param typeOfSubPublic   type of {meta.desc.public}
     * @param typeOfSubPrivate  type of {meta.desc.private}
     * @throws IllegalArgumentException if types cannot be parsed
     */
    public void setTypes(String typeOfDescPublic, String typeOfDescPrivate,
                         String typeOfSubPublic, String typeOfSubPrivate) throws IllegalArgumentException {
        final TypeFactory tf = Tinode.getTypeFactory();
        setTypes(tf.constructFromCanonical(typeOfDescPublic), tf.constructFromCanonical(typeOfDescPrivate),
                tf.constructFromCanonical(typeOfSubPublic), tf.constructFromCanonical(typeOfSubPrivate));
    }

    /**
     * Update topic parameters from a Subscription object. Called by MeTopic.
     *
     * @param sub updated topic parameters
     */
    protected boolean update(Subscription<SP, SR> sub) {
        boolean changed = mDesc.merge(sub);

        if (changed) {
            if (mStore != null) {
                mStore.topicUpdate(this);
            }
            if (isP2PType()) {
                mTinode.updateUser(getName(), mDesc);
            }
        }

        return changed;
    }

    /**
     * Update topic parameters from a Description object.
     *
     * @param desc updated topic parameters
     */
    protected void update(Description<DP, DR> desc) {
        if (mDesc.merge(desc)) {
            if (mStore != null) {
                mStore.topicUpdate(this);
            }
            if (isP2PType()) {
                mTinode.updateUser(getName(), mDesc);
            }
        }
    }

    /**
     * Topic sent an update to subscription, got a confirmation.
     *
     * @param params {ctrl} parameters returned by the server (could be null).
     * @param sSub   updated topic parameters.
     */
    @SuppressWarnings("unchecked")
    protected void update(Map<String, Object> params, MetaSetSub sSub) {
        String user = sSub.user;

        Map<String, String> acsMap = params != null ? (Map<String, String>) params.get("acs") : null;
        Acs acs;
        if (acsMap != null) {
            acs = new Acs(acsMap);
        } else {
            acs = new Acs();
            if (user == null) {
                acs.setWant(sSub.mode);
            } else {
                acs.setGiven(sSub.mode);
            }
        }

        if (user == null || mTinode.isMe(user)) {
            user = mTinode.getMyId();
            boolean changed;
            // This is an update to user's own subscription to topic (want)
            if (mDesc.acs == null) {
                mDesc.acs = acs;
                changed = true;
            } else {
                changed = mDesc.acs.merge(acs);
            }

            if (changed) {
                if (mStore != null) {
                    mStore.topicUpdate(this);
                }
                if (isP2PType()) {
                    mTinode.updateUser(getName(), mDesc);
                }
            }
        }


        // This is an update to someone else's subscription to topic (given)
        Subscription<SP, SR> sub = getSubscription(user);
        if (sub == null) {
            sub = new Subscription<>();
            sub.user = user;
            sub.acs = acs;
            addSubToCache(sub);
            if (mStore != null) {
                mStore.subNew(this, sub);
            }
        } else {
            sub.acs.merge(acs);
            if (mStore != null) {
                mStore.subUpdate(this, sub);
            }
        }
    }

    /**
     * Topic sent an update to topic parameters, got a confirmation, now copy
     * these parameters to topic description.
     *
     * @param desc updated topic parameters
     */
    protected void update(MetaSetDesc<DP, DR> desc) {
        if (mDesc.merge(desc)) {
            if (mStore != null) {
                mStore.topicUpdate(this);
            }
            if (isP2PType()) {
                mTinode.updateUser(getName(), mDesc);
            }
        }
    }

    /**
     * Topic sent an update to description or subscription, got a confirmation, now
     * update local data with the new info.
     *
     * @param ctrl {ctrl} packet sent by the server
     * @param meta original {meta} packet updated topic parameters
     */
    protected void update(MsgServerCtrl ctrl, MsgSetMeta<DP, DR> meta) {
        if (meta.isDescSet()) {
            update(meta.desc);
            if (mListener != null) {
                mListener.onMetaDesc(mDesc);
            }
        }

        if (meta.isSubSet()) {
            update(ctrl.params, meta.sub);
            if (mListener != null) {
                if (meta.sub.user == null) {
                    mListener.onMetaDesc(mDesc);
                }
                mListener.onSubsUpdated();
            }
        }

        if (meta.isTagsSet()) {
            update(meta.tags);
            if (mListener != null) {
                mListener.onMetaTags(mTags);
            }
        }

        if (meta.isAuxSet()) {
            update(meta.aux);
            if (mListener != null) {
                mListener.onMetaAux(mAux);
            }
        }
    }

    /**
     * Update topic parameters from a tags array.
     *
     * @param tags updated topic  tags
     */
    protected void update(String[] tags) {
        this.mTags = tags;
        if (mStore != null) {
            mStore.topicUpdate(this);
        }
    }

    /**
     * Find the first tag with the given prefix.
     * @param prefix prefix to search for.
     * @return tag if found or null.
     */
    @Nullable
    public String tagByPrefix(@NotNull String prefix) {
        return Tinode.tagByPrefix(mTags, prefix);
    }

    /**
     * Find the first tag with the given prefix and return tag value, i.e. in 'prefix:value' return the 'value'.
     * @param prefix prefix to search for.
     * @return tag value if found or null.
     */
    @Nullable
    public String tagValueByPrefix(@NotNull String prefix) {
        Pair<String, String> tag = Tinode.tagSplit(Tinode.tagByPrefix(mTags, prefix));
        return tag != null ? tag.second : null;
    }

    /**
     * Get the first tag with the 'alias:' prefix and return its value, i.e. in 'alias:abc' return the 'abc'.
     * @return alias value, if present, or null.
     */
    @Nullable
    public String alias() {
        return tagValueByPrefix(Tinode.TAG_ALIAS);
    }

    /**
     * Update topic parameters from a tags array.
     *
     * @param aux updated auxiliary topic data.
     */
    protected void update(Map<String,Object> aux) {
        mAux = (HashMap<String, Object>) mergeMaps(mAux, aux);
        if (mAux != null) {
            // Sanitize aux.pins array.
            Object pinsObj = this.mAux.get("pins");
            if (pinsObj instanceof List) {
                List<Integer> pinList = ((List<?>) pinsObj).stream()
                        .mapToInt((ToIntFunction<Object>) value ->
                                value instanceof Number ? ((Number) value).intValue() : 0)
                        .filter(value -> value > 0)
                        .boxed()
                        .collect(Collectors.toList());
                this.mAux.put("pins", pinList);
            }
        }
        if (mStore != null) {
            mStore.topicUpdate(this);
        }
    }

    private static @Nullable Map<String, Object> mergeMaps(@Nullable Map<String, Object> dst,
                                                          @Nullable Map<String, Object> src) {
        if (src == null) {
            return dst;
        }

        if (dst == null) {
            return new HashMap<>(src);
        }

        for (Map.Entry<String, Object> e : src.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (Tinode.NULL_VALUE.equals(value)) {
                dst.remove(key);
            } else if (value != null) {
                dst.put(key, value);
            }
        }

        return dst;
    }

    /**
     * Assign pointer to cache.
     * Called by Tinode from {@link Tinode#startTrackingTopic(Topic)}
     *
     * @param store storage object
     */
    protected void setStorage(Storage store) {
        mStore = store;
    }

    public Date getCreated() {
        return mDesc.created;
    }

    public void setCreated(Date created) {
        mDesc.created = maxDate(mDesc.created, created);
    }

    public Date getUpdated() {
        return mDesc.updated;
    }

    public void setUpdated(Date updated) {
        mDesc.updated = maxDate(mDesc.updated, updated);
    }

    public Date getTouched() {
        return mDesc.touched;
    }

    public void setTouched(Date touched) {
        mDesc.touched = maxDate(mDesc.touched, touched);
    }

    @Override
    public int compareTo(@NotNull Topic t) {
        if (t.mDesc.touched == null) {
            if (mDesc.touched == null) {
                return 0;
            }
            return -1;
        }

        if (mDesc.touched == null) {
            return 1;
        }

        return -mDesc.touched.compareTo(t.mDesc.touched);
    }

    /**
     * Get timestamp of the latest update to subscriptions.
     * @return timestamp of the latest update to subscriptions
     */
    public Date getSubsUpdated() {
        return mSubsUpdated;
    }

    /**
     * Get greatest known seq ID as reported by the server.
     * @return greatest known seq ID.
     */
    public int getSeq() {
        return mDesc.seq;
    }

    /**
     * Update greatest known seq ID.
     * @param seq new seq ID.
     */
    public void setSeq(int seq) {
        if (seq > mDesc.seq) {
            mDesc.seq = seq;
        }
    }

    /**
     * Set new seq value and if it's greater than the current value make a network call to fetch new messages.
     * @param seq sequential ID to assign.
     */
    protected void setSeqAndFetch(final int seq) {
        if (seq > mDesc.seq) {
            // Fetch only if not attached. If it's attached it will be fetched elsewhere.
            if (!isAttached()) {
                try {
                    subscribe(null, getMetaGetBuilder().withLaterData().build()).thenApply(
                            new PromisedReply.SuccessListener<>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                                    mDesc.seq = seq;
                                    leave();
                                    return null;
                                }
                            }
                    );
                } catch (Exception ex) {
                    Log.w(TAG, "Failed to sync data", ex);
                }
            }
        }
    }

    public int getClear() {
        return mDesc.clear;
    }

    public void setClear(int clear) {
        if (clear > mDesc.clear) {
            mDesc.clear = clear;
        }
    }

    public int getMaxDel() {
        return mMaxDel;
    }

    public void setMaxDel(int max_del) {
        if (max_del > mMaxDel) {
            mMaxDel = max_del;
        }
    }

    public int getRead() {
        return mDesc.read;
    }

    public void setRead(int read) {
        if (read > mDesc.read) {
            mDesc.read = read;
        }
    }

    public int getRecv() {
        return mDesc.recv;
    }

    public void setRecv(int recv) {
        if (recv > mDesc.recv) {
            mDesc.recv = recv;
        }
    }

    public String[] getTags() {
        return mTags == null ? null : Arrays.copyOf(mTags, mTags.length);
    }

    public void setTags(String[] tags) {
        mTags = tags;
    }

    public Map<String, Object> getAux() {
        return mAux != null ? new HashMap<>(mAux) : null;
    }

    public Object getAux(String key) {
        return mAux != null ? mAux.get(key) : null;
    }

    public void setAux(Map<String, Object> aux) {
        mAux = aux != null ? new HashMap<>(aux) : null;
    }

    public DP getPub() {
        return mDesc.pub;
    }

    public void setPub(DP pub) {
        mDesc.pub = pub;
    }

    public TrustedType getTrusted() {
        return mDesc.trusted;
    }

    public void setTrusted(TrustedType trusted) {
        mDesc.trusted = trusted;
    }

    public DR getPriv() {
        return mDesc.priv;
    }

    public void setPriv(DR priv) {
        mDesc.priv = priv;
    }

    /**
     * Checks if the topic is archived. Not all topics support archiving.
     *
     * @return true if the topic is archived, false otherwise.
     */
    public boolean isArchived() {
        return false;
    }

    /**
     * Checks if the topic is deleted by remote.
     *
     * @return true if the topic is deleted by remote, false otherwise.
     */
    public boolean isDeleted() {
        return mDeleted;
    }

    /**
     * Mark topic as deleted.
     *
     * @param status true to mark topic as deleted, false to restore.
     */
    public void setDeleted(boolean status) {
        mDeleted = status;
    }
    public @Nullable MsgRange getCachedMessagesRange() {
        return mStore == null ? null : mStore.getCachedMessagesRange(this);
    }

    public @Nullable MsgRange[] getMissingMessageRanges(int startFrom, int limit, boolean newer) {
        if (mStore == null) {
            return null;
        }
        // If topic has messages, fetch the next missing message range (could be null)
        return mStore.getMissingRanges(this, startFrom, limit, newer);
    }

    /* Access mode management */
    public Acs getAccessMode() {
        return mDesc.acs;
    }

    public void setAccessMode(Acs mode) {
        mDesc.acs = mode;
    }

    public boolean updateAccessMode(AccessChange ac) {
        if (mDesc.acs == null) {
            mDesc.acs = new Acs();
        }

        boolean updated = mDesc.acs.update(ac);
        if (updated && mListener != null) {
            mListener.onMetaDesc(mDesc);
        }

        return updated;
    }

    /**
     * Check if user has an Approver (A) permission.
     *
     * @return true if the user has the permission.
     */
    public boolean isApprover() {
        return mDesc.acs != null && mDesc.acs.isApprover();
    }

    public PromisedReply<ServerMessage> updateAdmin(final boolean admin) {
        return updateMode(null, admin ? "+A" : "-A");
    }

    /**
     * Check if user has O or A permissions.
     *
     * @return true if current user is the owner (O) or approver (A).
     */
    public boolean isManager() {
        return mDesc.acs != null && mDesc.acs.isManager();
    }

    /**
     * Check if user has a Sharer (S) permission.
     *
     * @return true if user has the permission.
     */
    public boolean isSharer() {
        return mDesc.acs != null && mDesc.acs.isSharer();
    }

    public PromisedReply<ServerMessage> updateSharer(final boolean sharer) {
        return updateMode(null, sharer ? "+S" : "-S");
    }

    public boolean isMuted() {
        return mDesc.acs != null && mDesc.acs.isMuted();
    }

    @SuppressWarnings("UnusedReturnValue")
    public PromisedReply<ServerMessage> updateMuted(final boolean muted) {
        return updateMode(null, muted ? "-P" : "+P");
    }

    /**
     * Check if user is the Owner (O) of the topic.
     */
    public boolean isOwner() {
        return mDesc.acs != null && mDesc.acs.isOwner();
    }

    /**
     * Check if user has Read (R) permission.
     */
    public boolean isReader() {
        return mDesc.acs != null && mDesc.acs.isReader();
    }

    /**
     * Check if user has Write (W) permission.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isWriter() {
        return mDesc.acs != null && mDesc.acs.isWriter();
    }

    /**
     * Check if user has Join (J) permission on both sides: 'want' and 'given'.
     */
    public boolean isJoiner() {
        return mDesc.acs != null && mDesc.acs.isJoiner();
    }

    /**
     * Check if current user is blocked in the topic (does not have J permission on the Given side).
     */
    public boolean isBlocked() {
        return mDesc.acs == null || !mDesc.acs.isJoiner(Acs.Side.GIVEN);
    }

    /**
     * Check if user has permission to hard-delete messages (D).
     */
    public boolean isDeleter() {
        return mDesc.acs != null && mDesc.acs.isDeleter();
    }

    public Defacs getDefacs() {
        return mDesc.defacs;
    }

    public void setDefacs(Defacs da) {
        mDesc.defacs = da;
    }

    public void setDefacs(String auth, String anon) {
        mDesc.defacs.setAuth(auth);
        mDesc.defacs.setAnon(anon);
    }

    public AcsHelper getAuthAcs() {
        return mDesc.defacs == null ? null : mDesc.defacs.auth;
    }

    public String getAuthAcsStr() {
        return mDesc.defacs != null && mDesc.defacs.auth != null ? mDesc.defacs.auth.toString() : "";
    }

    public AcsHelper getAnonAcs() {
        return mDesc.defacs == null ? null : mDesc.defacs.anon;
    }

    public String getAnonAcsStr() {
        return mDesc.defacs != null && mDesc.defacs.anon != null ? mDesc.defacs.anon.toString() : "";
    }

    public int getUnreadCount() {
        int unread = mDesc.seq - mDesc.read;
        return Math.max(unread, 0);
    }

    /**
     * Get topic's online status.
     * @return true if topic is online, false otherwise.
     */
    public boolean getOnline() {
        return mDesc.online != null ? mDesc.online : false;
    }

    protected void setOnline(boolean online) {
        if (mDesc.online == null || online != mDesc.online) {
            mDesc.online = online;
            if (mListener != null) {
                mListener.onOnline(mDesc.online);
            }
        }
    }

    /**
     * Check if the topic is stored.
     *
     * @return true if the topic is persisted in local storage, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean isPersisted() {
        return getLocal() != null;
    }

    /**
     * Store topic to DB.
     */
    protected void persist() {
        if (mStore != null) {
            if (!isPersisted()) {
                mStore.topicAdd(this);
            }
        }
    }
    /**
     * Remove topic from DB or mark it as deleted.
     */
    protected void expunge(boolean hard) {
        mDeleted = true;
        if (mStore != null) {
            mStore.topicDelete(this, hard);
        }
    }

    protected boolean isTrusted(final String key) {
        if (mDesc.trusted != null) {
            return mDesc.trusted.getBooleanValue(key);
        }
        return false;
    }

    public boolean isTrustedVerified() {
        return isTrusted("verified");
    }
    public boolean isTrustedStaff() {
        return isTrusted("staff");
    }
    public boolean isTrustedDanger() {
        return isTrusted("danger");
    }

    /**
     * Update timestamp and user agent of when the topic was last online.
     */
    public void setLastSeen(Date when, String ua) {
        mDesc.seen = new LastSeen(when, ua);
    }

    /**
     * Update timestamp of when the topic was last online.
     */
    protected void setLastSeen(Date when) {
        if (mDesc.seen != null) {
            mDesc.seen.when = when;
        } else {
            mDesc.seen = new LastSeen(when);
        }
    }

    /**
     * Get timestamp when the topic was last online, if available.
     */
    public Date getLastSeen() {
        return mDesc.seen != null ? mDesc.seen.when : null;
    }

    /**
     * Get user agent string associated with the time when the topic was last online.
     */
    public String getLastSeenUA() {
        return mDesc.seen != null ? mDesc.seen.ua : null;
    }

    /**
     * Subscribe to topic.
     */
    protected PromisedReply<ServerMessage> subscribe() {
        MetaGetBuilder mgb = getMetaGetBuilder().withDesc().withData().withSub();
        if (isMeType() || (isGrpType() && isOwner())) {
            // Ask for tags only if it's a 'me' topic or the user is the owner of a 'grp' topic.
            mgb = mgb.withTags();
        }

        return subscribe(null, mgb.build());
    }

    /**
     * Subscribe to topic with parameters, optionally in background.
     *
     * @throws NotConnectedException      if there is no live connection to the server
     * @throws AlreadySubscribedException if the client is already subscribed to the given topic
     */
    @SuppressWarnings("unchecked")
    public PromisedReply<ServerMessage> subscribe(MsgSetMeta<DP, DR> set, MsgGetMeta get) {
        if (mAttached > 0) {
            if (set == null && get == null) {
                // If the topic is already attached and the user does not attempt to set or
                // get any data, just return resolved promise.
                return new PromisedReply<>((ServerMessage) null);
            }
            return new PromisedReply<>(new AlreadySubscribedException());
        }

        final String topicName = getName();
        if (!isPersisted()) {
            persist();
        }

        return mTinode.subscribe(topicName, set, get).thenApply(
                new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                        if (msg.ctrl == null || msg.ctrl.code >= 300) {
                            // 3XX response: already subscribed.
                            mAttached++;
                            return null;
                        }

                        if (mAttached <= 0) {
                            mAttached = 1;
                            if (msg.ctrl.params != null) {
                                Map<String, String> acs = (Map<String, String>) msg.ctrl.params.get("acs");
                                if (acs != null) {
                                    mDesc.acs = new Acs(acs);
                                }

                                if (isNew()) {
                                    setUpdated(msg.ctrl.ts);
                                    setName(msg.ctrl.topic);
                                    mTinode.changeTopicName(Topic.this, topicName);
                                }

                                if (mStore != null) {
                                    mStore.topicUpdate(Topic.this);
                                }
                                if (isP2PType()) {
                                    mTinode.updateUser(getName(), mDesc);
                                }
                            }

                            if (mListener != null) {
                                mListener.onSubscribe(msg.ctrl.code, msg.ctrl.text);
                            }

                        } else {
                            mAttached++;
                        }
                        return null;
                    }
                }, new PromisedReply.FailureListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                        // Clean up if topic creation failed for any reason.
                        if (isNew() && err instanceof ServerResponseException sre) {
                            if (sre.getCode() >= ServerMessage.STATUS_BAD_REQUEST) {
                                mTinode.stopTrackingTopic(topicName);
                                expunge(true);
                            }
                        }

                        // Rethrow exception to trigger the next failure handler.
                        throw err;
                    }
                });
    }

    public MetaGetBuilder getMetaGetBuilder() {
        return new MetaGetBuilder(this);
    }

    /**
     * Leave topic
     *
     * @param unsub true to disconnect and unsubscribe from topic, otherwise just disconnect
     */
    public PromisedReply<ServerMessage> leave(final boolean unsub) {
        if (mAttached == 1 || (mAttached >= 1 && unsub)) {
            return mTinode.leave(getName(), unsub).thenApply(
                    new PromisedReply.SuccessListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            topicLeft(unsub, result.ctrl.code, result.ctrl.text);
                            if (unsub) {
                                mTinode.stopTrackingTopic(getName());
                                expunge(true);
                            }
                            return null;
                        }
                    });
        } else if (mAttached >= 1) {
            // Attached more than once, just decrement count.
            mAttached --;
            return new PromisedReply<>((ServerMessage) null);
        } else if (!unsub) {
            // Detaching (not unsubscribing) while not attached.
            return new PromisedReply<>((ServerMessage) null);
        } else if (mTinode.isConnected()) {
            return new PromisedReply<>(new NotSubscribedException());
        }

        return new PromisedReply<>(new NotConnectedException());
    }

    /**
     * Leave topic without unsubscribing
     */
    @SuppressWarnings("UnusedReturnValue")
    public PromisedReply<ServerMessage> leave() {
        return leave(false);
    }

    // Handle server response to publish().
    private void processDelivery(final MsgServerCtrl ctrl, final long id) {
        if (ctrl != null) {
            int seq = ctrl.getIntParam("seq", 0);
            if (seq > 0) {
                setSeq(seq);
                setTouched(ctrl.ts);
                if (id > 0 && mStore != null) {
                    if (mStore.msgDelivered(this, id, ctrl.ts, seq)) {
                        setRecv(seq);
                    }
                } else {
                    setRecv(seq);
                }

                // FIXME: this causes READ notification not to be sent.
                setRead(seq);
                if (mStore != null) {
                    mStore.setRead(this, seq);

                    // Update cached message.
                    mTinode.setLastMessage(getName(), mStore.getMessagePreviewById(id));
                }
            }
        }
    }

    protected PromisedReply<ServerMessage> publish(final Drafty content, Map<String, Object> head, final long msgId) {
        String[] attachments = null;
        if (!content.isPlain()) {
            if (head == null) {
                head = new HashMap<>();
            }
            head.put("mime", Drafty.MIME_TYPE);
            attachments = content.getEntReferences();
        } else if (head != null) {
            // Otherwise, plain text content should not have "mime" header. Clear it.
            head.remove("mime");
        }
        return mTinode.publish(getName(), content.isPlain() ? content.toString() : content, head, attachments).thenApply(
                new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        processDelivery(result.ctrl, msgId);
                        return null;
                    }
                },
                new PromisedReply.FailureListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                        if (mStore != null) {
                            mStore.msgSyncing(Topic.this, msgId, false);

                            // Update cached message.
                            mTinode.setLastMessage(getName(), mStore.getMessagePreviewById(msgId));
                        }
                        // Rethrow exception to trigger the next possible failure listener.
                        throw err;
                    }
                });
    }

    /**
     * Publish message to a topic. It will attempt to publish regardless of subscription status.
     *
     * @param content payload
     */
    public PromisedReply<ServerMessage> publish(final Drafty content) {
        return publish(content, null);
    }

    /**
     * Publish message to a topic. It will attempt to publish regardless of subscription status.
     *
     * @param content payload
     * @param extraHeaders additional message headers.
     */
    public PromisedReply<ServerMessage> publish(final Drafty content, final Map<String, Object> extraHeaders) {
        final Map<String, Object> head;
        if (!content.isPlain() || (extraHeaders != null && !extraHeaders.isEmpty())) {
            head = new HashMap<>();
            if (extraHeaders != null) {
                head.putAll(extraHeaders);
            }
            if (!content.isPlain()) {
                head.put("mime", Drafty.MIME_TYPE);
            }
            if (head.get("webrtc") != null) {
                Drafty.updateVideoEnt(content, head, false);
            }
        } else {
            head = null;
        }

        final Storage.Message msg;
        if (mStore != null) {
            msg = mStore.msgSend(this, content, head);
        } else {
            msg = null;
        }

        final long msgId;
        if (msg != null) {
            // Cache the message.
            mTinode.setLastMessage(getName(), msg);
            msgId = msg.getDbId();
        } else {
            msgId = -1;
        }

        if (mAttached > 0) {
            return publish(content, head, msgId);
        } else {
            return subscribe()
                    .thenApply(new PromisedReply.SuccessListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            mAttached++;
                            return publish(content, head, msgId);
                        }
                    })
                    .thenCatch(new PromisedReply.FailureListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                            if (mStore != null) {
                                mStore.msgSyncing(Topic.this, msgId, false);
                            }
                            throw err;
                        }
                    });
        }
    }

    /**
     * Convenience method for plain text messages. Will convert message to Drafty.
     *
     * @param content message to send
     * @return PromisedReply
     */
    public PromisedReply<ServerMessage> publish(String content) {
        return publish(Drafty.parse(content));
    }

    /**
     * Re-send pending messages, delete messages marked for deletion.
     * Processing will stop on the first error.
     *
     * @return {@link PromisedReply} of the last sent command.
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to server
     */
    @SuppressWarnings("UnusedReturnValue")
    public synchronized <ML extends Iterator<Storage.Message> & Closeable> PromisedReply<ServerMessage> syncAll() {
        PromisedReply<ServerMessage> last = new PromisedReply<>((ServerMessage) null);
        if (mStore == null) {
            return last;
        }

        // Get soft-deleted message IDs.
        final MsgRange[] toSoftDelete = mStore.getQueuedMessageDeletes(this, false);
        if (toSoftDelete != null) {
            last = mTinode.delMessage(getName(), toSoftDelete, false);
        }

        // Get hard-deleted message IDs.
        final MsgRange[] toHardDelete = mStore.getQueuedMessageDeletes(this, true);
        if (toHardDelete != null) {
            last = mTinode.delMessage(getName(), toHardDelete, true);
        }

        ML toSend = mStore.getQueuedMessages(this);
        if (toSend == null) {
            return last;
        }
        //noinspection TryFinallyCanBeTryWithResources: does not really apply here due to the need for the second try-catch
        try {
            while (toSend.hasNext()) {
                Storage.Message msg = toSend.next();
                final long msgId = msg.getDbId();
                if (msg.getStringHeader("webrtc") != null) {
                    // Drop unsent video call messages.
                    mStore.msgDiscard(this, msgId);
                    continue;
                }
                mStore.msgSyncing(this, msgId, true);
                last = publish(msg.getContent(), msg.getHead(), msgId);
            }
        } finally {
            try {
                toSend.close();
            } catch (IOException ignored) {
            }
        }
        return last;
    }

    /**
     * Try to sync one message.
     *
     * @return {@link PromisedReply} resolved on result of the operation.
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to server
     */
    public synchronized PromisedReply<ServerMessage> syncOne(long msgDatabaseId) {
        PromisedReply<ServerMessage> result = new PromisedReply<>((ServerMessage) null);
        if (mStore == null) {
            return result;
        }

        final Storage.Message m = mStore.getMessageById(msgDatabaseId);
        if (m != null) {
            if (m.isDeleted()) {
                result = mTinode.delMessage(getName(), m.getSeqId(), m.isDeleted(true));
            } else if (m.isReady()) {
                mStore.msgSyncing(this, m.getDbId(), true);
                result = publish(m.getContent(), m.getHead(), m.getDbId());
            }
        }

        return result;
    }

    public Storage.Message getMessage(int seq) {
        if (mStore == null) {
            return null;
        }
        return mStore.getMessageBySeq(this, seq);
    }

    /**
     * Query topic for data or metadata
     */
    public PromisedReply<ServerMessage> getMeta(MsgGetMeta query) {
        return mTinode.getMeta(getName(), query);
    }

    /**
     * Update topic metadata
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to the server
     */
    public PromisedReply<ServerMessage> setMeta(final MsgSetMeta<DP, DR> meta) {
        return mTinode.setMeta(getName(), meta).thenApply(
                new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        update(result.ctrl, meta);
                        return null;
                    }
                });
    }

    /**
     * Update topic description. Calls {@link #setMeta}.
     *
     * @param desc new description (public, private, default access)
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to the server
     */
    protected PromisedReply<ServerMessage> setDescription(final MetaSetDesc<DP, DR> desc) {
        return setMeta(new MsgSetMeta.Builder<DP, DR>().with(desc).build());
    }

    /**
     * Update topic description. Calls {@link #setMeta}.
     *
     * @param pub  new public info
     * @param priv new private info
     * @param attachments URLs of out-of-band attachments contained in the values of pub (or priv).
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to the server
     */
    public PromisedReply<ServerMessage> setDescription(final DP pub, final DR priv, String[] attachments) {
        MetaSetDesc<DP,DR> meta = new MetaSetDesc<>(pub, priv);
        meta.attachments = attachments;
        return setDescription(meta);
    }

    /**
     * Update topic's default access
     *
     * @param auth default access mode for authenticated users
     * @param anon default access mode for anonymous users
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to the server
     */
    public PromisedReply<ServerMessage> updateDefAcs(String auth, String anon) {
        return setDescription(new MetaSetDesc<>(new Defacs(auth, anon)));
    }

    /**
     * Update subscription. Calls {@link #setMeta}.
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to the server
     */
    protected PromisedReply<ServerMessage> setSubscription(final MetaSetSub sub) {
        return setMeta(new MsgSetMeta.Builder<DP, DR>().with(sub).build());
    }

    /**
     * Update own access mode.
     *
     * @param update string which defines the update. It could be a full value or a change.
     */
    public PromisedReply<ServerMessage> updateMode(final String update) {
        return updateMode(null, update);
    }

    /**
     * Update another user's access mode.
     *
     * @param uid    UID of the user to update.
     * @param update string which defines the update. It could be a full value or a change.
     */
    public PromisedReply<ServerMessage> updateMode(String uid, final String update) {
        final Subscription sub;
        if (uid != null) {
            sub = getSubscription(uid);
            if (uid.equals(mTinode.getMyId())) {
                uid = null;
            }
        } else {
            sub = getSubscription(mTinode.getMyId());
        }

        final boolean self = (uid == null || sub == null);

        if (mDesc.acs == null) {
            mDesc.acs = new Acs();
        }

        final AcsHelper mode = self ? mDesc.acs.getWantHelper() : sub.acs.getGivenHelper();
        if (mode.update(update)) {
            return setSubscription(new MetaSetSub(uid, mode.toString()));
        }
        // The state is unchanged, return resolved promise.
        return new PromisedReply<>((ServerMessage) null);
    }

    /**
     * Update tags.
     * @param tags new tags to send to the server.
     */
    public PromisedReply<ServerMessage> updateTags(String[] tags) {
        return setMeta(new MsgSetMeta.Builder<DP, DR>().with(tags).build());
    }

    /**
     * Update tags.
     * @param tagList comma separated list of new tags to send to the server.
     */
    public PromisedReply<ServerMessage> updateTags(String tagList) {
        return updateTags(mTinode.parseTags(tagList));
    }

    /**
     * Invite user to the topic.
     *
     * @param uid  ID of the user to invite to topic
     * @param mode access mode granted to user
     */
    public PromisedReply<ServerMessage> invite(String uid, String mode) {

        final Subscription<SP, SR> sub;
        if (getSubscription(uid) != null) {
            sub = getSubscription(uid);
            sub.acs.setGiven(mode);
        } else {
            sub = new Subscription<>();
            sub.topic = getName();
            sub.user = uid;
            sub.acs = new Acs();
            sub.acs.setGiven(mode);

            if (mStore != null) {
                mStore.subNew(this, sub);
            }

            User<SP> user = mTinode.getUser(uid);
            sub.pub = user != null ? user.pub : null;

            addSubToCache(sub);
        }

        if (mListener != null) {
            mListener.onMetaSub(sub);
            mListener.onSubsUpdated();
        }

        // Check if topic is already synchronized. If not, don't send the request, it will fail anyway.
        if (isNew()) {
            return new PromisedReply<>(new NotSynchronizedException());
        }

        return setSubscription(new MetaSetSub(uid, mode)).thenApply(
                new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        if (mStore != null) {
                            mStore.subUpdate(Topic.this, sub);
                        }
                        if (mListener != null) {
                            mListener.onMetaSub(sub);
                            mListener.onSubsUpdated();
                        }
                        return null;
                    }
                });
    }

    /**
     * Eject subscriber from topic.
     *
     * @param uid id of the user to unsubscribe from the topic
     * @param ban ban user (set mode.Given = 'N')
     */
    public PromisedReply<ServerMessage> eject(String uid, boolean ban) {
        final Subscription<SP, SR> sub = getSubscription(uid);

        if (sub == null) {
            return new PromisedReply<>(new NotSubscribedException());
        }

        if (ban) {
            // Banning someone means the mode is set to 'N' but subscription is persisted.
            return invite(uid, "N");
        }

        if (isNew()) {
            // This topic is not yet synced.
            if (mStore != null) {
                mStore.subDelete(this, sub);
            }

            if (mListener != null) {
                mListener.onSubsUpdated();
            }

            return new PromisedReply<>(new NotSynchronizedException());
        }

        return mTinode.delSubscription(getName(), uid).thenApply(new PromisedReply.SuccessListener<>() {
            @Override
            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                if (mStore != null) {
                    mStore.subDelete(Topic.this, sub);
                }

                removeSubFromCache(sub);
                if (mListener != null) {
                    mListener.onSubsUpdated();
                }
                return null;
            }
        });
    }

    /**
     * Delete message range.
     *
     * @param hard hard-delete messages
     */
    public PromisedReply<ServerMessage> delMessages(final int fromId, final int toId, final boolean hard) {
        if (mStore != null) {
            mStore.msgMarkToDelete(this, fromId, toId, hard);
        }
        if (mAttached > 0) {
            return mTinode.delMessage(getName(), fromId, toId, hard).thenApply(new PromisedReply.SuccessListener<>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    int delId = result.ctrl.getIntParam("del", 0);
                    setClear(delId);
                    setMaxDel(delId);
                    if (mStore != null && delId > 0) {
                        mStore.msgDelete(Topic.this, delId, fromId, toId);
                    }
                    return null;
                }
            });
        }

        if (mTinode.isConnected()) {
            return new PromisedReply<>(new NotSubscribedException());
        }

        return new PromisedReply<>(new NotConnectedException());
    }

    /**
     * Delete messages with IDs in the provided array of ranges.
     *
     * @param ranges delete messages with ids in these ranges.
     * @param hard hard-delete messages
     */
    public PromisedReply<ServerMessage> delMessages(final MsgRange[] ranges, final boolean hard) {
        if (mStore != null) {
            mStore.msgMarkToDelete(this, ranges, hard);
        }

        if (mAttached > 0) {
            return mTinode.delMessage(getName(), ranges, hard).thenApply(new PromisedReply.SuccessListener<>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    int delId = result.ctrl.getIntParam("del", 0);
                    setClear(delId);
                    setMaxDel(delId);
                    if (mStore != null && delId > 0) {
                        mStore.msgDelete(Topic.this, delId, ranges);
                    }
                    return null;
                }
            });
        }

        if (mTinode.isConnected()) {
            return new PromisedReply<>(new NotSubscribedException());
        }

        return new PromisedReply<>(new NotConnectedException());
    }

    /**
     * Delete messages with id in the provided list.
     *
     * @param list delete messages with IDs from the list.
     * @param hard hard-delete messages
     */
    public PromisedReply<ServerMessage> delMessages(final List<Integer> list, final boolean hard) {
        return delMessages(MsgRange.toRanges(list), hard);
    }

    /**
     * Delete all messages.
     *
     * @param hard hard-delete messages
     */
    public PromisedReply<ServerMessage> delMessages(final boolean hard) {
        return delMessages(0, getSeq() + 1, hard);
    }

    /**
     * Delete topic
     *
     * @param hard hard-delete topic.
     */
    public PromisedReply<ServerMessage> delete(boolean hard) {
        if (isDeleted()) {
            // Already deleted.
            topicLeft(true, 200, "OK");
            mTinode.stopTrackingTopic(getName());
            expunge(true);
            return new PromisedReply<>(null);
        }

        // Delete works even if the topic is not attached.
        return mTinode.delTopic(getName(), hard).thenApply(
                new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        topicLeft(true, result.ctrl.code, result.ctrl.text);
                        mTinode.stopTrackingTopic(getName());
                        expunge(true);
                        return null;
                    }
                });
    }

    /**
     * Let server know the seq id of the most recent received/read message.
     *
     * @param what "read" or "recv" to indicate which action to report
     * @param fromMe indicates if the message is from the current user; update cache but do not send a message.
     * @param seq explicit ID to acknowledge; ignored if <= 0.
     * @return ID of the acknowledged message or 0.
     */
    protected int noteReadRecv(NoteType what, boolean fromMe, int seq) {
        int result = 0;

        try {
            switch (what) {
                case RECV:
                    if (mDesc.recv < mDesc.seq) {
                        if (!fromMe) {
                            mTinode.noteRecv(getName(), mDesc.seq);
                        }
                        result = mDesc.recv = mDesc.seq;
                    }
                    break;

                case READ:
                    if (mDesc.read < mDesc.seq || seq > 0) {
                        if (!fromMe) {
                            mTinode.noteRead(getName(), seq > 0 ? seq : mDesc.seq);
                        }

                        if (seq <= 0) {
                            result = mDesc.read = mDesc.seq;
                        } else if (seq > mDesc.read) {
                            result = mDesc.read = seq;
                        }
                    }
                    break;
            }
        } catch (NotConnectedException ignored) {
        }

        return result;
    }

    /**
     * Notify the server that the client read the last message.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int noteRead() {
        return noteRead(false, -1);
    }

    @SuppressWarnings("UnusedReturnValue")
    public int noteRead(int seq) {
        return noteRead(false, seq);
    }

    public int noteRead(boolean fromMe, int seq) {
        int result = noteReadRecv(NoteType.READ, fromMe, seq);
        if (mStore != null && result > 0) {
            mStore.setRead(this, result);
        }
        return result;
    }

    /**
     * Notify the server that the messages is stored on the client
     */
    @SuppressWarnings("UnusedReturnValue")
    public int noteRecv() {
        return noteRecv(false);
    }

    protected int noteRecv(boolean fromMe) {
        int result = noteReadRecv(NoteType.RECV, fromMe, -1);
        if (mStore != null && result > 0) {
            mStore.setRecv(this, result);
        }
        return result;
    }

    /**
     * Send a recording notification to server. Ensure we do not sent too many.
     */
    public void noteRecording(boolean audioOnly) {
        long now = System.currentTimeMillis();
        if (now - mLastKeyPress > Tinode.getKeyPressDelay()) {
            try {
                mTinode.noteRecording(getName(), audioOnly);
                mLastKeyPress = now;
            } catch (NotConnectedException ignored) {
            }
        }
    }

    /**
     * Send a key press notification to server. Ensure we do not sent too many.
     */
    public void noteKeyPress() {
        long now = System.currentTimeMillis();
        if (now - mLastKeyPress > Tinode.getKeyPressDelay()) {
            try {
                mTinode.noteKeyPress(getName());
                mLastKeyPress = now;
            } catch (NotConnectedException ignored) {
            }
        }
    }

    /**
     * Send a generic video call notification to server.
     * @param event is a video call event to notify the other call party about (e.g. "accept" or "hang-up").
     * @param seq call message ID.
     * @param payload is a JSON payload associated with the event.
     */
    protected void videoCall(String event, int seq, Object payload) {
        mTinode.videoCall(getName(), seq, event, payload);
    }

    /**
     * Send a video call accept notification to server.
     * @param seq call message ID.
     */
    public void videoCallAccept(int seq) {
        videoCall("accept", seq, null);
    }

    /**
     * Video call ICE exchange notification to the server.
     * @param seq call message ID.
     */
    public void videoCallAnswer(int seq, Object payload) {
        videoCall("answer", seq, payload);
    }

    /**
     * Send a video call hang up notification to server.
     * @param seq call message ID.
     */
    public void videoCallHangUp(int seq) {
        videoCall("hang-up", seq, null);
    }

    /**
     * Video call ICE exchange notification to the server.
     * @param seq call message ID.
     * @param payload is a JSON payload associated with the event.
     */
    public void videoCallICECandidate(int seq, Object payload) {
        videoCall("ice-candidate", seq, payload);
    }

    /**
     * Video call ICE exchange notification to the server.
     * @param seq call message ID.
     */
    public void videoCallOffer(int seq, Object payload) {
        videoCall("offer", seq, payload);
    }

    /**
     * Send a notification that the call invite was received but not answered yet.
     * @param seq call message ID.
     */
    public void videoCallRinging(int seq) {
        videoCall("ringing", seq, null);
    }

    public String getName() {
        return mName;
    }

    protected void setName(String name) {
        mName = name;
    }

    @SuppressWarnings("WeakerAccess, UnusedReturnValue, unchecked")
    protected int loadSubs() {
        Collection<Subscription> subs = mStore != null ? mStore.getSubscriptions(this) : null;
        if (subs == null || subs.isEmpty()) {
            return 0;
        }

        for (Subscription sub : subs) {
            if (mSubsUpdated == null || mSubsUpdated.before(sub.updated)) {
                mSubsUpdated = sub.updated;
            }
            addSubToCache(sub);
        }
        return mSubs.size();
    }

    /**
     * Add subscription to cache. Needs to be overriden in MeTopic because it keeps subs indexed by topic.
     *
     * @param sub subscription to add to cache
     */
    protected void addSubToCache(Subscription<SP, SR> sub) {
        if (mSubs == null) {
            mSubs = new HashMap<>();
        }

        mSubs.put(sub.user, sub);
    }

    /**
     * Remove subscription to cache. Needs to be overriden in MeTopic because it keeps subs indexed by topic.
     *
     * @param sub subscription to remove from cache
     */
    protected void removeSubFromCache(Subscription<SP, SR> sub) {
        if (mSubs != null) {
            mSubs.remove(sub.user);
        }
    }

    public Subscription<SP, SR> getSubscription(String key) {
        if (mSubs == null) {
            loadSubs();
        }
        return mSubs != null ? mSubs.get(key) : null;
    }

    public Collection<Subscription<SP, SR>> getSubscriptions() {
        if (mSubs == null) {
            loadSubs();
        }
        return mSubs != null ? mSubs.values() : null;
    }

    // Check if topic is subscribed/online.
    public boolean isAttached() {
        return mAttached > 0;
    }

    // Check if topic is valid;
    public boolean isValid() {
        return mStore != null;
    }

    /**
     * Tells how many topic subscribers have reported the message as received.
     *
     * @param seq sequence id of the message to test
     * @return count of recepients who claim to have received the message
     */
    public int msgRecvCount(int seq) {
        int count = 0;
        if (seq > 0) {
            String me = mTinode.getMyId();
            Collection<Subscription<SP, SR>> subs = getSubscriptions();
            if (subs != null) {
                for (Subscription sub : subs) {
                    if (!sub.user.equals(me) && sub.recv >= seq) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Tells how many topic subscribers have reported the message as read.
     *
     * @param seq sequence id of the message to test.
     * @return count of recipients who claim to have read the message.
     */
    public int msgReadCount(int seq) {
        int count = 0;
        String me = mTinode.getMyId();
        if (seq > 0 && me != null) {
            Collection<Subscription<SP, SR>> subs = getSubscriptions();
            if (subs != null) {
                for (Subscription sub : subs) {
                    if (!me.equals(sub.user) && sub.read >= seq) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Get type of the topic.
     *
     * @return topic type.
     */
    public TopicType getTopicType() {
        return getTopicTypeByName(mName);
    }

    /**
     * Check if topic is 'me' type.
     *
     * @return true if topic is 'me' type, false otherwise.
     */
    public boolean isMeType() {
        return getTopicType() == TopicType.ME;
    }

    /**
     * Check if topic is 'p2p' type.
     *
     * @return true if topic is 'p2p' type, false otherwise.
     */
    public boolean isP2PType() {
        return getTopicType() == TopicType.P2P;
    }

    /**
     * Check if topic is 'slf' type.
     *
     * @return true if topic is 'slf' type, false otherwise.
     */
    public boolean isSlfType() {
        return getTopicType() == TopicType.SLF;
    }

    /**
     * Check if topic is a communication topic, i.e. a 'slf', 'p2p' or 'grp' type.
     *
     * @return true if topic is user-visible, like 'p2p' or 'grp', false otherwise.
     */
    public boolean isUserType() {
        return switch (getTopicType()) {
            case SLF, P2P, GRP -> true;
            default -> false;
        };
    }

    /**
     * Check if topic is 'fnd' type.
     *
     * @return true if topic is 'fnd' type, false otherwise.
     */
    public boolean isFndType() {
        return getTopicType() == TopicType.FND;
    }

    /**
     * Check if topic is 'grp' type.
     *
     * @return true if topic is 'grp' type, false otherwise.
     */
    public boolean isGrpType() {
        return getTopicType() == TopicType.GRP;
    }

    /**
     * Check if topic is not yet synchronized to the server.
     *
     * @return true is topic is new (i.e. no name is yet assigned by the server)
     */
    public boolean isNew() {
        return isNew(mName);
    }

    /**
     * Called when the topic receives leave() confirmation. Overriden in 'me'.
     *
     * @param unsub  - not just detached but also unsubscribed
     * @param code   result code, always 200
     * @param reason usually "OK"
     */
    protected void topicLeft(boolean unsub, int code, String reason) {
        if (mAttached > 0) {
            mAttached = 0;

            // Don't change topic online status here. Change it in the 'me' topic

            if (mListener != null) {
                mListener.onLeave(unsub, code, reason);
            }
        }
    }

    protected void routeMeta(MsgServerMeta<DP, DR, SP, SR> meta) {
        if (meta.desc != null) {
            routeMetaDesc(meta);
        }
        if (meta.sub != null) {
            if (mSubsUpdated == null || meta.ts.after(mSubsUpdated)) {
                mSubsUpdated = meta.ts;
            }
            routeMetaSub(meta);
        }
        if (meta.del != null) {
            routeMetaDel(meta.del.clear, meta.del.delseq);
        }
        if (meta.tags != null) {
            routeMetaTags(meta.tags);
        }
        if (meta.aux != null) {
            routeMetaAux(meta.aux);
        }
        if (mListener != null) {
            mListener.onMeta(meta);
        }
    }

    protected void routeMetaDesc(MsgServerMeta<DP, DR, SP, SR> meta) {
        update(meta.desc);

        if (getTopicType() == TopicType.P2P) {
            mTinode.updateUser(getName(), meta.desc);
        }

        if (mListener != null) {
            mListener.onMetaDesc(meta.desc);
        }
    }

    protected void processSub(Subscription<SP, SR> newsub) {
        // In case of a generic (non-'me') topic, meta.sub contains topic subscribers.
        // I.e. sub.user is set, but sub.topic is equal to current topic.

        Subscription<SP, SR> sub;

        if (newsub.deleted != null) {
            if (mStore != null) {
                mStore.subDelete(this, newsub);
            }
            removeSubFromCache(newsub);

            sub = newsub;
        } else {
            sub = getSubscription(newsub.user);
            if (sub != null) {
                sub.merge(newsub);
                if (mStore != null) {
                    mStore.subUpdate(this, sub);
                }
            } else {
                sub = newsub;
                addSubToCache(sub);
                if (mStore != null) {
                    mStore.subAdd(this, sub);
                }
            }

            mTinode.updateUser(sub);

            // If this is a change to user's own permissions, update topic too.
            if (mTinode.isMe(sub.user) && sub.acs != null) {
                setAccessMode(sub.acs);
                if (mStore != null) {
                    mStore.topicUpdate(this);
                }

                // Notify listener that topic has updated.
                if (mListener != null) {
                    mListener.onContUpdated(sub.user);
                }
            }
        }

        if (mListener != null) {
            mListener.onMetaSub(sub);
        }
    }

    protected void routeMetaSub(MsgServerMeta<DP, DR, SP, SR> meta) {
        for (Subscription<SP, SR> newsub : meta.sub) {
            processSub(newsub);
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }

    protected void routeMetaDel(int clear, MsgRange[] delseq) {
        if (mStore != null) {
            mStore.msgDelete(this, clear, delseq);
        }
        setMaxDel(clear);

        if (mListener != null) {
            mListener.onData(null);
        }
    }

    protected void routeMetaTags(String[] tags) {
        update(tags);

        if (mListener != null) {
            mListener.onMetaTags(tags);
        }
    }

    protected void routeMetaAux(Map<String, Object> aux) {
        update(aux);

        if (mListener != null) {
            mListener.onMetaAux(aux);
        }
    }

    protected void routeData(MsgServerData data) {
        if (mStore != null) {
            Storage.Message msg = mStore.msgReceived(this, getSubscription(data.from), data);
            if (msg != null) {
                mTinode.setLastMessage(getName(), msg);
                noteRecv(mTinode.isMe(data.from));
            }
        } else {
            noteRecv(mTinode.isMe(data.from));
        }
        setSeq(data.seq);
        setTouched(data.ts);

        // Use data message from another person to mark messages as read by him.
        if (data.from != null && !mTinode.isMe(data.from) && !isChannel(getName())) {
            MsgServerInfo info = new MsgServerInfo();
            info.what = Tinode.NOTE_READ;
            info.from = data.from;
            info.seq = data.seq;
            // Mark messages as read by the sender.
            routeInfo(info);
        }

        if (mListener != null) {
            mListener.onData(data);
        }

        // Call notification listener on 'me' to refresh chat list, if appropriate.
        MeTopic me = mTinode.getMeTopic();
        if (me != null) {
            me.setMsgReadRecv(getName(), "", 0);
        }
    }

    protected void allMessagesReceived(Integer count) {
        if (mListener != null) {
            mListener.onAllMessagesReceived(count);
        }
    }

    protected void allSubsReceived() {
        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }
    protected void routePres(MsgServerPres pres) {
        MsgServerPres.What what = MsgServerPres.parseWhat(pres.what);
        Subscription<SP, SR> sub;
        switch (what) {
            case ON:
            case OFF:
                sub = getSubscription(pres.src);
                if (sub != null) {
                    sub.online = (what == MsgServerPres.What.ON);
                }
                break;

            case DEL:
                routeMetaDel(pres.clear, pres.delseq);
                break;

            case TERM:
                topicLeft(false, 500, "term");
                break;

            case UPD:
                // A topic subscriber has updated his description.
                if (pres.src != null && mTinode.getTopic(pres.src) == null) {
                    // Issue {get sub} only if the current user has no relationship with the updated user.
                    // Otherwise 'me' will issue a {get desc} request.
                    getMeta(getMetaGetBuilder().withSub(pres.src).build());
                }
                break;
            case ACS:
                String userId = pres.src != null ? pres.src : mTinode.getMyId();
                sub = getSubscription(userId);
                if (sub == null) {
                    Acs acs = new Acs();
                    acs.update(pres.dacs);
                    if (acs.isModeDefined()) {
                        sub = new Subscription<>();
                        sub.topic = getName();
                        sub.user = userId;
                        sub.acs = acs;
                        sub.updated = new Date();
                        User<SP> user = mTinode.getUser(userId);
                        if (user == null) {
                            getMeta(getMetaGetBuilder().withSub(userId).build());
                        } else {
                            sub.pub = user.pub;
                        }
                    } else {
                        Log.w(TAG, "Invalid access mode update '" + pres.dacs.toString() + "'");
                    }
                } else {
                    // Update to an existing subscription.
                    sub.updateAccessMode(pres.dacs);
                }

                if (sub != null) {
                    processSub(sub);
                }
                break;
            case MSG:
            case READ:
            case RECV:
                // Explicitly ignore message-related notifications. They are handled in the 'me' topic.
                break;
            case AUX:
                // Auxiliary data update.
                getMeta(getMetaGetBuilder().withAux().build());
                break;
            default:
                Log.d(TAG, "Unhandled presence update '" + pres.what + "' in '" + getName() + "'");
        }

        if (mListener != null) {
            mListener.onPres(pres);
        }
    }

    protected void setReadRecvByRemote(final String userId, final String what, final int seq) {
        Subscription<SP, SR> sub = getSubscription(userId);
        if (sub == null) {
            return;
        }
        switch (what) {
            case Tinode.NOTE_RECV:
                sub.recv = seq;
                if (mStore != null) {
                    mStore.msgRecvByRemote(sub, seq);
                }
                break;
            case Tinode.NOTE_READ:
                sub.read = seq;
                if (sub.recv < sub.read) {
                    sub.recv = sub.read;
                    if (mStore != null) {
                        mStore.msgRecvByRemote(sub, seq);
                    }
                }
                if (mStore != null) {
                    mStore.msgReadByRemote(sub, seq);
                }
                break;
            default:
                break;
        }
    }

    protected void routeInfo(MsgServerInfo info) {
        switch (info.what) {
            case Tinode.NOTE_KP:
            case Tinode.NOTE_REC_AUDIO:
            case Tinode.NOTE_REC_VIDEO:
            case Tinode.NOTE_CALL:
                break;

            case Tinode.NOTE_READ:
            case Tinode.NOTE_RECV:
                setReadRecvByRemote(info.from, info.what, info.seq);

                // If this is an update from the current user, update the contact with the new count too.
                if (mTinode.isMe(info.from)) {
                    MeTopic me = mTinode.getMeTopic();
                    if (me != null) {
                        me.setMsgReadRecv(getName(), info.what, info.seq);
                    }
                }
                break;
            default:
                // Unknown value
        }

        if (mListener != null) {
            mListener.onInfo(info);
        }
    }

    @Override
    public Payload getLocal() {
        return mLocal;
    }

    @Override
    public void setLocal(Payload value) {
        mLocal = value;
    }

    public synchronized void setListener(Listener<DP, DR, SP, SR> l) {
        mListener = l;
    }

    public enum TopicType {
        ME(0x01), FND(0x02), GRP(0x04), P2P(0x08), SYS(0x10), SLF(0x20),
        USER(0x04 | 0x08 | 0x20), INTERNAL(0x01 | 0x02 | 0x10), UNKNOWN(0x00),
        ANY(0x01 | 0x02 | 0x04 | 0x08 | 0x10 | 0x20);

        private final int val;

        TopicType(int val) {
            this.val = val;
        }

        public int val() {
            return val;
        }

        public boolean match(TopicType v2) {
            return (val & v2.val) != 0;
        }
    }

    protected enum NoteType {READ, RECV}

    public interface Listener<DP, DR, SP, SR> {

        default void onSubscribe(int code, String text) {
        }

        default void onLeave(boolean unsub, int code, String text) {
        }

        /**
         * Process {data} message.
         *
         * @param data data packet
         */
        default void onData(MsgServerData data) {
        }

        /**
         * All requested data messages received.
         */
        default void onAllMessagesReceived(Integer count) {
        }

        /**
         * {info} message received
         */
        default void onInfo(MsgServerInfo info) {
        }

        /**
         * {meta} message received
         */
        default void onMeta(MsgServerMeta<DP, DR, SP, SR> meta) {
        }

        /**
         * {meta what="sub"} message received, and this is one of the subs
         */
        default void onMetaSub(Subscription<SP, SR> sub) {
        }

        /**
         * {meta what="desc"} message received
         */
        default void onMetaDesc(Description<DP, DR> desc) {
        }

        /**
         * {meta what="tags"} message received
         */
        default void onMetaTags(String[] tags) {
        }

        /**
         * {meta what="aux"} message received
         */
        default void onMetaAux(Map<String,Object> aux) {
        }

        /**
         * {meta what="sub"} message received and all subs were processed
         */
        default void onSubsUpdated() {
        }

        /**
         * {pres} received
         */
        default void onPres(MsgServerPres pres) {
        }

        /**
         * {pres what="on|off"} is received
         */
        default void onOnline(boolean online) {
        }

        /** Called when subscription is updated. */
        default void onContUpdated(String contact) {
        }
    }

    /**
     * Helper class for generating query parameters for {sub get} and {get} packets.
     */
    public static class MetaGetBuilder {
        protected final Topic topic;
        protected MsgGetMeta meta;

        MetaGetBuilder(Topic parent) {
            meta = new MsgGetMeta();
            topic = parent;
        }

        /**
         * Add query parameters to fetch messages within explicit limits. Any/all parameters can be null.
         *
         * @param since  messages newer than this;
         * @param before older than this
         * @param limit  maximum number of messages to fetch
         */
        public MetaGetBuilder withData(Integer since, Integer before, Integer limit) {
            meta.setData(since, before, limit);
            return this;
        }

        /**
         * Add query parameters to fetch messages within given ranges.
         *
         * @param ranges  message ranges to fetch;
         * @param limit  maximum number of messages to fetch
         */
        public MetaGetBuilder withData(MsgRange[] ranges, Integer limit) {
            meta.setData(ranges, limit);
            return this;
        }

        /**
         * Add query parameters to fetch messages newer than the latest saved message.
         */
        public MetaGetBuilder withLaterData() {
            return withLaterData(Tinode.DEFAULT_MESSAGE_PAGE);
        }

        /**
         * Add query parameters to fetch messages newer than the latest saved message.
         *
         * @param limit number of messages to fetch
         */
        public MetaGetBuilder withLaterData(Integer limit) {
            MsgRange r = topic.getCachedMessagesRange();
            if (r != null) {
                return withData(r.hi, null, limit);
            }
            return withData(null, null, limit);
        }

        /**
         * Add query parameters to fetch messages older than the earliest saved message.
         *
         * @param limit number of messages to fetch
         */
        public MetaGetBuilder withEarlierData(Integer limit) {
            MsgRange r = topic.getCachedMessagesRange();
            if (r != null) {
                return r.low > 1 ? withData(null, r.low, limit) : this;
            }
            return withData(0, null, limit);
        }

        /**
         * Default query - same as withLaterData with default number of
         * messages to fetch.
         */
        public MetaGetBuilder withData() {
            return withLaterData(Tinode.DEFAULT_MESSAGE_PAGE);
        }

        public MetaGetBuilder withDesc(Date ims) {
            meta.setDesc(ims);
            return this;
        }

        /**
         * Get description if it was updated since the last recorded update.
         * @return <code>this</code>
         */
        public MetaGetBuilder withDesc() {
            return withDesc(topic.getUpdated());
        }

        public MetaGetBuilder withSub(String userOrTopic, Date ims, Integer limit) {
            if (topic.getTopicType() == TopicType.ME) {
                meta.setSubTopic(userOrTopic, ims, limit);
            } else {
                meta.setSubUser(userOrTopic, ims, limit);
            }
            return this;
        }

        public MetaGetBuilder withSub(Date ims, Integer limit) {
            return withSub(null, ims, limit);
        }

        public MetaGetBuilder withSub() {
            return withSub(null, topic.getSubsUpdated(), null);
        }

        /**
         * Get subscriptions updated since the last recorded update.
         * @return <code>this</code>
         */
        public MetaGetBuilder withSub(String userOrTopic) {
            return withSub(userOrTopic, topic.getSubsUpdated(), null);
        }

        public MetaGetBuilder withDel(Integer since, Integer limit) {
            meta.setDel(since, limit);
            return this;
        }

        public MetaGetBuilder withLaterDel(Integer limit) {
            int del_id = topic.getMaxDel();
            return withDel(del_id > 0 ? del_id + 1 : null, limit);
        }

        public MetaGetBuilder withDel() {
            return withLaterDel(null);
        }

        public MetaGetBuilder withTags() {
            meta.setTags();
            return this;
        }

        public MetaGetBuilder withAux() {
            meta.setAux();
            return this;
        }

        public MetaGetBuilder reset() {
            meta = new MsgGetMeta();
            return this;
        }

        public MsgGetMeta build() {
            if (meta.isEmpty()) {
                return null;
            }
            return meta;
        }
    }
}
