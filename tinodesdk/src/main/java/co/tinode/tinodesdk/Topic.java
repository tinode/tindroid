/**
 * Created by gene on 06/02/16.
 */

package co.tinode.tinodesdk;

import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.Defacs;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.Subscription;

import com.fasterxml.jackson.databind.JavaType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Date;
import java.util.Map;


/**
 *
 * Class for handling communication on a single topic
 *
 */
public class Topic<Pu,Pr,T> {
    private static final String TAG = "co.tinode.tinodesdk.Topic";

    protected JavaType mTypeOfDataPacket = null;
    protected JavaType mTypeOfMetaPacket = null;

    protected String mName;

    protected Description<Pu,Pr> mDescription;

    protected Tinode mTinode;

    protected HashMap<String,Subscription<Pu,Pr>> mSubs;
    protected List<MsgServerData<T>> mMessages;

    protected boolean mSubscribed;
    protected Listener<Pu,Pr,T> mListener;

    /**
     * Create a named topic.
     *
     * @param tinode instance of Tinode object to communicate with the server
     * @param name name of the topic
     * @param l event listener, optional
     */
    public Topic(Tinode tinode, String name, Listener<Pu,Pr,T> l) {
        mTinode = tinode;
        mName = name;
        mListener = l;
        mSubscribed = false;
        mSubs = new HashMap<>();
        mMessages = new ArrayList<>();
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
     * Subscribe topic
     *
     * @throws IOException
     */
    public PromisedReply subscribe() throws Exception {
        if (!mSubscribed) {
            MsgGetMeta getParams = null;
            if (mDescription == null || mDescription.updated == null) {
                getParams = new MsgGetMeta();
                getParams.what = "desc sub data";
            }

            return mTinode.subscribe(getName(), null, getParams).thenApply(
                    new PromisedReply.SuccessListener() {
                        @Override
                        public PromisedReply onSuccess(Object result) throws Exception {
                            subscribed();
                            return null;
                        }
                    }, null);
        }
        return null;
    }

    /**
     * Leave topic
     * @param unsub true to disconnect and unsubscribe from topic, otherwise just disconnect
     *
     * @throws IOException
     */
    public PromisedReply leave(boolean unsub) throws IOException {
        if (mSubscribed) {
            return mTinode.leave(getName(), unsub);
        }
        return null;
    }

    /**
     * Publish message to a topic. It will attempt to publish regardless of subscription status.
     *
     * @param content payload
     * @throws IOException
     */
    public PromisedReply publish(T content) throws IOException {
        return mTinode.publish(getName(), content);
    }

    /**
     * Let server know the seq id of the most recent message
     */
    public void noteRecv() {
        mTinode.note(getName(), "recv", mDescription.seq);
    }

    public void noteRead() {
        mTinode.note(getName(), "read", mDescription.seq);
    }

    public void noteKeyPress() {
        mTinode.noteKeyPress(getName());
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
        return mDescription.pub;
    }

    public int getMessageCount() {
        return mMessages.size();
    }

    /**
     * Given a sender UID, return a integer index of the sender within the topic. The index is guaranteed
     * to be a small number (< 16), consistent within a session.
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

    public MsgServerData<T> getMessageAt(int position) {
        return mMessages.get(position);
    }

    public Subscription<Pu,Pr> getSubscription(String key) {
        return mSubs.get(key);
    }

    protected void setListener(Listener<Pu,Pr,T> l) {
        mListener = l;
    }

    public boolean isSubscribed() {
        return mSubscribed;
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

    /**
     * Called when the topic receives subscription confirmation
     */
    protected void subscribed() {
        if (!mSubscribed) {
            mSubscribed = true;

            if (mListener != null) {
                mListener.onSubscribe(200, "subscribed");
            }
        }
    }

    protected void disconnected() {
        if (mSubscribed) {
            mSubscribed = false;

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
        }
        int count = mMessages.size();
        if (count == 0) {
            data.setDisplay(MsgServerData.DisplayAs.SINGLE);
        } else {
            MsgServerData<?> prev = mMessages.get(count - 1);
            if (prev.from.equals(data.from)) {
                if (prev.getDisplay() == MsgServerData.DisplayAs.SINGLE) {
                    prev.setDisplay(MsgServerData.DisplayAs.FIRST);
                } else if (prev.getDisplay() == MsgServerData.DisplayAs.LAST) {
                    prev.setDisplay(MsgServerData.DisplayAs.MIDDLE);
                }
                data.setDisplay(MsgServerData.DisplayAs.LAST);
            } else {
                data.setDisplay(MsgServerData.DisplayAs.SINGLE);
            }
        }
        mMessages.add(data);

        if (mListener != null) {
            mListener.onData(data);
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
        // TODO(gene): if it's online/offline, updated cached sender

        mListener.onInfo(info);
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
        public void onData(MsgServerData<Tt> data) {}
        public void onPres(MsgServerPres pres) {}
        public void onContactUpdate(String what, Subscription<PPu,PPr> sub) {}
        public void onInfo(MsgServerInfo info) {}
        public void onMeta(MsgServerMeta<PPu,PPr> meta) {}
        public void onMetaSub(Subscription<PPu,PPr> sub) {}
        public void onMetaDesc(Description<PPu,PPr> desc) {}
        public void onSubsUpdated() {}
    }
}
