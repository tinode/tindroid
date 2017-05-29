/**
 * Created by gene on 06/02/16.
 */

package co.tinode.tinodesdk;

import android.util.Log;

import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.AcsHelper;
import co.tinode.tinodesdk.model.Defacs;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.LastSeen;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.MetaSetSub;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

/**
 *
 * Class for handling communication on a single topic
 *
 */
public class Topic<Pu,Pr,T> implements LocalData {
    private static final String TAG = "tinodesdk.Topic";

    public enum TopicType {
        ME(0x01), FND(0x02), GRP(0x04), P2P(0x08),
        USER(0x04 | 0x08), SYSTEM(0x01 | 0x02), UNKNOWN(0x00),
        ANY(0x01 | 0x02 | 0x04 | 0x08);

        private int val = 0;
        TopicType(int val) {
            this.val = val;
        }

        public int val() {return val;}
    }

    protected enum NoteType {READ, RECV}

    protected JavaType mTypeOfDataPacket = null;
    protected JavaType mTypeOfMetaPacket = null;

    protected String mClassNameOfPublic;
    protected String mClassNameOfPrivate;
    protected String mClassNameOfContent;

    protected Tinode mTinode;

    protected String mName;

    /** The mStore is set by Tinode when the topic calls {@link Tinode#registerTopic(Topic)} */
    Storage mStore = null;

    // Server-provided values:

    // The bulk of topic data
    protected Description<Pu,Pr> mDesc;
    // Cache of topic subscribers indexed by userID
    protected HashMap<String,Subscription<Pu,Pr>> mSubs = null;
    // Timestamp of the last update to subscriptions. Default: Oct 25, 2014 05:06:02 UTC, incidentally equal
    // to the first few digits of sqrt(2)
    protected Date mSubsUpdated = new Date(1414213562);

    protected boolean mAttached = false;
    protected Listener<Pu,Pr,T> mListener = null;

    // Timestamp of the last key press that the server was notified of, milliseconds
    protected long mLastKeyPress = 0;

    private Payload mLocal = null;

    protected boolean mOnline = false;

    protected LastSeen mLastSeen = null;

    protected Topic(Tinode tinode, Subscription<Pu,Pr> sub) {
        if (tinode == null) {
            throw new IllegalArgumentException("Tinode cannot be null");
        }
        mTinode = tinode;

        mTypeOfDataPacket   = tinode.getTypeOfDataPacket();
        mTypeOfMetaPacket   = tinode.getTypeOfMetaPacket();

        setName(sub.topic);

        mDesc   = new Description<>();
        mDesc.merge(sub);
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
    public Topic(Tinode tinode, String name, Listener<Pu,Pr,T> l) {
        if (tinode == null) {
            throw new IllegalArgumentException("Tinode cannot be null");
        }
        mTinode = tinode;

        setName(name);

        mDesc = new Description<>();

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
    public Topic(Tinode tinode, Listener<Pu,Pr,T> l) {
        this(tinode, Tinode.TOPIC_NEW + tinode.nextUniqueString(), l);
    }

    /**
     * Set custom types of payload: {data} as well as public and private content. Needed for
     * deserialization of server messages.
     *
     * @param typeOfPublic type of {meta.desc.public}
     * @param typeOfPrivate type of {meta.desc.private}
     * @param typeOfContent type of {data.content}
     *
     */
    public void setTypes(JavaType typeOfPublic,
                                JavaType typeOfPrivate, JavaType typeOfContent) {
        mClassNameOfPublic = typeOfPublic.toCanonical();
        mClassNameOfPrivate = typeOfPrivate.toCanonical();
        mClassNameOfContent = typeOfContent.toCanonical();

        mTypeOfDataPacket = Tinode.getTypeFactory()
                .constructParametricType(MsgServerData.class, typeOfContent);
        mTypeOfMetaPacket = Tinode.getTypeFactory()
                .constructParametricType(MsgServerMeta.class, typeOfPublic, typeOfPrivate);
    }

    /**
     * Set types of payload: {data} as well as public and private content. Needed for
     * deserialization of server messages.
     *
     * @param typeOfPublic type of {meta.desc.public}
     * @param typeOfPrivate type of {meta.desc.private}
     * @param typeOfContent type of {data.content}
     *
     */
    public void setTypes(Class<?> typeOfPublic,
                                Class<?> typeOfPrivate, Class<?> typeOfContent) {
        final TypeFactory tf = Tinode.getTypeFactory();
        setTypes(tf.constructType(typeOfPublic), tf.constructType(typeOfPrivate), tf.constructType(typeOfContent));
    }

    /**
     * Set types of payload: {data} content as well as public and private fields of topic.
     * Type names must be generated by {@link JavaType#toCanonical()}
     *
     * @param typeOfPublic type of {meta.desc.public}
     * @param typeOfPrivate type of {meta.desc.private}
     * @param typeOfContent type of {data.content}
     *
     * @throws IllegalArgumentException if types cannot be parsed
     */
    public void setTypes(String typeOfPublic, String typeOfPrivate, String typeOfContent)
            throws IllegalArgumentException {
        final TypeFactory tf = Tinode.getTypeFactory();
        setTypes(tf.constructFromCanonical(typeOfPublic), tf.constructFromCanonical(typeOfPrivate),
                tf.constructFromCanonical(typeOfContent));
    }

    /**
     * Set types of payload: {data} content as well as public and private fields of topic.
     * Type names must be generated by {@link Topic#getSerializedTypes()}.
     *
     * @param serialized type of {meta.desc.public}
     *
     * @throws IllegalArgumentException if types cannot be parsed
     */
    public void setSerializedTypes(String serialized) throws IllegalArgumentException {
        // Log.d(TAG, "Serialized types: " + serialized);

        if (serialized == null) {
            return;
        }

        String[] parts = serialized.split("\\n");
        // Log.d(TAG, "Serialized types parsed: " + Arrays.toString(parts));

        if (parts.length == 3) {
            setTypes(parts[0], parts[1], parts[2]);
        } else {
            throw new IllegalArgumentException("Failed to parse serialized types");
        }
    }

    /**
     * @return Content types as a string suitable for persistent storage.
     */
    public String getSerializedTypes() {
        if (mClassNameOfContent == null || mClassNameOfPrivate == null || mClassNameOfPublic == null) {
            return null;
        }
        return mClassNameOfPublic + "\n" + mClassNameOfPrivate + "\n" + mClassNameOfContent;
    }

    /**
     * Update topic parameters from a Subscription object. Called by MeTopic.
     *
     * @param sub updated topic parameters
     */
    protected void update(Subscription<Pu,Pr> sub) {
        if (mDesc.merge(sub) && mStore != null) {
            mStore.topicUpdate(this);
        }
    }

    /**
     * Update topic parameters from a Description object.
     *
     * @param desc updated topic parameters
     */
    protected void update(Description<Pu,Pr> desc) {
        if (mDesc.merge(desc) && mStore != null) {
            mStore.topicUpdate(this);
        }
    }

    /**
     * Topic sent an update to subscription, got a confirmation.
     *
     * @param sub updated topic parameters
     */
    protected void update(Map<String,Object> params, MetaSetSub<?> sub) {
        Log.d(TAG, "Topic.update(ctrl.params, MetaSetSub)");
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
            boolean changed = false;
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
        Subscription<Pu,Pr> s = mSubs.get(user);
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
    protected void update(MetaSetDesc<Pu,Pr> desc) {
        Log.d(TAG, "Topic.update(MetaSetDesc)");
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
    protected void update(MsgServerCtrl ctrl, MsgSetMeta<Pu,Pr,?> meta) {
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
    }

    /**
     * Called by Tinode from {@link Tinode#registerTopic(Topic)}
     *
     * @param store storage object
     */
    void setStorage(Storage store) {
        mStore = store;
    }

    public Date getCreated() {
        return mDesc.created;
    }
    public void setCreated(Date created) {
        mDesc.created = created;
    }

    public Date getUpdated() {
        return mDesc.updated;
    }
    public void setUpdated(Date updated) {
        mDesc.updated = updated;
    }

    public Date getSubsUpdated() {
        return mSubsUpdated;
    }

    public String getWith() {
        return getTopicType() == TopicType.P2P ? mDesc.with : null;
    }
    public void setWith(String with) {
        if (getTopicType() == TopicType.P2P) {
            mDesc.with = with;
        }
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

    public Pu getPub() {
        return mDesc.pub;
    }
    public void setPub(Pu pub) {
        mDesc.pub = pub;
    }

    public Pr getPriv() {
        return mDesc.priv;
    }
    public void setPriv(Pr priv) {
        mDesc.priv = priv;
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
    //public void setAccessMode(String mode) {
    //    mDesc = new Acs(mode);
    //}

    public boolean isAdmin() {
        return mDesc.acs != null && mDesc.acs.isAdmin();
    }
    public PromisedReply<ServerMessage> updateAdmin(final boolean isAdmin) throws Exception {
        return updateMode(null, isAdmin ? "+S" : "-S");
    }

    public boolean isMuted() {
        return mDesc.acs != null && mDesc.acs.isMuted();
    }
    public PromisedReply<ServerMessage> updateMuted(final boolean muted) throws Exception {
        return updateMode(null, muted ? "-P" : "+P");
    }

    public boolean isOwner() {
        return mDesc.acs != null && mDesc.acs.isOwner();
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
        //Log.d(TAG, "getUnreadCount topic=" + mName + ", seq=" + mDesc.seq + ", read=" + mDesc.read);
        int unread = mDesc.seq - mDesc.read;
        return unread > 0 ? unread : 0;
    }

    public boolean getOnline() {
        return mOnline;
    }
    protected void setOnline(boolean online) {
        if (online != mOnline) {
            //(TAG, "Topic[" + mName + "].setOnline(" + online + ");");
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
    protected boolean isPersisted() {
        return getLocal() != null;
    }

    protected void setLastSeen(Date when, String ua) {
        mLastSeen = new LastSeen(when, ua);
    }

    /**
     * Subscribe to topic
     *
     * @throws Exception when anything goes wrong
     */
    public PromisedReply<ServerMessage> subscribe() throws Exception {
        MsgSetMeta<Pu,Pr,?> mset = null;
        if (isNew() && (mDesc.pub != null || mDesc.priv != null)) {
            // If this is a new topic, sync topic description
            mset = new MsgSetMeta<>(new MetaSetDesc<>(mDesc.pub, mDesc.priv), null);
        }
        return subscribe(mset, subscribeParamGetBuilder()
                .withGetDesc().withGetData().withGetSub().build());
    }

    /**
     * Subscribe to topic with parameters
     *
     * @throws NotConnectedException if there is no live connection to the server
     * @throws AlreadySubscribedException if the client is already subscribed to the given topic
     */
    public PromisedReply<ServerMessage> subscribe(MsgSetMeta<Pu,Pr,?> set, MsgGetMeta get)
            throws Exception {

        if (mAttached) {
            throw new AlreadySubscribedException();
        }

        if (mTinode.getTopic(getName()) == null) {
            mTinode.registerTopic(this);
        }

        if (!mTinode.isConnected()) {
            throw new NotConnectedException();
        }

        return mTinode.subscribe(getName(), set, get).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage msg)
                            throws Exception {
                        if (!mAttached) {
                            mAttached = true;
                            if (msg.ctrl != null) {
                                if (msg.ctrl.params != null) {
                                    mDesc.acs = new Acs((Map<String, String>) msg.ctrl.params.get("acs"));
                                    if (isNew()) {
                                        setUpdated(msg.ctrl.ts);
                                        setName(msg.ctrl.topic);
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
                }, null);
    }

    public MetaGetBuilder subscribeParamGetBuilder() {
        return new MetaGetBuilder(this);
    }

    /**
     * Leave topic
     * @param unsub true to disconnect and unsubscribe from topic, otherwise just disconnect
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
     */
    public PromisedReply<ServerMessage> leave(final boolean unsub) throws Exception {
        if (mAttached) {
            return mTinode.leave(getName(), unsub).thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result)
                                throws Exception {
                            topicLeft(unsub, result.ctrl.code, result.ctrl.text);
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
     * Leave topic without unsubscribing
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
     */
    public PromisedReply<ServerMessage> leave() throws Exception {
        return leave(false);
    }

    private void processDelivery(final MsgServerCtrl ctrl, final long id) {
        if (ctrl != null) {
            int seq = ctrl.getIntParam("seq");
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

    /**
     * Publish message to a topic. It will attempt to publish regardless of subscription status.
     *
     * @param content payload
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
     */
    public PromisedReply<ServerMessage> publish(T content) throws Exception {
        final long id;
        if (mStore != null) {
            id = mStore.msgSend(this, content);
        } else {
            id = -1;
        }

        if (mAttached) {
            return mTinode.publish(getName(), content).thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            processDelivery(result.ctrl, id);
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
     * Re-send pending messages. Processing will stop on the first error.
     *
     * @return {@link PromisedReply} of the last sent message.
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
     */
    public <ML extends Iterator<Storage.Message<T>> & Closeable> PromisedReply<ServerMessage> publishPending()
            throws Exception {
        Log.d(TAG, "publishPending");

        ML list = mStore.getUnsentMessages(this);
        if (list == null) {
            Log.d(TAG, "getUnreadMessages returned null");
            return new PromisedReply<>((ServerMessage) null);
        }

        PromisedReply<ServerMessage> last = new PromisedReply<>((ServerMessage) null);
        while (list.hasNext()) {
            final Storage.Message<T> msg = list.next();
            Log.d(TAG, "publishing '" + msg.getId() + "'");
            last = mTinode.publish(getName(), msg.getContent());
            last.thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            processDelivery(result.ctrl, msg.getId());
                            return null;
                        }
                    }, null);
        }

        try {
            list.close();
        } catch (IOException ignored) {}

        return last;
    }

    /**
     * Query topic for data or metadata
     */
    public PromisedReply getMeta(MsgGetMeta query) {
        return mTinode.getMeta(getName(), query);
    }

    /**
     * Update topic metadata
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> setMeta(final MsgSetMeta<Pu,Pr,T> meta) throws Exception {
        if (mAttached) {
            return mTinode.setMeta(getName(), meta).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result)
                        throws Exception {
                    update(result.ctrl, meta);
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
     * Update topic description. Calls {@link #setMeta}.
     * @param desc new description (public, private, default access)
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    protected PromisedReply<ServerMessage> setDescription(final MetaSetDesc<Pu,Pr> desc) throws Exception {
        return setMeta(new MsgSetMeta<Pu,Pr,T>(desc, null));
    }

    /**
     * Update topic description. Calls {@link #setMeta}.
     * @param pub new public info
     * @param priv new private info
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> setDescription(final Pu pub, final Pr priv) throws Exception {
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
    public PromisedReply<ServerMessage> updateDefAcs(String auth, String anon)  throws Exception {
        return setDescription(new MetaSetDesc<Pu,Pr>(auth, anon));
    }

    /**
     * Update subscription. Calls {@link #setMeta}.
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    protected PromisedReply<ServerMessage> setSubscription(final MetaSetSub<T> sub) throws Exception {
        return setMeta(new MsgSetMeta<Pu,Pr,T>(null, sub));
    }

    /**
     * Update another user's access mode.
     *
     * @param uid UID of the user to update
     * @param update string which defines the update. It could be a full value or a change.

     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> updateMode(String uid, final String update) throws Exception {
        final Subscription sub;
        if (uid != null) {
            sub = getSubscription(uid);
            if (uid.equals(mTinode.getMyId())) {
                uid = null;
            }
        } else {
            sub = getSubscription(mTinode.getMyId());
        }

        final boolean self = (uid == null);

        if (sub == null) {
            throw new NotSubscribedException();
        }

        if (mDesc.acs == null) {
            mDesc.acs = new Acs();
        }

        final AcsHelper mode = uid == null ? mDesc.acs.getWantHelper() : sub.acs.getGivenHelper();
        if (mode.update(update)) {
            return setSubscription(new MetaSetSub<T>(uid, mode.toString(), null))
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            if (result.ctrl != null) {
                                if (self) {
                                    mDesc.acs.merge((Map) result.ctrl.params);
                                }
                                sub.acs.merge((Map) result.ctrl.params);
                            }
                            return null;
                        }
                    }, null);
        }
        // The state is unchanged, return resolved promise.
        return new PromisedReply<>((ServerMessage) null);
    }
    /**
     * Invite user to the topic.
     *
     * @param uid ID of the user to invite to topic
     * @param mode access mode granted to user
     * @param invite content opf the invite message
     *
     * @throws NotConnectedException if there is no connection to the server
     * @throws NotSynchronizedException if the topic has not yet been synchronized with the server
     */
    public PromisedReply<ServerMessage> invite(String uid, String mode, T invite)  throws Exception {

        final Subscription<Pu,Pr> sub;
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

        return setSubscription(new MetaSetSub<>(uid, mode, invite)).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                        if (mStore != null) {
                            mStore.subUpdate(Topic.this, sub);
                        }
                        if (mListener != null) {
                            mListener.onMetaSub(sub);
                            mListener.onSubsUpdated();
                        }
                        return null;
                    }
                }, null);
    }

    /**
     * Eject subscriber from topic.
     *
     * @param uid id of the user to unsubscribe from the topic
     * @param ban ban user (set mode.Given = 'X')
     *
     * @throws NotSubscribedException if the user is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     * @throws NotSynchronizedException if the topic has not yet been synchronized with the server
     */
    public PromisedReply<ServerMessage> eject(String uid, boolean ban) throws Exception {
        final Subscription<Pu,Pr> sub = getSubscription(uid);

        if (sub == null) {
            throw new NotSubscribedException();
        }

        if (ban) {
            // Banning someone means the mode is set to 'X' but subscription is persisted.
            return invite(uid, "X", null);
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
            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
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
     * Delete messages with seq value up to <b>before</b>.
     *
     * @param before delete messages with id up to this
     * @param hard hard-delete messages
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply delMessages(final int before, final boolean hard) throws Exception {
        if (mStore != null) {
            mStore.msgMarkToDelete(this, before);
        }
        if (mAttached) {
            return mTinode.delMessage(getName(), before, hard).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                    if (mStore != null) {
                        mStore.msgDelete(Topic.this, before);
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
    public PromisedReply<ServerMessage> delMessages(final int[] list, final boolean hard) throws Exception {
        if (mStore != null) {
            mStore.msgMarkToDelete(this, list);
        }
        if (mAttached) {
            return mTinode.delMessage(getName(), list, hard).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                    if (mStore != null) {
                        mStore.msgDelete(Topic.this, list);
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
     * Delete topic
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> delete() throws Exception {
        if (mAttached) {
            return mTinode.delTopic(getName());
        }

        if (mTinode.isConnected()) {
            throw new NotSubscribedException();
        }

        throw new NotConnectedException();
    }

    /**
     * Let server know the seq id of the most recent received/read message.
     *
     * @param what "read" or "recv" to indicate which action to report
     */
    protected int noteReadRecv(NoteType what) {
        int result = 0;

        try {
            switch (what) {
                case RECV:
                    if (mDesc.recv < mDesc.seq) {
                        mTinode.noteRecv(getName(), mDesc.seq);
                        result = mDesc.recv = mDesc.seq;
                    }
                    break;

                case READ:
                    if (mDesc.read < mDesc.seq) {
                        mTinode.noteRead(getName(), mDesc.seq);
                        result = mDesc.read = mDesc.seq;
                    }
                    break;
            }
        } catch (NotConnectedException ignored) {}

        return result;
    }

    /** Notify the server that the client read the message */
    public int noteRead() {
        int result = noteReadRecv(NoteType.READ);
        if (mStore != null) {
            mStore.setRead(this, result);
        }
        return result;
    }

    /** Notify the server that the messages is stored on the client */
    public int noteRecv() {
        int result = noteReadRecv(NoteType.RECV);
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

    @SuppressWarnings("WeakerAccess")
    protected JavaType getTypeOfDataPacket() {
        return mTypeOfDataPacket;
    }

    @SuppressWarnings("WeakerAccess")
    protected JavaType getTypeOfMetaPacket() {
        return mTypeOfMetaPacket;
    }

    public String getName() {
        return mName;
    }

    protected void setName(String name) {
        mName = name;
    }

    @SuppressWarnings("WeakerAccess")
    protected int loadSubs() {
        Collection<Subscription> subs = mStore != null ? mStore.getSubscriptions(this) : null;
        if (subs == null) {
            return 0;
        }

        for (Subscription sub : subs) {
            if (mSubsUpdated.before(sub.updated)) {
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
    protected void addSubToCache(Subscription<Pu,Pr> sub) {
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
    protected void removeSubFromCache(Subscription<Pu,Pr> sub) {
        if (mSubs != null) {
            mSubs.remove(sub.user);
        }
    }


    public Subscription<Pu,Pr> getSubscription(String key) {
        if (mSubs == null) {
            loadSubs();
        }
        return mSubs != null ? mSubs.get(key) : null;
    }

    public Collection<Subscription<Pu,Pr>> getSubscriptions() {
        if (mSubs == null) {
            loadSubs();
        }
        return mSubs != null ? mSubs.values() : null;
    }

    public boolean isAttached() {
        return mAttached;
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
            Collection<Subscription<Pu,Pr>> subs = getSubscriptions();
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
            Collection<Subscription<Pu,Pr>> subs = getSubscriptions();
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
        TopicType tp = TopicType.UNKNOWN;
        if (name != null) {
            if (name.equals(Tinode.TOPIC_ME)) {
                tp = TopicType.ME;
            } else if (name.equals(Tinode.TOPIC_FND)) {
                tp = TopicType.FND;
            } else if (name.startsWith(Tinode.TOPIC_GRP_PREFIX) || name.startsWith(Tinode.TOPIC_NEW)) {
                tp = TopicType.GRP;
            } else if (name.startsWith(Tinode.TOPIC_P2P_PREFIX) || name.startsWith(Tinode.TOPIC_USR_PREFIX)) {
                tp = TopicType.P2P;
            }
        }
        return tp;
    }

    public TopicType getTopicType() {
        return getTopicTypeByName(mName);
    }

    public static boolean getIsNewByName(String name) {
        return name.startsWith(Tinode.TOPIC_USR_PREFIX) ||
                name.startsWith(Tinode.TOPIC_NEW);  // "newRANDOM" when the topic was locally initialized but not yet
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
                mListener.onLeave(code, reason);
            }
        }
    }

    protected void routeMeta(MsgServerMeta<Pu,Pr> meta) {
        //Log.d(TAG, "Generic.routeMeta");
        if (meta.desc != null) {
            routeMetaDesc(meta);
        }
        if (meta.sub != null) {
            routeMetaSub(meta);
        }

        if (mListener != null) {
            mListener.onMeta(meta);
        }
    }

    protected void routeMetaDesc(MsgServerMeta<Pu,Pr> meta) {
        update(meta.desc);

        if (mListener != null) {
            mListener.onMetaDesc(meta.desc);
        }
    }

    protected void routeMetaSub(MsgServerMeta<Pu,Pr> meta) {
        //Log.d(TAG, "Generic.routeMetaSub");
        // In case of a generic (non-'me') topic, meta.sub contains topic subscribers.
        // I.e. sub.user is set, but sub.topic is equal to current topic.
        for (Subscription<Pu,Pr> newsub : meta.sub) {
            Subscription<Pu,Pr> sub = getSubscription(newsub.user);
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

            if (mListener != null) {
                mListener.onMetaSub(sub);
            }
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }


    protected void routeData(MsgServerData<T> data) {
        if (data.seq > mDesc.seq) {
            mDesc.seq = data.seq;
        }

        if (mStore != null && mStore.msgReceived(this, getSubscription(data.from), data) > 0) {
            noteRecv();
        }

        if (mListener != null) {
            mListener.onData(data);
        }
    }

    protected void routePres(MsgServerPres pres) {
        Subscription<Pu,Pr> sub = getSubscription(pres.src);
        if (sub != null) {
            // FIXME(gene): add actual handler
            MsgServerPres.What what = MsgServerPres.parseWhat(pres.what);
            if (what == MsgServerPres.What.ON) {
                sub.online = true;
            } else if (what == MsgServerPres.What.OFF) {
                sub.online = false;
            }
        }

        if (mListener != null) {
            mListener.onPres(pres);
        }
    }

    protected void routeInfo(MsgServerInfo info) {
        if (!info.what.equals(Tinode.NOTE_KP)) {
            Subscription sub = getSubscription(info.from);
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
                        if (mStore != null) {
                            mStore.msgReadByRemote(sub, info.seq);
                        }
                        break;
                    default:
                        break;
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

    public synchronized void setListener(Listener <Pu,Pr,T> l) {
        mListener = l;
    }

    public static class Listener<PPu,PPr,Tt> {

        public void onSubscribe(int code, String text) {}
        public void onLeave(int code, String text) {}

        /**
         * Process {data} message.
         * @param data data packet
         */
        public void onData(MsgServerData<Tt> data) { }

        public void onContactUpdate(String what, Subscription<PPu,PPr> sub) {}
        public void onInfo(MsgServerInfo info) {}
        public void onMeta(MsgServerMeta<PPu,PPr> meta) {}
        public void onMetaSub(Subscription<PPu,PPr> sub) {}
        public void onMetaDesc(Description<PPu,PPr> desc) {}
        public void onSubsUpdated() {}
        public void onPres(MsgServerPres pres) {}
        public void onOnline(boolean online) {}
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

        public MetaGetBuilder withGetSub(Date ims, Integer limit) {
            meta.setSub(ims, limit);
            return this;
        }

        public MetaGetBuilder withGetSub() {
            return withGetSub(topic.getSubsUpdated(), null);
        }

        public MsgGetMeta build() {
            return meta;
        }
    }
}
