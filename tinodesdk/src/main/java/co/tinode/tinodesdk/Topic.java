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

    protected String mName;
    /** the mName could be invalid: "new" or "usrXXX */
    protected boolean isValidName = false;

    // Server-provided values:
    protected Date mCreated;
    protected Date mUpdated;
    protected AccessMode mMode;

    // For P2P topics the UID of the other party, server-provided
    protected String mWith;

    protected Description<Pu,Pr> mDescription;

    protected Tinode mTinode;

    // Cache of topic subscribers indexed by userID
    protected HashMap<String,Subscription<Pu,Pr>> mSubs;
    protected List<MsgServerData<T>> mMessages;

    protected boolean mAttached;
    protected Listener<Pu,Pr,T> mListener;

    protected long mLastKeyPress = 0;

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

        mName = name;
        isValidName = getTopicTypeByName(name) != TopicType.UNKNOWN;

        mListener = l;
        mAttached = false;
        mSubs = new HashMap<>();
        mMessages = new ArrayList<>();

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
                            topicLeft();
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
        Subscription<?,?> sub = mSubs.get(mTinode.getMyId());
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
        } else {
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
    }

    public Pu getPublic() {
        return mDescription != null ? mDescription.pub : null;
    }

    /**
     * @return The number of messages stored in local cache
     */
    public int getCachedMsgCount() {
        return mMessages.size();
    }

    /**
     * @return The SeqID of the latest message stored in local cache.
     */
    public int lastCachedMsgSeq() {
        if (mMessages.isEmpty()) {
            return 0;
        }
        return mMessages.get(mMessages.size()-1).seq;
    }

    /**
     * @return The SeqID of the earliest message in cache.
     */
    public int firstCachedMsgSeq() {
        if (mMessages.isEmpty()) {
            return 0;
        }
        return mMessages.get(0).seq;
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

    /**
     * Given a sender UID, return a integer index of the sender within the topic. The index is guaranteed
     * to be a small number (< 16), unchanging for the duration of the session.
     *
     * @param sender sender UID to check
     *
     * @return index of the given sender or -1 if sender is not found;
     *
     */
    public int getSenderIndex(String sender) {
        Subscription s = mSubs.get(sender);
        if (s == null) {
            return -1;
        }
        return s.getTopicIndex();
    }

    /**
     * Check if UID is equal to the current user UID
     *
     * @param sender sender UID to check
     *
     * @return true if the sender UID is the same as current user UID
     */
    public boolean isMyMessage(String sender) {
        return sender.equals(mTinode.getMyId());
    }

    public MsgServerData<T> getMessageAt(int position) {
        return mMessages.get(position);
    }

    public Subscription<Pu,Pr> getSubscription(String key) {
        return mSubs.get(key);
    }

    public Collection<Subscription<Pu,Pr>> getSubscriptions() {
        return mSubs.values();
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
        for (Subscription<Pu,Pr> sub: mSubs.values()) {
            if ((getTopicTypeByName(sub.topic).val() & type.val()) != 0) {
                if ((marker == null) || (sub.updated != null && marker.before(sub.updated))) {
                    result.add(sub);
                }
            }
        }
        return result;
    }

    protected void setListener(Listener<Pu,Pr,T> l) {
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
            Iterator it = mSubs.entrySet().iterator();
            String me = mTinode.getMyId();
            while (it.hasNext()) {
                Subscription s = (Subscription) ((Map.Entry) it.next()).getValue();
                if (!s.user.equals(me) && s.recv >= seq) {
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
            Iterator it = mSubs.entrySet().iterator();
            String me = mTinode.getMyId();
            while (it.hasNext()) {
                Subscription s = (Subscription) ((Map.Entry) it.next()).getValue();
                if (!s.user.equals(me) && s.read >= seq) {
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
                mName = name;
                mTinode.registerTopic(this);
            }

            if (mListener != null) {
                mListener.onSubscribe(200, "subscribed");
            }
        }
    }

    /** Generate a Subscription object from the Topic object */
    protected Subscription<Pu,Pr> toSubscription() {
        Subscription<Pu,Pr> sub = new Subscription<>();
        sub.topic = mName;
        sub.updated = mUpdated;
        sub.mode = mMode != null ? mMode.toString() : null;
        sub.with = mWith;
        if (mDescription != null) {
            sub.read = mDescription.read;
            sub.recv = mDescription.recv;
            sub.clear = mDescription.clear;
            sub.priv = mDescription.priv;

            sub.seq = mDescription.seq;
            sub.pub = mDescription.pub;
        }

        return sub;
    }

    /**
     * Called when the topic receives leave() confirmation
     */
    protected void topicLeft() {
        if (mAttached) {
            mAttached = false;

            if (mListener != null) {
                mListener.onLeave(503, "connection lost");
            }
        }
    }

    protected void routeMeta(MsgServerMeta<Pu,Pr> meta) {
        if (meta.desc != null) {
            processMetaDesc(meta.desc);
        }
        if (meta.sub != null) {
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

            // TODO(gene): do I need to save it to mSubs too?
        }
        mMessages.add(data);

        if (mListener != null && mListener.onData(data)) {
            noteRecv();
        }

        MeTopic me = mTinode.getMeTopic();
        if (me != null) {
            me.setMsgSeq(getName(), data.seq);
        }
    }

    protected void routePres(MsgServerPres pres) {
        Subscription<Pu,Pr> sub = mSubs.get(pres.topic);
        if (sub != null) {
            // FIXME(gene): add actual handler
            sub.online = pres.what.equals("on");
        }
        if (mListener != null) {
            mListener.onPres(pres);
        }
    }

    protected void routeInfo(MsgServerInfo info) {
        if (!info.what.equals("kp")) {
            Subscription sub = mSubs.get(info.from);
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

    // Called by Tinode when meta.sub is recived.
    protected void processMetaSubs(Subscription<Pu,Pr>[] subs) {
        for (Subscription<Pu,Pr> sub : subs) {
            // Response to get.sub on 'me' topic does not have .user set
            if (sub.user != null && !sub.user.equals("")) {
                // Cache user in the topic as well.
                Subscription<Pu,Pr> cached = mSubs.get(sub.user);
                if (cached != null) {
                    cached.merge(sub);
                } else {
                    sub.setTopicIndex(mSubs.size());
                    mSubs.put(sub.user, sub);
                }

                // TODO(gene): Save the object to global cache.
            }

            if (mListener != null) {
                mListener.onMetaSub(sub);
            }
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }

    public static class Listener<PPu,PPr,Tt> {
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
        public void onPresOnline(boolean online) {}
        public void onPresUpd() {}
        public void onPresUa(String ua) {}
    }
}
