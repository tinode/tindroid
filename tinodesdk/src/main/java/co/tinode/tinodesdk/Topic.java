package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

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
    private static final String TAG = "tinodesdk.Topic";
    protected Tinode mTinode;
    protected String mName;
    // The bulk of topic data
    protected Description<DP, DR> mDesc;
    // Cache of topic subscribers indexed by userID
    protected HashMap<String, Subscription<SP, SR>> mSubs = null;
    // Timestamp of the last update to subscriptions. Default: Oct 25, 2014 05:06:02 UTC, incidentally equal
    // to the first few digits of sqrt(2)
    protected Date mSubsUpdated = null;

    // Server-provided values:
    // Tags: user and topic discovery
    protected String[] mTags;
    // The topic is subscribed/online.
    protected boolean mAttached = false;
    protected Listener<DP, DR, SP, SR> mListener = null;
    // Timestamp of the last key press that the server was notified of, milliseconds
    protected long mLastKeyPress = 0;
    protected boolean mOnline = false;
    protected LastSeen mLastSeen = null;
    // ID of the last applied delete transaction. Different from 'clear' which is the highest known.
    protected int mMaxDel = 0;
    /**
     * The mStore is set by Tinode when the topic calls {@link Tinode#startTrackingTopic(Topic)}
     */
    Storage mStore = null;
    private Payload mLocal = null;

    Topic(Tinode tinode, String name) {
        mTinode = tinode;
        if (name == null) {
            name = Tinode.TOPIC_NEW + tinode.nextUniqueString();
        }
        setName(name);
        mDesc = new Description<>();

        // Tinode could be null if the topic does not need to be tracked, i.e.
        // loaded by Firebase in response to a push notification.
        if (mTinode != null) {
            mTinode.startTrackingTopic(this);
        }
    }

    protected Topic(Tinode tinode, Subscription<SP, SR> sub) {
        this(tinode, sub.topic);
        mDesc.merge(sub);
        if (sub.online != null) {
            mOnline = sub.online;
        }
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
    protected Topic(Tinode tinode, Listener<DP, DR, SP, SR> l) {
        this(tinode, null, l);
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

    public static TopicType getTopicTypeByName(String name) {
        if (name != null) {
            if (name.equals(Tinode.TOPIC_ME)) {
                return TopicType.ME;
            } else if (name.equals(Tinode.TOPIC_SYS)) {
                return TopicType.SYS;
            } else if (name.equals(Tinode.TOPIC_FND)) {
                return TopicType.FND;
            } else if (name.startsWith(Tinode.TOPIC_GRP_PREFIX) || name.startsWith(Tinode.TOPIC_NEW)) {
                return TopicType.GRP;
            } else if (name.startsWith(Tinode.TOPIC_USR_PREFIX)) {
                return TopicType.P2P;
            }
        }
        return TopicType.UNKNOWN;
    }

    public static boolean getIsNewByName(String name) {
        return name.startsWith(Tinode.TOPIC_NEW);  // "newRANDOM" when the topic was locally initialized but not yet
        // synced with the server
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
    protected void update(Subscription<SP, SR> sub) {
        boolean changed;

        if (mLastSeen == null) {
            changed = true;
            mLastSeen = sub.seen;
        } else {
            changed = mLastSeen.merge(sub.seen);
        }

        if (mDesc.merge(sub)) {
            changed = true;
        }

        if (changed && mStore != null) {
            mStore.topicUpdate(this);
        }

        if (sub.online != null) {
            mOnline = sub.online;
        }
    }

    /**
     * Update topic parameters from a Description object.
     *
     * @param desc updated topic parameters
     */
    protected void update(Description<DP, DR> desc) {
        if (mDesc.merge(desc) && mStore != null) {
            mStore.topicUpdate(this);
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

            if (changed && mStore != null) {
                mStore.topicUpdate(this);
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
        if (mDesc.merge(desc) && mStore != null) {
            mStore.topicUpdate(this);
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
        if (meta.desc != null) {
            update(meta.desc);
            if (mListener != null) {
                mListener.onMetaDesc(mDesc);
            }
        }

        if (meta.sub != null) {
            update(ctrl.params, meta.sub);
            if (mListener != null) {
                if (meta.sub.user == null) {
                    mListener.onMetaDesc(mDesc);
                }
                mListener.onSubsUpdated();
            }
        }

        if (meta.tags != null) {
            update(meta.tags);
            if (mListener != null) {
                mListener.onMetaTags(mTags);
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
        mDesc.touched = maxDate(mDesc.updated, touched);
    }

    @Override
    public int compareTo(Topic t) {
        if (t == null || t.mDesc.touched == null) {
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
    protected void setSeqAndFetch(int seq) {
        if (seq > mDesc.seq) {
            int limit = seq - mDesc.seq;
            mDesc.seq = seq;
            // Fetch only if not attached. If it's attached it will be fetched elsewhere.
            if (!isAttached()) {
                try {
                    // Fully asynchronous fire and forget.
                    subscribe(null, getMetaGetBuilder().withLaterData(limit).build(), true);
                    leave();
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
        return mTags;
    }

    public void setTags(String[] tags) {
        mTags = tags;
    }

    public DP getPub() {
        return mDesc.pub;
    }

    public void setPub(DP pub) {
        mDesc.pub = pub;
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


    public MsgRange getCachedMessagesRange() {
        return mStore == null ? null : mStore.getCachedMessagesRange(this);
    }

    public MsgRange getMissingMessageRange() {
        if (mStore == null) {
            return null;
        }
        // If topic has messages, fetch the next missing message range (could be null)
        return mStore.getNextMissingRange(this);
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
        return mDesc.acs.update(ac);
    }

    /**
     * Check if user has an Approver (A) permission.
     *
     * @return true if the user has the permission.
     */
    public boolean isAdmin() {
        return mDesc.acs != null && mDesc.acs.isAdmin();
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

    public boolean isOwner() {
        return mDesc.acs != null && mDesc.acs.isOwner();
    }

    public boolean isReader() {
        return mDesc.acs != null && mDesc.acs.isReader();
    }

    public boolean isWriter() {
        return mDesc.acs != null && mDesc.acs.isWriter();
    }

    public boolean isJoiner() {
        return mDesc.acs != null && mDesc.acs.isJoiner();
    }

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

    public boolean getOnline() {
        return mOnline;
    }

    protected void setOnline(boolean online) {
        if (online != mOnline) {
            mOnline = online;
            if (mListener != null) {
                mListener.onOnline(mOnline);
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

    protected void persist(boolean on) {
        if (mStore != null) {
            if (on) {
                if (!isPersisted()) {
                    mStore.topicAdd(this);
                }
            } else {
                mStore.topicDelete(this);
            }
        }
    }

    protected void setLastSeen(Date when, String ua) {
        mLastSeen = new LastSeen(when, ua);
    }

    protected void setLastSeen(Date when) {
        if (mLastSeen != null) {
            mLastSeen.when = when;
        } else {
            mLastSeen = new LastSeen(when);
        }
    }

    /**
     * Subscribe to topic.
     */
    public PromisedReply<ServerMessage> subscribe() {
        MsgSetMeta<DP, DR> mset = null;
        MsgGetMeta mget;
        if (isNew()) {
            mset = new MsgSetMeta<>(new MetaSetDesc<>(mDesc.pub, mDesc.priv), null, mTags, null);
            mget = null;
        } else {
            MetaGetBuilder mgb = getMetaGetBuilder()
                    .withDesc().withData().withSub();
            if (isMeType() || (isGrpType() && isOwner())) {
                // Ask for tags only if it's a 'me' topic or the user is the owner of a 'grp' topic.
                mgb = mgb.withTags();
            }
            mget = mgb.build();
        }
        return subscribe(mset, mget);
    }

    /**
     * Service subscription to topic with explicit parameters.
     *
     * @param set values to be assigned to topic on successful subscription.
     * @param get query topic for data.
     *
     * @throws NotConnectedException      if there is no live connection to the server
     * @throws AlreadySubscribedException if the client is already subscribed to the given topic
     */
    public PromisedReply<ServerMessage> subscribe(MsgSetMeta<DP, DR> set, MsgGetMeta get) {
        return subscribe(set, get, false);
    }

    /**
     * Subscribe to topic with parameters, optionally in background.
     *
     * @throws NotConnectedException      if there is no live connection to the server
     * @throws AlreadySubscribedException if the client is already subscribed to the given topic
     */
    @SuppressWarnings("unchecked")
    public PromisedReply<ServerMessage> subscribe(MsgSetMeta<DP, DR> set, MsgGetMeta get, boolean background) {
        if (mAttached) {
            if (set == null && get == null) {
                // If the topic is already attached and the user does not attempt to set or
                // get any data, just return resolved promise.
                return new PromisedReply<>((ServerMessage) null);
            }
            return new PromisedReply<>(new AlreadySubscribedException());
        }

        final String topicName = getName();
        if (!isPersisted()) {
            persist(true);
        }

        return mTinode.subscribe(topicName, set, get, background).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                        if (!mAttached) {
                            mAttached = true;
                            if (msg.ctrl != null) {
                                if (msg.ctrl.params != null) {
                                    mDesc.acs = new Acs((Map<String, String>) msg.ctrl.params.get("acs"));
                                    if (isNew()) {
                                        setUpdated(msg.ctrl.ts);
                                        setName(msg.ctrl.topic);
                                        mTinode.changeTopicName(Topic.this, topicName);
                                    }

                                    if (mStore != null) {
                                        mStore.topicUpdate(Topic.this);
                                    }
                                }

                                if (mListener != null) {
                                    mListener.onSubscribe(msg.ctrl.code, msg.ctrl.text);
                                }
                            }
                        }
                        return null;
                    }
                }, new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                        if (isNew() && err instanceof ServerResponseException) {
                            ServerResponseException sre = (ServerResponseException) err;
                            if (sre.getCode() >= ServerMessage.STATUS_BAD_REQUEST &&
                                    sre.getCode() < ServerMessage.STATUS_INTERNAL_SERVER_ERROR) {
                                mTinode.stopTrackingTopic(topicName);
                                persist(false);
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
        if (mAttached) {
            return mTinode.leave(getName(), unsub).thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            topicLeft(unsub, result.ctrl.code, result.ctrl.text);
                            if (unsub) {
                                mTinode.stopTrackingTopic(getName());
                                persist(false);
                            }
                            return null;
                        }
                    });
        } else if (!unsub) {
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
                }
            }
        }
    }

    protected PromisedReply<ServerMessage> publish(final Drafty content, Map<String, Object> head, final long msgId) {
        if (content.isPlain() && head != null) {
            // Plain text content should not be sent with the "mine" header. Clear it.
            head.remove("mime");
        }
        return mTinode.publish(getName(), content.isPlain() ? content.toString() : content, head).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        processDelivery(result.ctrl, msgId);
                        return null;
                    }
                },
                new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                        if (mStore != null) {
                            mStore.msgSyncing(Topic.this, msgId, false);
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
        final Map<String, Object> head = !content.isPlain() ? Tinode.draftyHeadersFor(content) : null;
        final long id;
        if (mStore != null) {
            id = mStore.msgSend(this, content, head);
        } else {
            id = -1;
        }

        if (mAttached) {
            return publish(content, head, id);
        } else {
            return subscribe()
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            return publish(content, head, id);
                        }
                    })
                    .thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                            if (mStore != null) {
                                mStore.msgSyncing(Topic.this, id, false);
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

        try {
            while (toSend.hasNext()) {
                Storage.Message msg = toSend.next();
                final long msgId = msg.getId();
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

        final Storage.Message m = mStore.getMessageById(this, msgDatabaseId);
        if (m != null) {
            if (m.isDeleted()) {
                result = mTinode.delMessage(getName(), m.getSeqId(), m.isDeleted(true));
            } else if (m.isReady()) {
                mStore.msgSyncing(this, m.getId(), true);
                result = publish(m.getContent(), m.getHead(), m.getId());
            }
        }

        return result;
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
                new PromisedReply.SuccessListener<ServerMessage>() {
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
        return setMeta(new MsgSetMeta<>(desc));
    }

    /**
     * Update topic description. Calls {@link #setMeta}.
     *
     * @param pub  new public info
     * @param priv new private info
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to the server
     */
    public PromisedReply<ServerMessage> setDescription(final DP pub, final DR priv) {
        return setDescription(new MetaSetDesc<>(pub, priv));
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
        return setDescription(new MetaSetDesc<DP, DR>(new Defacs(auth, anon)));
    }

    /**
     * Update subscription. Calls {@link #setMeta}.
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException  if there is no connection to the server
     */
    protected PromisedReply<ServerMessage> setSubscription(final MetaSetSub sub) {
        return setMeta(new MsgSetMeta<DP, DR>(sub));
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
                new PromisedReply.SuccessListener<ServerMessage>() {
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

        return mTinode.delSubscription(getName(), uid).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
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
        if (mAttached) {
            return mTinode.delMessage(getName(), fromId, toId, hard).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
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
     * Delete messages with id in the provided list.
     *
     * @param ranges delete messages with ids in these ranges.
     * @param hard hard-delete messages
     */
    public PromisedReply<ServerMessage> delMessages(final MsgRange[] ranges, final boolean hard) {
        if (mStore != null) {
            mStore.msgMarkToDelete(this, ranges, hard);
        }

        if (mAttached) {
            return mTinode.delMessage(getName(), ranges, hard).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
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
        // Delete works even if the topic is not attached.
        return mTinode.delTopic(getName(), hard).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        topicLeft(true, result.ctrl.code, result.ctrl.text);
                        mTinode.stopTrackingTopic(getName());
                        persist(false);
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

    public String getName() {
        return mName;
    }

    protected void setName(String name) {
        mName = name;
    }

    @SuppressWarnings("WeakerAccess, UnusedReturnValue, unchecked")
    protected int loadSubs() {
        Collection<Subscription> subs = mStore != null ? mStore.getSubscriptions(this) : null;
        if (subs == null) {
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
        return mAttached;
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
     * @return count of recepients who claim to have read the message.
     */
    public int msgReadCount(int seq) {
        int count = 0;
        if (seq > 0) {
            String me = mTinode.getMyId();
            Collection<Subscription<SP, SR>> subs = getSubscriptions();
            if (subs != null) {
                for (Subscription sub : subs) {
                    if (!sub.user.equals(me) && sub.read >= seq) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public TopicType getTopicType() {
        return getTopicTypeByName(mName);
    }

    public boolean isMeType() {
        return getTopicType() == TopicType.ME;
    }

    public boolean isP2PType() {
        return getTopicType() == TopicType.P2P;
    }

    public boolean isFndType() {
        return getTopicType() == TopicType.FND;
    }

    public boolean isGrpType() {
        return getTopicType() == TopicType.GRP;
    }

    /**
     * Check if topic is not yet synchronized to the server.
     *
     * @return true is topic is new (i.e. no name is yet assigned by the server)
     */
    public boolean isNew() {
        return getIsNewByName(mName);
    }

    /**
     * Called when the topic receives leave() confirmation. Overriden in 'me'.
     *
     * @param unsub  - not just detached but also unsubscribed
     * @param code   result code, always 200
     * @param reason usually "OK"
     */
    protected void topicLeft(boolean unsub, int code, String reason) {
        if (mAttached) {
            mAttached = false;

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

    protected void routeData(MsgServerData data) {
        if (mStore != null) {
            if (mStore.msgReceived(this, getSubscription(data.from), data) > 0) {
                noteRecv(mTinode.isMe(data.from));
            }
        } else {
            noteRecv(mTinode.isMe(data.from));
        }
        setSeq(data.seq);
        setTouched(data.ts);

        if (mListener != null) {
            mListener.onData(data);
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

            case ACS:
                sub = getSubscription(pres.src);
                if (sub == null) {
                    Acs acs = new Acs();
                    acs.update(pres.dacs);
                    if (acs.isModeDefined()) {
                        sub = new Subscription<>();
                        sub.topic = getName();
                        sub.user = pres.src;
                        sub.acs = acs;
                        sub.updated = new Date();
                        User<SP> user = mTinode.getUser(pres.src);
                        if (user == null) {
                            getMeta(getMetaGetBuilder().withSub(pres.src).build());
                        } else {
                            sub.pub = user.pub;
                        }
                    } else {
                        Log.w(TAG, "Invalid access mode update '" + pres.dacs + "'");
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
            default:
                Log.i(TAG, "Unhandled presence update '" + pres.what + "' in '" + getName() + "'");
        }

        if (mListener != null) {
            mListener.onPres(pres);
        }
    }

    protected void routeInfo(MsgServerInfo info) {
        if (!info.what.equals(Tinode.NOTE_KP)) {
            Subscription<SP, SR> sub = getSubscription(info.from);
            if (sub != null) {
                switch (info.what) {
                    case Tinode.NOTE_RECV:
                        sub.recv = info.seq;
                        if (mStore != null) {
                            mStore.msgRecvByRemote(sub, info.seq);
                        }
                        break;
                    case Tinode.NOTE_READ:
                        sub.read = info.seq;
                        if (sub.recv < sub.read) {
                            sub.recv = sub.read;
                            if (mStore != null) {
                                mStore.msgRecvByRemote(sub, info.seq);
                            }
                        }
                        if (mStore != null) {
                            mStore.msgReadByRemote(sub, info.seq);
                        }
                        break;
                    default:
                        break;
                }

                // If this is an update from the current user, update the contact with the new count too.
                if (mTinode.isMe(info.from)) {
                    MeTopic me = mTinode.getMeTopic();
                    if (me != null) {
                        me.setMsgReadRecv(getName(), info.what, info.seq);
                    }
                }
            }
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
        ME(0x01), FND(0x02), GRP(0x04), P2P(0x08), SYS(0x10),
        USER(0x04 | 0x08), INTERNAL(0x01 | 0x02 | 0x10), UNKNOWN(0x00),
        ANY(0x01 | 0x02 | 0x04 | 0x08);

        private int val;

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

    @SuppressWarnings("EmptyMethod")
    public static class Listener<DP, DR, SP, SR> {

        public void onSubscribe(int code, String text) {
        }

        public void onLeave(boolean unsub, int code, String text) {
        }

        /**
         * Process {data} message.
         *
         * @param data data packet
         */
        public void onData(MsgServerData data) {
        }

        /**
         * All requested data messages received.
         */
        public void onAllMessagesReceived(Integer count) {
        }

        /**
         * {info} message received
         */
        public void onInfo(MsgServerInfo info) {
        }

        /**
         * {meta} message received
         */
        public void onMeta(MsgServerMeta<DP, DR, SP, SR> meta) {
        }

        /**
         * {meta what="sub"} message received, and this is one of the subs
         */
        public void onMetaSub(Subscription<SP, SR> sub) {
        }

        /**
         * {meta what="desc"} message received
         */
        public void onMetaDesc(Description<DP, DR> desc) {
        }

        /**
         * {meta what="tags"} message received
         */
        public void onMetaTags(String[] tags) {
        }

        /**
         * {meta what="sub"} message received and all subs were processed
         */
        public void onSubsUpdated() {
        }

        /**
         * {pres} received
         */
        public void onPres(MsgServerPres pres) {
        }

        /**
         * {pres what="on|off"} is received
         */
        public void onOnline(boolean online) {
        }

        /** Called when contact is updated. */
        public void onContUpdated(String contact) {
        }
    }

    /**
     * Helper class for generating qury parameters for {sub get} and {meta get} packets.
     */
    public static class MetaGetBuilder {
        protected Topic topic;
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
         * @param limit  number of messages to fetch
         */
        public MetaGetBuilder withData(Integer since, Integer before, Integer limit) {
            meta.setData(since, before, limit);
            return this;
        }

        /**
         * Add query parameters to fetch messages newer than the latest saved message.
         *
         * @param limit number of messages to fetch
         */
        public MetaGetBuilder withLaterData(Integer limit) {
            MsgRange r = topic.getCachedMessagesRange();

            if (r == null || r.hi <= 1) {
                return withData(null, null, limit);
            }
            return withData(r.hi, null, limit);
        }

        /**
         * Add query parameters to fetch messages older than the earliest saved message.
         *
         * @param limit number of messages to fetch
         */
        public MetaGetBuilder withEarlierData(Integer limit) {
            MsgRange r = topic.getMissingMessageRange();
            if (r == null) {
                return withData(null, null, limit);
            }
            return withData(r.low, r.hi, limit);
        }

        /**
         * Default query - same as withLaterData with default number of
         * messages to fetch.
         */
        public MetaGetBuilder withData() {
            return withLaterData(null);
        }

        public MetaGetBuilder withDesc(Date ims) {
            meta.setDesc(ims);
            return this;
        }

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

        public MsgGetMeta build() {
            return meta;
        }
    }
}
