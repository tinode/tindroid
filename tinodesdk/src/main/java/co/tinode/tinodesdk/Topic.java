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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * Class for handling communication on a single topic
 *
 */
public class Topic<Pu,Pr,T> {
    private static final String TAG = "tinodesdk.Topic";

    public enum TopicType {
        ME(0x01), FND(0x02), GRP(0x04), P2P(0x08), USER(0x04 | 0x08), UNKNOWN(0x00), ANY(0x01 | 0x02 | 0x04 | 0x08);

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
    protected Description<Pu,Pr> mDescription;
    // Cache of topic subscribers indexed by userID
    protected HashMap<String,Subscription<Pu,Pr>> mSubs = null;
    // Timestamp of the last update to subscriptions. Default: Oct 25, 2014 05:06:02 UTC, incidentally equal
    // to the first few digits of sqrt(2)
    protected Date mSubsUpdated = new Date(1414213562);


    protected boolean mAttached;
    protected Listener<Pu,Pr,T> mListener;

    protected long mLastKeyPress = 0;

    /**
     * Copy constructor
     *
     * @param topic Original instance to copy
     */
    protected Topic(Topic<Pu,Pr,T> topic) {
        mTypeOfDataPacket   = topic.mTypeOfDataPacket;
        mTypeOfMetaPacket   = topic.mTypeOfMetaPacket;
        mName               = topic.mName;
        isValidName         = topic.isValidName;
        mStore              = topic.mStore;
        mMode               = topic.mMode;
        mDescription        = topic.mDescription;
        mSubs               = topic.mSubs;
        mSubsUpdated        = topic.mSubsUpdated;
        mAttached           = topic.mAttached;
        mListener           = topic.mListener;
        mLastKeyPress       = topic.mLastKeyPress;
    }

    /**
     * Create a named topic.
     *
     * @param tinode instance of Tinode object to communicate with the server
     * @param name name of the topic
     * @param l event listener, optional
     */
    public Topic(Tinode tinode, String name, Listener<Pu,Pr,T> l) {
        if (tinode == null) {
            throw new IllegalArgumentException("Tinode cannot be null");
        }
        mTinode = tinode;

        setName(name);

        if (l != null) {
            l.mTopic = this;
        }
        mListener = l;
        mAttached = false;

        if (isValidName) {
            mTinode.registerTopic(this);
        }
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
     * Called by Tinode from {@link Tinode#registerTopic(Topic)}
     *
     * @param store storage object
     */
    void setStorage(Storage store) {
        mStore = store;
    }

    /**
     * Subscribe to topic
     *
     * @throws Exception
     */
    public PromisedReply<ServerMessage> subscribe() throws Exception {
        if (!mAttached) {
            MsgGetMeta getParams = null;
            if (mDescription == null || mDescription.updated == null) {
                getParams = new MsgGetMeta();
            } else {
                // Check if the last received message has lower ID than the last known message.
                // If so, fetch missing messages.
                MeTopic me = mTinode.getMeTopic();
                if (me != null) {
                    int seqId = me.getMsgSeq(mName);
                    if (seqId > mDescription.seq) {
                        MsgGetMeta.GetData data = new MsgGetMeta.GetData();
                        data.since = mDescription.seq;
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
    public PromisedReply<ServerMessage> leave(boolean unsub) throws Exception {
        if (mAttached) {
            return mTinode.leave(getName(), unsub).thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result)
                                throws Exception {
                            topicLeft(result.ctrl.code, result.ctrl.text);
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
    public PromisedReply<ServerMessage> publish(T content) {
        return mTinode.publish(getName(), content);
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
    public PromisedReply setMeta(MsgSetMeta meta) {
        return mTinode.setMeta(getName(), meta);
    }

    /**
     * Delete messages
     */
    public PromisedReply delete(int before, boolean hard) {
        return mTinode.delMessage(getName(), before, hard);
    }

    /**
     * Let server know the seq id of the most recent received/read message.
     *
     * @param what "read" or "recv" to indicate which action to report
     */
    protected int noteReadRecv(NoteType what) {
        if (mDescription == null) {
            mDescription = new Description<>();
        }

        // Save read and received status on subscription.
        Subscription<?,?> sub = getSubscription(mTinode.getMyId());
        int result = 0;
        if (sub != null) {
            switch (what) {
                case RECV:
                    if (sub.recv < mDescription.seq) {
                        mTinode.noteRecv(getName(), mDescription.seq);
                        result = sub.recv = mDescription.seq;
                    }
                    break;
                case READ:
                    if (sub.read < mDescription.seq) {
                        mTinode.noteRead(getName(), mDescription.seq);
                        result = sub.read = mDescription.seq;
                    }
                    break;
            }
        } else if (mTinode.isConnected()) {
            Log.e(TAG, "Subscription not found in topic");
        }

        // Update cached contact with the new count.
        MeTopic me = mTinode.getMeTopic();
        if (me != null) {
            if (what == NoteType.RECV) {
                me.setRecv(mName, mDescription.seq);
            } else {
                me.setRead(mName, mDescription.seq);
            }
        }

        return result;
    }

    public int noteRead() {
        return noteReadRecv(NoteType.READ);
    }

    public int noteRecv() {
        return noteReadRecv(NoteType.RECV);
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
        return mDescription != null ? mDescription.pub : null;
    }

    /**
     * @return SeqID of the latest message sent through topic.
     */
    public int lastMessageSeq() {
        if (mDescription != null) {
            return mDescription.seq;
        }
        return 0;
    }

    private int loadSubs() {
        Collection<Subscription<Pu,Pr>> subs = mStore.getSubscriptions(getName());

        if (subs == null) {
            return 0;
        }

        for (Subscription<Pu,Pr> sub : subs) {
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
     * Check if UID is equal to the current user UID
     *
     * @param sender sender UID to check
     *
     * @return true if the sender UID is the same as current user UID
     */
    public boolean isMyMessage(String sender) {
        return sender != null && sender.equals(mTinode.getMyId());
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

    /**
     * Extract subscriptions with the update timestamp after the marker
     *
     * @param marker timestamp of the last update
     * @return updated subscriptions
     */
    public Collection<Subscription<Pu,Pr>> getUpdatedSubscriptions(Date marker) {
        return getFilteredSubscriptions(marker, TopicType.ANY);
    }

    /**
     * Extract subscriptions with the update timestamp after the marker
     *
     * @param marker timestamp of the last update
     * @param type type of the topic to filter for
     * @return updated subscriptions
     */
    public Collection<Subscription<Pu,Pr>> getFilteredSubscriptions(Date marker, TopicType type) {
        if (marker == null && type == TopicType.ANY) {
            return getSubscriptions();
        } else if (type == TopicType.UNKNOWN) {
            return null;
        }

        ArrayList<Subscription<Pu,Pr>> result = new ArrayList<>();
        for (Subscription<Pu,Pr> sub: getSubscriptions()) {
            if ((getTopicTypeByName(sub.topic).val() & type.val()) != 0) {
                if ((marker == null) || (sub.updated != null && marker.before(sub.updated))) {
                    result.add(sub);
                }
            }
        }
        return result;
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
     */
    protected void topicLeft(int code, String reason) {
        if (mAttached) {
            mAttached = false;

            if (mListener != null) {
                mListener.onLeave(code, reason);
            }
        }
    }

    protected void routeMeta(MsgServerMeta<Pu,Pr> meta) {
        if (meta.desc != null) {
            if (mStore != null) {
                mStore.topicUpdate(getName(), meta.ts, meta.desc);
            }
            processMetaDesc(meta.desc);
        }
        if (meta.sub != null) {
            if (mStore != null) {
                mStore.topicUpdate(getName(), meta.ts, meta.sub);
                loadSubs();
            }
            processMetaSubs(meta.sub);
        }

        if (mListener != null) {
            mListener.onMeta(meta);
        }
    }

    protected void routeData(MsgServerData<T> data) {
        // TODO(gene): cache/save sender

        if (data.seq > mDescription.seq) {
            mDescription.seq = data.seq;
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
                        break;
                    case "read":
                        sub.read = info.seq;
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

    protected void processMetaDesc(Description<Pu,Pr> desc) {
        if (mDescription != null) {
            mDescription.merge(desc);
        } else {
            mDescription = desc;
        }

        if (mListener != null) {
            mListener.onMetaDesc(desc);
        }
    }

    // Subscriptions updated, notify listeners.
    protected void processMetaSubs(Subscription<Pu,Pr>[] unused) {
        for (Subscription<Pu,Pr> sub : getSubscriptions()) {
            if (mListener != null) {
                mListener.onMetaSub(sub);
            }
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }

    public static class Listener<PPu,PPr,Tt> {
        protected Topic<PPu,PPr,Tt> mTopic;

        public void onSubscribe(int code, String text) {}
        public void onLeave(int code, String text) {}

        /**
         * Process {data} message.
         * @param data data packet
         * @return true if Tinode should issue a recv notification, false otherwise
         */
        public boolean onData(MsgServerData<Tt> data) { return false; }
        public void onContactUpdate(String what, Subscription<PPu,PPr> sub) {}
        public void onInfo(MsgServerInfo info) {}
        public void onMeta(MsgServerMeta<PPu,PPr> meta) {}
        public void onMetaSub(Subscription<PPu,PPr> sub) {}
        public void onMetaDesc(Description<PPu,PPr> desc) {}
        public void onSubsUpdated() {}
        public void onPres(MsgServerPres pres) {}
    }
}
