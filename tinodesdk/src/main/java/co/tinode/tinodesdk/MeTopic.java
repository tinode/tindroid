package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.Subscription;

/**
 * MeTopic handles invites and manages contact list
 */
public class MeTopic<Pu, Pr> extends Topic<Pu, Pr> {
    private static final String TAG = "MeTopic";

    public MeTopic(Tinode tinode, Listener<Pu, Pr> l) {
        super(tinode, Tinode.TOPIC_ME, l);
    }

    protected MeTopic(Tinode tinode, Description<Pu, Pr> desc) {
        super(tinode, Tinode.TOPIC_ME, desc);
    }

    @Override
    public void setTypes(JavaType typeOfPu, JavaType typeOfPr) {
        super.setTypes(typeOfPu, typeOfPr);
    }

    @Override
    public void setTypes(Class<?> typeOfPu, Class<?> typeOfPr) {
        this.setTypes(Tinode.getTypeFactory().constructType(typeOfPu),
                Tinode.getTypeFactory().constructType(typeOfPr));
    }

    @Override
    protected void addSubToCache(Subscription<Pu, Pr> sub) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void removeSubFromCache(Subscription<Pu, Pr> sub) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Subscription<Pu, Pr> getSubscription(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Subscription<Pu, Pr>> getSubscriptions() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void routeMetaSub(MsgServerMeta<Pu, Pr> meta) {
        // Log.d(TAG, "Me:routeMetaSub");
        for (Subscription<Pu, Pr> sub : meta.sub) {
            // Log.d(TAG, "Sub " + sub.topic + " is " + sub.online);
            Topic<Pu, Pr> topic = mTinode.getTopic(sub.topic);
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
                mTinode.registerTopic(new Topic<>(mTinode, sub));
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
                    break;

                case MSG: // new message received
                    topic.setSeq(pres.seq);
                    break;

                case UPD: // pub/priv updated
                    this.getMeta(getMetaGetBuilder().withGetSub().build());
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
                    this.getMeta(getMetaGetBuilder().withGetSub().build());
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
}
