package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import co.tinode.tinodesdk.model.MsgDelRange;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 *
 * Class for handling communication on a single topic
 * Generic parameters:
 *  @param <DP> is the type of Desc.Public
 *  @param <DR> is the type of Desc.Private
 *  @param <SP> is the type of Subscription.Public
 *  @param <SR> is the type of Subscription.Private
 */
@SuppressWarnings("WeakerAccess, unused")
public class Topic<DP,DR,SP,SR> implements LocalData, Comparable<Topic> {
    private static final String TAG = "tinodesdk.Topic";

    public enum TopicType {
        ME(0x01), FND(0x02), GRP(0x04), P2P(0x08),
        USER(0x04 | 0x08), SYSTEM(0x01 | 0x02), UNKNOWN(0x00),
        ANY(0x01 | 0x02 | 0x04 | 0x08);

        private int val;
        TopicType(int val) {
            this.val = val;
        }

        public int val() {return val;}
        public boolean match(TopicType v2) {
            return (val & v2.val) != 0;
        }
    }

    protected enum NoteType {READ, RECV}

    protected Tinode mTinode;

    protected String mName;

    /** The mStore is set by Tinode when the topic calls {@link Tinode#startTrackingTopic(Topic)} */
    Storage mStore = null;

    // Server-provided values:

    // The bulk of topic data
    protected Description<DP,DR> mDesc;
    // Cache of topic subscribers indexed by userID
    protected HashMap<String,Subscription<SP,SR>> mSubs = null;
    // Timestamp of the last update to subscriptions. Default: Oct 25, 2014 05:06:02 UTC, incidentally equal
    // to the first few digits of sqrt(2)
    protected Date mSubsUpdated = null;

    // Tags: user and topic discovery
    protected String[] mTags;

    // The topic is subscribed/online.
    protected boolean mAttached = false;

    protected Listener<DP,DR,SP,SR> mListener = null;

    // Timestamp of the last key press that the server was notified of, milliseconds
    protected long mLastKeyPress = 0;

    private Payload mLocal = null;

    protected boolean mOnline = false;

    protected LastSeen mLastSeen = null;

    protected int mMaxDel = 0;

    Topic(Tinode tinode, String name) {
        mTinode = tinode;
        if (name == null) {
            name = Tinode.TOPIC_NEW + tinode.nextUniqueString();
        }
        setName(name);
        mDesc = new Description<>();

        mTinode.startTrackingTopic(this);
    }

    protected Topic(Tinode tinode, Subscription<SP,SR> sub) {
        this(tinode, sub.topic);
        mDesc.merge(sub);
        if (sub.online != null) {
            mOnline = sub.online;
        }
    }

    protected Topic(Tinode tinode, String name, Description<DP,DR> desc) {
        this(tinode, name);
        mDesc.merge(desc);
    }

    /**
     * Create a named topic.
     *
     * @param tinode instance of Tinode object to communicate with the server
     * @param name name of the topic
     * @param l event listener, optional
     *
     * @throws IllegalArgumentException if 'tinode' argument is null
     */
    protected Topic(Tinode tinode, String name, Listener<DP,DR,SP,SR> l) {
        this(tinode, name);
        setListener(l);
    }

    /**
     * Start a new topic.
     *
     * Construct {@code }typeOfT} with one of {@code
     * com.fasterxml.jackson.databind.type.TypeFactory.constructXYZ()} methods such as
     * {@code mMyConnectionInstance.getTypeFactory().constructType(MyPayloadClass.class)}.
     *
     * The actual topic name will be set after completion of a successful subscribe call
     *
     * @param tinode tinode instance
     * @param l event listener, optional
     */
    protected Topic(Tinode tinode, Listener<DP,DR,SP,SR> l) {
        this(tinode, null, l);
    }

    /**
     * Set custom types of payload: {data} as well as public and private content. Needed for
     * deserialization of server messages.
     *
     * @param typeOfDescPublic type of {meta.desc.public}
     * @param typeOfDescPrivate type of {meta.desc.private}
     * @param typeOfSubPublic type of {meta.subs[].public}
     * @param typeOfSubPrivate type of {meta.subs[].private}
     *
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
     * @param typeOfDescPublic type of {meta.desc.public}
     * @param typeOfDescPrivate type of {meta.desc.private}
     * @param typeOfSubPublic type of {meta.sub[].public}
     * @param typeOfSubPrivate type of {meta.sub[].private}
     *
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
     * @param typeOfDescPublic type of {meta.desc.public}
     * @param typeOfDescPrivate type of {meta.desc.private}
     * @param typeOfSubPublic type of {meta.desc.public}
     * @param typeOfSubPrivate type of {meta.desc.private}
     *
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
    protected void update(Subscription<SP,SR> sub) {
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
    protected void update(Description<DP,DR> desc) {
        if (mDesc.merge(desc) && mStore != null) {
            mStore.topicUpdate(this);
        }
    }

    /**
     * Topic sent an update to subscription, got a confirmation.
     *
     * @param sub updated topic parameters
     */
    @SuppressWarnings("unchecked")
    protected void update(Map<String,Object> params, MetaSetSub sub) {
        String user = sub.user;

        Map<String,String> acsMap = params != null ? (Map<String,String>) params.get("acs") : null;
        Acs acs;
        if (acsMap != null) {
            acs = new Acs(acsMap);
        } else {
            acs = new Acs();
            if (user == null) {
                acs.setWant(sub.mode);
            } else {
                acs.setGiven(sub.mode);
            }
        }

        if (user == null) {
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
        Subscription<SP,SR> s = getSubscription(user);
        if (s == null) {
            s = new Subscription<>();
            s.user = user;
            s.acs = acs;
            addSubToCache(s);
        } else {
            s.acs.merge(acs);
        }

    }

    /**
     * Topic sent an update to topic parameters, got a confirmation, now copy
     * these parameters to topic description.
     *
     * @param desc updated topic parameters
     */
    protected void update(MetaSetDesc<DP,DR> desc) {
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
    protected void update(MsgServerCtrl ctrl, MsgSetMeta<DP,DR> meta) {
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
     * Called by Tinode from {@link Tinode#startTrackingTopic(Topic)}
     *
     * @param store storage object
     */
    protected void setStorage(Storage store) {
        mStore = store;
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


    public Date getSubsUpdated() {
        return mSubsUpdated;
    }

    public int getSeq() {
        return mDesc.seq;
    }
    public void setSeq(int seq) {
        if (seq > mDesc.seq) {
            mDesc.seq = seq;
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
     * @return true if the topic is archived, false otherwise.
     */
    public boolean isArchived() {
        return false;
    }


    public Storage.Range getCachedMessageRange() {
        return mStore == null ? null : mStore.getCachedMessagesRange(this);
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

    //public void setAccessMode(String mode) {
    //    mDesc = new Acs(mode);
    //}

    public boolean isAdmin() {
        return mDesc.acs != null && mDesc.acs.isAdmin();
    }
    public PromisedReply<ServerMessage> updateAdmin(final boolean admin) {
        return updateMode(null, admin ? "+A" : "-A");
    }

    public boolean isManager() {
        return mDesc.acs != null && mDesc.acs.isManager();
    }

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
        return unread > 0 ? unread : 0;
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
     * Subscribe to topic
     */
    public PromisedReply<ServerMessage> subscribe() {
        MsgSetMeta<DP,DR> mset = null;
        MsgGetMeta mget;
        if (isNew()) {
            // If this is a new topic, sync topic description
            List<String> tags = null;
            if (mTags != null) {
                tags = Arrays.asList(mTags);
            }
            mset = new MsgSetMeta<>(new MetaSetDesc<>(mDesc.pub, mDesc.priv), null, tags);
            mget = null;
        } else {
            mget = getMetaGetBuilder()
                    .withGetDesc().withGetData().withGetSub().withGetTags().build();
        }
        return subscribe(mset, mget);
    }

    /**
     * Subscribe to topic with parameters
     *
     * @throws NotConnectedException if there is no live connection to the server
     * @throws AlreadySubscribedException if the client is already subscribed to the given topic
     */
    @SuppressWarnings("unchecked")
    public PromisedReply<ServerMessage> subscribe(MsgSetMeta<DP,DR> set, MsgGetMeta get) {
        if (mAttached) {
            if (set == null && get == null) {
                // If the topic is already attached and the user does not attempt to set or
                // get any data, just return resolved promise.
                return new PromisedReply<>((ServerMessage) null);
            }
            throw new AlreadySubscribedException();
        }

        if (!mTinode.isConnected()) {
            throw new NotConnectedException();
        }

        final String topicName = getName();
        if (!isPersisted()) {
            persist(true);
        }

        return mTinode.subscribe(topicName, set, get).thenApply(
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
                            if (sre.getCode() >= 400 && sre.getCode() < 500) {
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
     * @param unsub true to disconnect and unsubscribe from topic, otherwise just disconnect
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
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
        }

        if (mTinode.isConnected()) {
            throw new NotSubscribedException();
        }

        throw new NotConnectedException();
    }

    /**
     * Leave topic without unsubscribing
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
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
                if (id > 0 && mStore != null) {
                    if (mStore.msgDelivered(Topic.this, id, ctrl.ts, seq)) {
                        setRecv(seq);
                    }
                } else {
                    setRecv(seq);
                }
                setRead(seq);
                if (mStore != null) {
                    mStore.setRead(Topic.this, seq);
                }
            }
        }
    }

    protected PromisedReply<ServerMessage> publish(final Drafty content, final long msgId) {
        return mTinode.publish(getName(), content.isPlain() ? content.toString() : content).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        processDelivery(result.ctrl, msgId);
                        return null;
                    }
                },
                new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        if (mStore != null) {
                            mStore.msgSyncing(Topic.this, msgId, false);
                        }
                        return null;
                    }
                });
    }

    /**
     * Publish message to a topic. It will attempt to publish regardless of subscription status.
     *
     * @param content payload
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
     */
    public PromisedReply<ServerMessage> publish(final Drafty content) {
        final long id;
        if (mStore != null) {
            id = mStore.msgSend(this, content);
        } else {
            id = -1;
        }

        if (mAttached) {
            return publish(content, id);
        } else {
            return subscribe().thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            return publish(content, id);
                        }
                    });
        }
    }

    /**
     * Convenience method for plain text messages. Will convert message to Drafty.
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
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
     */
    @SuppressWarnings("UnusedReturnValue")
    public <ML extends Iterator<Storage.Message> & Closeable> PromisedReply<ServerMessage> syncAll() {
        PromisedReply<ServerMessage> last = new PromisedReply<>((ServerMessage) null);
        if (mStore == null) {
            return last;
        }

        // Get soft-deleted message IDs.
        final List<Integer> toSoftDelete = mStore.getQueuedMessageDeletes(this, false);
        if (toSoftDelete != null) {
            last = mTinode.delMessage(getName(), toSoftDelete, false)
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        int delId = result.ctrl.getIntParam("del", 0);
                        if (mStore != null && delId > 0) {
                            mStore.msgDelete(Topic.this, delId, toSoftDelete);
                        }
                        return null;
                }
            });
        }

        // Get hard-deleted message IDs.
        final List<Integer> toHardDelete = mStore.getQueuedMessageDeletes(this, true);
        if (toHardDelete != null) {
            last = mTinode.delMessage(getName(), toHardDelete, true)
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        int delId = result.ctrl.getIntParam("del", 0);
                        if (mStore != null && delId > 0) {
                            mStore.msgDelete(Topic.this, delId, toHardDelete);
                        }
                        return null;
                }
            });
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
                last = mTinode.publish(getName(), msg.getContent())
                        .thenApply(
                                new PromisedReply.SuccessListener<ServerMessage>() {
                                    @Override
                                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                        processDelivery(result.ctrl, msgId);
                                        return null;
                                    }
                                },
                                new PromisedReply.FailureListener<ServerMessage>() {
                                    @Override
                                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                                        Log.w(TAG, "SyncAll failed msgId=" + msgId, err);
                                        mStore.msgSyncing(Topic.this, msgId, false);
                                        return null;
                                    }
                                });
            }
        } finally {
            try {
                toSend.close();
            } catch (IOException ignored) {}
        }

        return last;
    }

    /**
     * Try to sync one message.
     *
     * @return {@link PromisedReply} resolved on result of the operation.
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
     */
    public PromisedReply<ServerMessage> syncOne(long msgDatabaseId) {
        PromisedReply<ServerMessage> result = new PromisedReply<>((ServerMessage) null);
        if (mStore == null) {
            return result;
        }

        final Storage.Message m = mStore.getMessageById(this, msgDatabaseId);
        if (m != null) {
            try {
                if (m.isDeleted()) {
                    result = mTinode.delMessage(getName(), m.getSeqId(), m.isDeleted(true))
                            .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                    int delId = result.ctrl.getIntParam("del", 0);
                                    if (mStore != null && delId > 0) {
                                        mStore.msgDelete(Topic.this, delId, m.getSeqId(), m.getSeqId() + 1);
                                    }
                                    return null;
                                }
                            });
                } else if (m.isReady()) {
                    mStore.msgSyncing(this, m.getId(), true);
                    result = mTinode.publish(getName(), m.getContent())
                            .thenApply(
                                    new PromisedReply.SuccessListener<ServerMessage>() {
                                        @Override
                                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                            processDelivery(result.ctrl, m.getId());
                                            return null;
                                        }
                                    },
                                    new PromisedReply.FailureListener<ServerMessage>() {
                                        @Override
                                        public PromisedReply<ServerMessage> onFailure(Exception err) {
                                            Log.w(TAG, "SyncOne failed msgId=" + m.getId(), err);
                                            mStore.msgSyncing(Topic.this, m.getId(), false);
                                            return null;
                                        }
                                    });
                }
            } catch (Exception ignored) {}
        }

        return result;
    }

    /**
     * Query topic for data or metadata
     */
    public PromisedReply<ServerMessage> getMeta(MsgGetMeta query) {
        if (!mTinode.isConnected()) {
            throw new NotConnectedException();
        }

        return mTinode.getMeta(getName(), query);
    }

    /**
     * Update topic metadata
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> setMeta(final MsgSetMeta<DP,DR> meta) {
        if (!mTinode.isConnected()) {
            throw new NotConnectedException();
        }

        return mTinode.setMeta(getName(), meta).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    update(result.ctrl, meta);
                    return null;
                }
            }, null);
    }

    /**
     * Update topic description. Calls {@link #setMeta}.
     * @param desc new description (public, private, default access)
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    protected PromisedReply<ServerMessage> setDescription(final MetaSetDesc<DP,DR> desc) {
        return setMeta(new MsgSetMeta<>(desc, null, null));
    }

    /**
     * Update topic description. Calls {@link #setMeta}.
     * @param pub new public info
     * @param priv new private info
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> setDescription(final DP pub, final DR priv) {
        return setDescription(new MetaSetDesc<>(pub, priv));
    }

    /**
     * Update topic's default access
     *
     * @param auth default access mode for authenticated users
     * @param anon default access mode for anonymous users
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> updateDefAcs(String auth, String anon) {
        return setDescription(new MetaSetDesc<DP,DR>(new Defacs(auth, anon)));
    }

    /**
     * Update subscription. Calls {@link #setMeta}.
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    protected PromisedReply<ServerMessage> setSubscription(final MetaSetSub sub) {
        return setMeta(new MsgSetMeta<DP,DR>(null, sub, null));
    }

    /**
     * Update another user's access mode.
     *
     * @param uid UID of the user to update
     * @param update string which defines the update. It could be a full value or a change.

     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
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

        if (sub == null && getTopicType() != TopicType.P2P) {
            throw new NotSubscribedException();
        }

        final boolean self = (uid == null || sub == null);

        if (mDesc.acs == null) {
            mDesc.acs = new Acs();
        }

        final AcsHelper mode = self ? mDesc.acs.getWantHelper() : sub.acs.getGivenHelper();
        if (mode.update(update)) {
            return setSubscription(new MetaSetSub(uid, mode.toString()))
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            if (result.ctrl != null) {
                                if (self) {
                                    mDesc.acs.merge((Map) result.ctrl.params);
                                } else {
                                    sub.acs.merge((Map) result.ctrl.params);
                                }
                            }
                            return null;
                        }
                    });
        }
        // The state is unchanged, return resolved promise.
        return new PromisedReply<>((ServerMessage) null);
    }
    /**
     * Invite user to the topic.
     *
     * @param uid ID of the user to invite to topic
     * @param mode access mode granted to user
     *
     * @throws NotConnectedException if there is no connection to the server
     * @throws NotSynchronizedException if the topic has not yet been synchronized with the server
     */
    @SuppressWarnings("unchecked")
    public PromisedReply<ServerMessage> invite(String uid, String mode) {

        final Subscription<SP,SR> sub;
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
            throw new NotSynchronizedException();
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
     *
     * @throws NotSubscribedException if the user is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     * @throws NotSynchronizedException if the topic has not yet been synchronized with the server
     */
    public PromisedReply<ServerMessage> eject(String uid, boolean ban) {
        final Subscription<SP,SR> sub = getSubscription(uid);

        if (sub == null) {
            throw new NotSubscribedException();
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

            throw new NotSynchronizedException();
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
        }, null);
    }

    /**
     * Delete message range.
     *
     * @param hard hard-delete messages
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
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
                    if (mStore != null && delId > 0) {
                        mStore.msgDelete(Topic.this, delId, fromId, toId);
                    }
                    return null;
                }
            }, null);
        }

        if (mTinode.isConnected()) {
            throw new NotSubscribedException();
        }

        throw new NotConnectedException();
    }

    /**
     * Delete messages with id in the provided list.
     *
     * @param list delete messages with ids in this list
     * @param hard hard-delete messages
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> delMessages(final List<Integer> list, final boolean hard) {
        if (mStore != null) {
            mStore.msgMarkToDelete(this, list, hard);
        }

        if (mAttached) {
            return mTinode.delMessage(getName(), list, hard).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    int delId = result.ctrl.getIntParam("del", 0);
                    if (mStore != null && delId > 0) {
                        mStore.msgDelete(Topic.this, delId, list);
                    }
                    return null;
                }
            }, null);
        }

        if (mTinode.isConnected()) {
            throw new NotSubscribedException();
        }

        throw new NotConnectedException();
    }
    /**
     * Delete all messages.
     *
     * @param hard hard-delete messages
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> delMessages(final boolean hard) {
        return delMessages(0, getSeq() + 1, hard);
    }

    /**
     * Delete topic
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> delete() {
        if (!mTinode.isConnected()) {
            throw new NotConnectedException();
        }

        // Delete works even if the topic is not attached.

        return mTinode.delTopic(getName()).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        topicLeft(true, result.ctrl.code, result.ctrl.text);
                        mTinode.stopTrackingTopic(getName());
                        persist(false);
                        return null;
                    }
                }, null);
    }

    /**
     * Let server know the seq id of the most recent received/read message.
     *
     * @param what "read" or "recv" to indicate which action to report
     */
    protected int noteReadRecv(NoteType what, boolean fromMe) {
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
                    if (mDesc.read < mDesc.seq) {
                        if (!fromMe) {
                            mTinode.noteRead(getName(), mDesc.seq);
                        }
                        result = mDesc.read = mDesc.seq;
                    }
                    break;
            }
        } catch (NotConnectedException ignored) {}

        return result;
    }

    /** Notify the server that the client read the message */
    @SuppressWarnings("UnusedReturnValue")
    public int noteRead() {
        return noteRead(false);
    }

    public int noteRead(boolean fromMe) {
        int result = noteReadRecv(NoteType.READ, fromMe);
        if (mStore != null) {
            mStore.setRead(this, result);
        }
        return result;
    }

    /** Notify the server that the messages is stored on the client */
    @SuppressWarnings("UnusedReturnValue")
    public int noteRecv() {
        return noteRecv(false);
    }

    protected int noteRecv(boolean fromMe) {
        int result = noteReadRecv(NoteType.RECV, fromMe);
        if (mStore != null) {
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
            } catch (NotConnectedException ignored) {}
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
    protected void addSubToCache(Subscription<SP,SR> sub) {
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
    protected void removeSubFromCache(Subscription<SP,SR> sub) {
        if (mSubs != null) {
            mSubs.remove(sub.user);
        }
    }

    public Subscription<SP,SR> getSubscription(String key) {
        if (mSubs == null) {
            loadSubs();
        }
        return mSubs != null ? mSubs.get(key) : null;
    }

    public Collection<Subscription<SP,SR>> getSubscriptions() {
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
     *
     * @return count of recepients who claim to have received the message
     */
    public int msgRecvCount(int seq) {
        int count = 0;
        if (seq > 0) {
            String me = mTinode.getMyId();
            Collection<Subscription<SP,SR>> subs = getSubscriptions();
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
     *
     * @return count of recepients who claim to have read the message.
     */
    public int msgReadCount(int seq) {
        int count = 0;
        if (seq > 0) {
            String me = mTinode.getMyId();
            Collection<Subscription<SP,SR>> subs = getSubscriptions();
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

    public static TopicType getTopicTypeByName(String name) {
        if (name != null) {
            if (name.equals(Tinode.TOPIC_ME)) {
                return TopicType.ME;
            } else if (name.equals(Tinode.TOPIC_FND)) {
                return TopicType.FND;
            } else if (name.startsWith(Tinode.TOPIC_GRP_PREFIX) || name.startsWith(Tinode.TOPIC_NEW)) {
                return TopicType.GRP;
            } else if (name.startsWith(Tinode.TOPIC_USR_PREFIX)) {
                return TopicType.P2P;
            }
        }
        return  TopicType.UNKNOWN;
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

    public static boolean getIsNewByName(String name) {
        return name.startsWith(Tinode.TOPIC_NEW);  // "newRANDOM" when the topic was locally initialized but not yet
                                                    // synced with the server
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
     * @param unsub - not just detached but also unsubscribed
     * @param code result code, always 200
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

    protected void routeMeta(MsgServerMeta<DP,DR,SP,SR> meta) {
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

    protected void routeMetaDesc(MsgServerMeta<DP,DR,SP,SR> meta) {
        update(meta.desc);

        if (getTopicType() == TopicType.P2P) {
            mTinode.updateUser(getName(), meta.desc);
        }

        if (mListener != null) {
            mListener.onMetaDesc(meta.desc);
        }
    }

    @SuppressWarnings("unchecked")
    protected void processSub(Subscription<SP,SR> newsub) {
        // In case of a generic (non-'me') topic, meta.sub contains topic subscribers.
        // I.e. sub.user is set, but sub.topic is equal to current topic.

        Subscription<SP,SR> sub;

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
                    mListener.onContUpdate(sub);
                }
            }
        }

        if (mListener != null) {
            mListener.onMetaSub(sub);
        }
    }

    protected void routeMetaSub(MsgServerMeta<DP,DR,SP,SR> meta) {
        for (Subscription<SP,SR> newsub : meta.sub) {
            processSub(newsub);
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }

    protected void routeMetaDel(int clear, MsgDelRange[] delseq) {
        if (mStore != null) {
            for (MsgDelRange range : delseq) {
                mStore.msgDelete(this, clear, range.low, range.hi == null ? range.low + 1 : range.hi);
            }
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

    protected void routePres(MsgServerPres pres) {
        MsgServerPres.What what = MsgServerPres.parseWhat(pres.what);
        Subscription<SP,SR> sub;
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
                            getMeta(getMetaGetBuilder().withGetSub(pres.src).build());
                        } else {
                            sub.pub = user.pub;
                        }
                    }
                } else {
                    // Update to an existing subscription.
                    sub.updateAccessMode(pres.dacs);
                }
                processSub(sub);
                break;
            default:
                Log.w(TAG, "Unknown presence update: " + pres.what);
        }

        if (mListener != null) {
            mListener.onPres(pres);
        }
    }

    protected void routeInfo(MsgServerInfo info) {
        if (!info.what.equals(Tinode.NOTE_KP)) {
            Subscription<SP,SR> sub = getSubscription(info.from);
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
            }

            // If this is an update from the current user, update the contact with the new count too.
            if (mTinode.isMe(info.from)) {
                MeTopic me = mTinode.getMeTopic();
                if (me != null) {
                    //noinspection unchecked
                    me.processOneSub(sub);
                }
            }
        }

        if (mListener != null) {
            mListener.onInfo(info);
        }
    }

    @Override
    public void setLocal(Payload value) {
        mLocal = value;
    }

    @Override
    public Payload getLocal() {
        return mLocal;
    }

    public synchronized void setListener(Listener<DP,DR,SP,SR> l) {
        mListener = l;
    }

    public static class Listener<DP,DR,SP,SR> {

        public void onSubscribe(int code, String text) {}
        public void onLeave(boolean unsub, int code, String text) {}

        /**
         * Process {data} message.
         * @param data data packet
         */
        public void onData(MsgServerData data) {}
        /** All requested data messages received. */
        public void onAllMessagesReceived(Integer count) {}

        /** {info} message received */
        public void onInfo(MsgServerInfo info) {}
        /** {meta} message received */
        public void onMeta(MsgServerMeta<DP,DR,SP,SR> meta) {}
        /** {meta what="sub"} message received, and this is one of the subs */
        public void onMetaSub(Subscription<SP,SR> sub) {}
        /** {meta what="desc"} message received */
        public void onMetaDesc(Description<DP,DR> desc) {}
        /** {meta what="tags"} message received */
        public void onMetaTags(String[] tags) {}
        /** {meta what="sub"} message received and all subs were processed */
        public void onSubsUpdated() {}
        /** {pres} received */
        public void onPres(MsgServerPres pres) {}
        /** {pres what="on|off"} is received */
        public void onOnline(boolean online) {}
        /** Called when topic descriptor as contact is updated */
        public void onContUpdate(Subscription<SP,SR> sub) {}
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
         * @param since messages newer than this;
         * @param before older than this
         * @param limit number of messages to fetch
         */
        public MetaGetBuilder withGetData(Integer since, Integer before, Integer limit) {
            meta.setData(since, before, limit);
            return this;
        }

        /**
         * Add query parameters to fetch messages newer than the latest saved message.
         *
         * @param limit number of messages to fetch
         */
        public MetaGetBuilder withGetLaterData(Integer limit) {
            Storage.Range r = topic.getCachedMessageRange();

            if (r == null) {
                return withGetData(null, null, limit);
            }
            return withGetData(r.max > 0 ? r.max + 1 : null, null, limit);
        }

        /**
         * Add query parameters to fetch messages older than the earliest saved message.
         *
         * @param limit number of messages to fetch
         */
        public MetaGetBuilder withGetEarlierData(Integer limit) {
            Storage.Range r = topic.getCachedMessageRange();
            if (r == null) {
                return withGetData(null, null, limit);
            }
            return withGetData(null, r.min > 0 ? r.min : null, limit);
        }

        /**
         * Default query - same as withGetLaterData with default number of
         * messages to fetch.
         */
        public MetaGetBuilder withGetData() {
            return withGetLaterData(null);
        }

        public MetaGetBuilder withGetDesc(Date ims) {
            meta.setDesc(ims);
            return this;
        }

        public MetaGetBuilder withGetDesc() {
            return withGetDesc(topic.getUpdated());
        }

        public MetaGetBuilder withGetSub(String userOrTopic, Date ims, Integer limit) {
            if (topic.getTopicType() == TopicType.ME) {
                meta.setSubTopic(userOrTopic, ims, limit);
            } else {
                meta.setSubUser(userOrTopic, ims, limit);
            }
            return this;
        }

        public MetaGetBuilder withGetSub(Date ims, Integer limit) {
            return withGetSub(null, ims, limit);
        }

        public MetaGetBuilder withGetSub() {
            return withGetSub(null, topic.getSubsUpdated(), null);
        }

        public MetaGetBuilder withGetSub(String userOrTopic) {
            return withGetSub(userOrTopic, topic.getSubsUpdated(), null);
        }

        public MetaGetBuilder withGetDel(Integer since, Integer limit) {
            meta.setDel(since, limit);
            return this;
        }

        public MetaGetBuilder withGetLaterDel(Integer limit) {
            return withGetDel(topic.getMaxDel() + 1, limit);
        }

        public MetaGetBuilder withGetDel() {
            return withGetLaterDel(null);
        }

        public MetaGetBuilder withGetTags() {
            meta.setTags();
            return this;
        }

        public MsgGetMeta build() {
            return meta;
        }
    }
}
