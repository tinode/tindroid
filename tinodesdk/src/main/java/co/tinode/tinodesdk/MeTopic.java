package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * MeTopic manages contact list. MeTopic::Private is unused.
 */
public class MeTopic<DP> extends Topic<DP,PrivateType,DP,PrivateType> {
    private static final String TAG = "MeTopic";

    public MeTopic(Tinode tinode, Listener<DP,PrivateType,DP,PrivateType> l) {
        super(tinode, Tinode.TOPIC_ME, l);
    }

    protected MeTopic(Tinode tinode, Description<DP,PrivateType> desc) {
        super(tinode, Tinode.TOPIC_ME, desc);
    }

    public void setTypes(JavaType typeOfPu) {
        mTinode.setMeTypeOfMetaPacket(typeOfPu);
    }

    @Override
    protected void addSubToCache(Subscription<DP,PrivateType> sub) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void removeSubFromCache(Subscription sub) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PromisedReply<ServerMessage> publish(Drafty content) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public PromisedReply<ServerMessage> publish(String content) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Subscription getSubscription(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Subscription<DP,PrivateType>> getSubscriptions() {
        throw new UnsupportedOperationException();
    }

    public PrivateType getPriv() {
        return null;
    }
    public void setPriv(PrivateType priv) { /* do nothing */ }

    @Override
    @SuppressWarnings("unchecked")
    protected void routeMetaSub(MsgServerMeta<DP,PrivateType,DP,PrivateType> meta) {
        // Log.d(TAG, "Me:routeMetaSub");
        for (Subscription sub : meta.sub) {
            // Log.d(TAG, "Sub " + sub.topic + " is " + sub.online);
            Topic topic = mTinode.getTopic(sub.topic);
            if (topic != null) {
                // This is an existing topic.
                if (sub.deleted != null) {
                    // Expunge deleted topic
                    mTinode.unregisterTopic(sub.topic);
                } else {
                    // Update its record in memory and in the database.
                    topic.update(sub);
                    // Notify topic to update self.
                    if (topic.mListener != null) {
                        topic.mListener.onContUpdate(sub);
                    }
                }
            } else if (sub.deleted == null) {
                // This is a new topic. Register it and write to DB.
                mTinode.registerTopic(mTinode.newTopic(sub));
            }

            if (mListener != null) {
                mListener.onMetaSub(sub);
            }
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }

    @Override
    protected void routePres(MsgServerPres pres) {
        // FIXME(gene): pres.src may contain UID
        Topic topic = mTinode.getTopic(pres.src);
        MsgServerPres.What what = MsgServerPres.parseWhat(pres.what);
        if (topic != null) {
            switch (what) {
                case ON: // topic came online
                    topic.setOnline(true);
                    break;

                case OFF: // topic went offline
                    topic.setOnline(false);
                    topic.setLastSeen(new Date());
                    break;

                case MSG: // new message received
                    topic.setSeq(pres.seq);
                    break;

                case UPD: // pub/priv updated
                    this.getMeta(getMetaGetBuilder().withGetSub(pres.src).build());
                    break;

                case ACS: // access mode changed
                    if (topic.updateAccessMode(pres.dacs) && mStore != null) {
                        mStore.topicUpdate(topic);
                    }
                    break;

                case UA: // user agent changed
                    topic.setLastSeen(new Date(), pres.ua);
                    break;

                case RECV: // user's other session marked some messges as received
                    topic.setRecv(pres.seq);
                    break;

                case READ: // user's other session marked some messages as read
                    topic.setRead(pres.seq);
                    break;

                case DEL: // messages deleted
                    // TODO(gene): add handling for del
                    break;

                case GONE:
                    // If topic is unknown (==null), then we don't care to unregister it.
                    mTinode.unregisterTopic(pres.src);
                    break;
            }
        } else {
            switch (what) {
                case ACS:
                    Acs acs = new Acs();
                    acs.update(pres.dacs);
                    if (acs.isModeDefined()) {
                        getMeta(getMetaGetBuilder().withGetSub(pres.src).build());
                    } else {
                        Log.d(TAG, "Unexpected access mode in presence: '" + pres.dacs.want + "'/'" + pres.dacs.given + "'");
                    }
                    break;
                default:
                    Log.d(TAG, "Topic not found in me.routePres: " + pres.what + " in " + pres.src);
                    break;
            }
        }

        if (mListener != null) {
            if (what == MsgServerPres.What.GONE) {
                mListener.onSubsUpdated();
            }
            mListener.onPres(pres);
        }
    }

    @Override
    protected void topicLeft(boolean unsub, int code, String reason) {
        super.topicLeft(unsub, code, reason);

        List<Topic> topics = mTinode.getTopics();
        if (topics != null) {
            for (Topic t : topics) {
                t.setOnline(false);
            }
        }
    }

    public static class MeListener<DP> extends Listener<DP,PrivateType,DP,PrivateType> {
        /** {meta} message received */
        public void onMeta(MsgServerMeta<DP,PrivateType,DP,PrivateType> meta) {}
        /** {meta what="sub"} message received, and this is one of the subs */
        public void onMetaSub(Subscription<DP,PrivateType> sub) {}
        /** {meta what="desc"} message received */
        public void onMetaDesc(Description<DP,PrivateType> desc) {}
        /** Called by MeTopic when topic descriptor as contact is updated */
        public void onContUpdate(Subscription<DP,PrivateType> sub) {}
    }

}
