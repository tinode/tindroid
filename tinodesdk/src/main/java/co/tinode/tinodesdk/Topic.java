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

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

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


    protected boolean mAttached;
    protected Listener<Pu,Pr,T> mListener;

    protected long mLastKeyPress = 0;

    private Payload mLocal = null;

    protected boolean mOnline = false;

    protected LastSeen mLastSeen = null;
    /**
     * Copy constructor
     *
     * @param topic Original instance to copy
     */
    protected Topic(Topic<Pu,Pr,T> topic) {
        mTinode             = topic.mTinode;
        mTypeOfDataPacket   = topic.mTypeOfDataPacket;
        mTypeOfMetaPacket   = topic.mTypeOfMetaPacket;
        mName               = topic.mName;
        isValidName         = topic.isValidName;
        mStore              = topic.mStore;
        mMode               = topic.mMode;
        mDesc = topic.mDesc != null ? topic.mDesc : new Description<Pu,Pr>();
        mSubs               = topic.mSubs;
        mSubsUpdated        = topic.mSubsUpdated;
        mAttached           = topic.mAttached;
        mListener           = topic.mListener;
        mLastKeyPress       = topic.mLastKeyPress;
    }


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

        mAttached           = false;
        mListener           = null;
    }

    /**
     * Create a named topic.
     *
     * @param tinode instance of Tinode object to communicate with the server
     * @param name name of the topic
     * @param l event listener, optional
     */
    protected Topic(Tinode tinode, String name, Listener<Pu,Pr,T> l) {
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
        mAttached = false;
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
    protected Topic(Tinode tinode, Listener<Pu,Pr,T> l) {
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
        mTypeOfDataPacket = Tinode.getTypeFactory()
                .constructParametricType(MsgServerData.class, typeOfContent);
        mTypeOfMetaPacket = Tinode.getTypeFactory()
                .constructParametricType(MsgServerMeta.class, typeOfPublic, typeOfPrivate);
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
    public void setTypes(Class<?> typeOfPublic,
                                Class<?> typeOfPrivate, Class<?> typeOfContent) {
        mTypeOfDataPacket = Tinode.getTypeFactory()
                .constructParametricType(MsgServerData.class, typeOfContent);
        mTypeOfMetaPacket = Tinode.getTypeFactory()
                .constructParametricType(MsgServerMeta.class, typeOfPublic, typeOfPrivate);
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

    protected void setLastSeen(Date when, String ua) {
        mLastSeen = new LastSeen(when, ua);
    }
    /**
     * Subscribe to topic
     *
     * @throws Exception
     */
    public PromisedReply<ServerMessage> subscribe() throws Exception {
        if (!mAttached) {
            MsgGetMeta getParams = null;
            if (mDesc == null || mDesc.updated == null) {
                getParams = new MsgGetMeta();
            } else {
                // Check if the last received message has lower ID than the last known message.
                // If so, fetch missing messages.
                MeTopic me = mTinode.getMeTopic();
                if (me != null) {
                    int seqId = me.getMsgSeq(mName);
                    if (seqId > mDesc.seq) {
                        MsgGetMeta.GetData data = new MsgGetMeta.GetData();
                        data.since = mDesc.seq;
                        getParams = new MsgGetMeta(null, null, data);
                    }
                }
            }

            return subscribe(null, getParams);
        }
        throw new IllegalStateException("Already subscribed");
    }

    /**
     * Subscribe to topic with parameters
     *
     * @throws Exception
     */
    public PromisedReply<ServerMessage> subscribe(MsgSetMeta<Pu,Pr,?> set, MsgGetMeta get)
            throws Exception {
        if (!mAttached) {
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

    /**
     * Leave topic
     * @param unsub true to disconnect and unsubscribe from topic, otherwise just disconnect
     *
     * @throws Exception
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
        throw new IllegalStateException("Not subscribed");
    }

    /**
     * Leave topic without unsubscribing
     *
     * @throws Exception
     */
    public PromisedReply<ServerMessage> leave() throws Exception {
        return leave(false);
    }

    /**
     * Publish message to a topic. It will attempt to publish regardless of subscription status.
     *
     * @param content payload
     */
    public PromisedReply<ServerMessage> publish(T content) throws Exception {
        if (mAttached) {
            final long id;
            if (mStore != null) {
                id = mStore.msgSend(getName(), content);
            } else {
                id = -1;
            }

            return mTinode.publish(getName(), content).thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            if (result.ctrl != null && id > 0 && mStore != null) {
                                int seq = (Integer) result.ctrl.params.get("seq");
                                mStore.msgDelivered(id, result.ctrl.ts, seq);
                            }
                            return null;
                        }
                    }, null);
        }

        throw new IllegalStateException("Not subscribed");
    }

    /**
     * Query topic for data or metadata
     */
    public PromisedReply getMeta(MsgGetMeta query) {
        return mTinode.getMeta(getName(), query);
    }

    /**
     * Update topic metadata
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
        throw new IllegalStateException("Not subscribed");
    }

    /**
     * Delete messages
     *
     * @param before delete messages with id up to this
     * @param hard hard-delete messages
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
        throw new IllegalStateException("Not subscribed");
    }
    /**
     * Delete topic
     */
    public PromisedReply delete() throws Exception {
        if (mAttached) {
            return mTinode.delTopic(getName()).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                    mTinode.unregisterTopic(Topic.this);
                    return null;
                }
            }, null);
        }
        throw new IllegalStateException("Not subscribed");
    }

    /**
     * Let server know the seq id of the most recent received/read message.
     *
     * @param what "read" or "recv" to indicate which action to report
     */
    protected int noteReadRecv(NoteType what) {
        if (mDesc == null) {
            mDesc = new Description<>();
        }

        // Save read and received status on subscription.
        Subscription<?,?> sub = getSubscription(mTinode.getMyId());
        int result = 0;
        if (sub != null) {
            switch (what) {
                case RECV:
                    if (sub.recv < mDesc.seq) {
                        mTinode.noteRecv(getName(), mDesc.seq);
                        result = sub.recv = mDesc.seq;
                    }
                    break;
                case READ:
                    if (sub.read < mDesc.seq) {
                        mTinode.noteRead(getName(), mDesc.seq);
                        result = sub.read = mDesc.seq;
                    }
                    break;
            }
        } else if (mTinode.isConnected()) {
            Log.e(TAG, "Subscription not found in topic");
        }
/*
        // Update cached contact with the new count.
        MeTopic me = mTinode.getMeTopic();
        if (me != null) {
            if (what == NoteType.RECV) {
                me.setRecv(mName, mDesc.seq);
            } else {
                me.setRead(mName, mDesc.seq);
            }
        }
*/

        return result;
    }

    public int noteRead() {
        int result = noteReadRecv(NoteType.READ);
        if (mStore != null) {
            mStore.setRead(this, result);
        }
        return result;
    }

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
        long now = System.nanoTime();
        if (now - mLastKeyPress > Tinode.KEY_PRESS_DELAY) {
            mLastKeyPress = now;
            mTinode.noteKeyPress(getName());
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
        return mDesc != null ? mDesc.pub : null;
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
            for (Subscription sub: getSubscriptions()) {
                if (!sub.user.equals(me) && sub.recv >= seq) {
                    count++;
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
            for (Subscription sub: getSubscriptions()) {
                if (!sub.user.equals(me) && sub.read >= seq) {
                    count++;
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
                mTinode.registerTopic(this);
            }

            if (mListener != null) {
                mListener.onSubscribe(200, "subscribed");
            }
        }
    }

    /**
     * Called when the topic receives leave() confirmation
     * @param unsub - not just detached but also unsubscribed
     * @param code result code, always 200
     * @param reason usually "OK"
     */
    protected void topicLeft(boolean unsub, int code, String reason) {
        if (mAttached) {
            mAttached = false;
            mOnline = false;

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
            setOnline(sub.online);

            if (mListener != null) {
                mListener.onMetaSub(sub);
            }
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }


    protected void routeData(MsgServerData<T> data) {
        // TODO(gene): cache/save sender

        if (data.seq > mDesc.seq) {
            mDesc.seq = data.seq;
        }

        if (mStore != null && mStore.msgReceived(getSubscription(data.from), data) > 0) {
            noteRecv();
        }

        if (mListener != null) {
            mListener.onData(data);
        }

        // Update subscription cache
        MeTopic me = mTinode.getMeTopic();
        if (me != null) {
            me.setMsgSeq(getName(), data.seq);
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
        if (!info.what.equals("kp")) {
            Subscription sub = getSubscription(info.from);
            if (sub != null) {
                switch (info.what) {
                    case "recv":
                        sub.recv = info.seq;
                        if (mStore != null) {
                            mStore.msgRecvByRemote(sub, info.seq);
                        }
                        break;
                    case "read":
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
}
