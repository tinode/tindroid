/**
 * Created by gene on 06/02/16.
 */

package co.tinode.tinodesdk;

import android.util.Log;

import co.tinode.tinodesdk.model.AccessMode;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.LastSeen;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.StringTokenizer;

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
    // the mName could be invalid: 'new' or 'usrXXX'
    protected boolean isValidName = false;

    /** The mStore is set by Tinode when the topic calls {@link Tinode#registerTopic(Topic)} */
    Storage mStore = null;

    // Server-provided values:
    protected AccessMode mMode;
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

        mMode   = new AccessMode(sub.mode);

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

        if (l != null) {
            l.mTopic = this;
        }

        mListener = l;
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
        this(tinode, Tinode.TOPIC_NEW, l);
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
     * @param sub updated topic parameters
     */
    protected void update(Subscription<Pu,Pr> sub) {
        int changed = 0;

        if (sub.mode != null && !sub.mode.equals(mMode.toString())) {
            mMode = new AccessMode(sub.mode);
            changed ++;
        }

        if (mDesc.merge(sub)) {
            changed++;
        }

        if (changed > 0 && mStore != null) {
            mStore.topicUpdate(this);
        }
    }

    /**
     * Update topic parameters from a Description object.
     * @param desc updated topic parameters
     */
    protected void update(Description<Pu,Pr> desc) {
        int changed = 0;

        if (desc.acs != null && desc.acs.mode != null && !desc.acs.mode.equals(mMode.toString())) {
            mMode = new AccessMode(desc.acs.mode);
            changed ++;
        }

        if (mDesc.merge(desc)) {
            changed++;
        }

        if (changed > 0 && mStore != null) {
            mStore.topicUpdate(this);
        }
    }

    protected void update(Date updated, MsgSetMeta<Pu,Pr,?> meta) {

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

    public Date getDeleted() {
        return mDesc.deleted;
    }
    public void setDeleted(Date deleted) {
        mDesc.deleted = deleted;
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

    public Storage.Range getCachedMessageRange() {
        return mStore == null ? null : mStore.getCachedMessagesRange(this);
    }

    public AccessMode getMode() {
        return mMode;
    }
    public void setMode(AccessMode mode) {
        mMode = mode;
    }
    public void setMode(String mode) {
        mMode = new AccessMode(mode);
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

            //Log.d(TAG, "Topic[" + mName + "].setOnline(" + online + ");");

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
     * @throws Exception
     */
    public PromisedReply<ServerMessage> subscribe() throws Exception {
        return subscribe(null, subscribeParamGetBuilder()
                .withGetDesc().withGetData().withGetSub().build());
    }

    /**
     * Subscribe to topic with parameters
     *
     * @throws NotConnectedException if there is no live connection to the server
     * @throws IllegalStateException if the client is already subscribed to the given topic
     */
    public PromisedReply<ServerMessage> subscribe(MsgSetMeta<Pu,Pr,?> set, MsgGetMeta get)
            throws Exception {
        if (!mAttached) {

            if (!mTinode.isConnected()) {
                throw new NotConnectedException();
            }

            return mTinode.subscribe(getName(), set, get).thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage msg)
                                throws Exception {
                            if (msg.ctrl != null && msg.ctrl.params != null) {
                                subscribed(msg.ctrl.topic, (String)msg.ctrl.params.get("mode"));
                            }
                            return null;
                        }
                    }, null);
        }

        throw new IllegalStateException("Already subscribed");
    }

    public MetaGetBuilder subscribeParamGetBuilder() {
        return new MetaGetBuilder(this);
    }

    /**
     * Leave topic
     * @param unsub true to disconnect and unsubscribe from topic, otherwise just disconnect
     *
     * @throws IllegalStateException if the client is not subscribed to the topic
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
            throw new IllegalStateException("Not subscribed");
        }

        throw new NotConnectedException();
    }

    /**
     * Leave topic without unsubscribing
     *
     * @throws IllegalStateException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to server
     */
    public PromisedReply<ServerMessage> leave() throws Exception {
        return leave(false);
    }

    /**
     * Publish message to a topic. It will attempt to publish regardless of subscription status.
     *
     * @param content payload
     *
     * @throws IllegalStateException if the client is not subscribed to the topic
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
                            if (result.ctrl != null) {
                                int seq = result.ctrl.getIntParam("seq");
                                setSeq(seq);
                                if (id > 0 && mStore != null) {
                                    if (mStore.msgDelivered(id, result.ctrl.ts, seq)) {
                                        setRecv(seq);
                                    }
                                } else {
                                    setRecv(seq);
                                }
                                setRead(seq);
                            }
                            return null;
                        }
                    }, null);
        }

        if (mTinode.isConnected()) {
            throw new IllegalStateException("Not subscribed");
        }

        throw new NotConnectedException();
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
     * @throws IllegalStateException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> setMeta(final MsgSetMeta<Pu,Pr,T> meta) throws Exception {
        if (mAttached) {
            return mTinode.setMeta(getName(), meta).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result)
                        throws Exception {
                    update(result.ctrl.ts, meta);
                    return null;
                }
            }, null);
        }
        if (mTinode.isConnected()) {
            throw new IllegalStateException("Not subscribed");
        }

        throw new NotConnectedException();
    }

    /**
     * Delete messages with seq value up to <b>before</b>.
     *
     * @param before delete messages with id up to this
     * @param hard hard-delete messages
     *
     * @throws IllegalStateException if the client is not subscribed to the topic
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
                    mStore.msgDelete(Topic.this, before);
                    return null;
                }
            }, null);
        }

        if (mTinode.isConnected()) {
            throw new IllegalStateException("Not subscribed");
        }

        throw new NotConnectedException();
    }

    /**
     * Delete messages with id in the provided list.
     *
     * @param list delete messages with ids in this list
     * @param hard hard-delete messages
     *
     * @throws IllegalStateException if the client is not subscribed to the topic
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
                    mStore.msgDelete(Topic.this, list);
                    return null;
                }
            }, null);
        }

        if (mTinode.isConnected()) {
            throw new IllegalStateException("Not subscribed");
        }

        throw new NotConnectedException();
    }

    /**
     * Delete topic
     *
     * @throws IllegalStateException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> delete() throws Exception {
        if (mAttached) {
            return mTinode.delTopic(getName()).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                    mTinode.unregisterTopic(Topic.this);
                    return null;
                }
            }, null);
        }

        if (mTinode.isConnected()) {
            throw new IllegalStateException("Not subscribed");
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

        switch (what) {
            case RECV:
                if (mDesc.recv < mDesc.seq) {
                    try {
                        mTinode.noteRecv(getName(), mDesc.seq);
                        result = mDesc.recv = mDesc.seq;
                    } catch (NotConnectedException ignored) {}
                }
                break;
            case READ:
                if (mDesc.read < mDesc.seq) {
                    try {
                        mTinode.noteRead(getName(), mDesc.seq);
                        result = mDesc.read = mDesc.seq;
                    } catch (NotConnectedException ignored) {}
                }
                break;
        }

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


    protected JavaType getTypeOfDataPacket() {
        return mTypeOfDataPacket;
    }

    protected JavaType getTypeOfMetaPacket() {
        return mTypeOfMetaPacket;
    }

    public String getName() {
        return mName;
    }

    protected void setName(String name) {
        mName = name;
        isValidName = Topic.getTopicTypeByName(name) != TopicType.UNKNOWN;
    }

    public Pu getPublic() {
        return mDesc.pub;
    }


    protected int loadSubs() {
        Collection<Subscription> subs = mStore.getSubscriptions(this);
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

    public Subscription<Pu,Pr> getSubscription(String key) {
        if (mSubs == null) {
            loadSubs();
        }
        return mSubs != null ? mSubs.get(key) : null;
    }

    public Collection<Subscription<Pu,Pr>> getSubscriptions() {
        if (mSubs != null) {
            loadSubs();
        }
        return mSubs != null ? mSubs.values() : null;
    }

    public void setListener(Listener<Pu,Pr,T> l) {
        if (l != null) {
            l.mTopic = this;
        }
        mListener = l;
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
            if (name.equals("me")) {
                tp = TopicType.ME;
            } else if (name.equals("fnd")) {
                tp = TopicType.FND;
            } else if (name.startsWith("grp")) {
                tp = TopicType.GRP;
            } else if (name.startsWith("p2p")) {
                tp = TopicType.P2P;
            }
        }
        return tp;
    }

    public TopicType getTopicType() {
        return getTopicTypeByName(mName);
    }

    /**
     * Called when the topic receives subscription confirmation
     * @param name server-provided topic name; could be different from the current name
     */
    protected void subscribed(String name, String mode) {
        if (!mAttached) {
            mAttached = true;

            mMode = new AccessMode(mode);

            if (!isValidName) {
                setName(name);
            }
            mTinode.registerTopic(this);

            if (mListener != null) {
                mListener.onSubscribe(200, "subscribed");
            }
        }
    }

    /**
     * Called when the topic receives leave() confirmation
     *
     * @param unsub - not just detached but also unsubscribed
     * @param code result code, always 200
     * @param reason usually "OK"
     */
    protected void topicLeft(boolean unsub, int code, String reason) {
        if (mAttached) {
            mAttached = false;

            // Don't change topic online status here. Change it in the 'me' topic

            if (unsub) {
                mTinode.unregisterTopic(this);
            }

            if (mListener != null) {
                mListener.onLeave(code, reason);
            }
        }
    }

    protected void routeMeta(MsgServerMeta<Pu,Pr> meta) {
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
        // In case of a generic (non-'me') topic, meta.subcontains topic subscribers.
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

    public static class Listener<PPu,PPr,Tt> {
        protected Topic<PPu,PPr,Tt> mTopic;

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
            return withGetSub(topic.getUpdated(), null);
        }

        public MsgGetMeta build() {
            return meta;
        }
    }
}
