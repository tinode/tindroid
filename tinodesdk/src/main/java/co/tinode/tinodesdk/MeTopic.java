package co.tinode.tinodesdk;

import com.fasterxml.jackson.databind.JavaType;

import co.tinode.tinodesdk.model.Invitation;
import co.tinode.tinodesdk.model.LastSeen;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.Subscription;

import java.util.Date;

/**
 * MeTopic handles invites and manages contact list
 */
public class MeTopic<Pu,Pr,T> extends Topic<Pu,Pr,Invitation<T>> {
    private static final String TAG = "MeTopic";

    public MeTopic(Tinode tinode, Listener<Pu,Pr,Invitation<T>> l) {
        super(tinode, Tinode.TOPIC_ME, l);
    }

    @Override
    public void setTypes(JavaType typeOfPu, JavaType typeOfPr, JavaType typeOfInviteInfo) {
        super.setTypes(typeOfPu, typeOfPr,
                Tinode.getTypeFactory().constructParametricType(Invitation.class, typeOfInviteInfo));
    }

    @Override
    public void setTypes(Class<?> typeOfPu, Class<?> typeOfPr, Class<?> typeOfInviteInfo) {
        this.setTypes(Tinode.getTypeFactory().constructType(typeOfPu),
                Tinode.getTypeFactory().constructType(typeOfPr),
                Tinode.getTypeFactory().constructType(typeOfInviteInfo));
    }
    
    @Override
    protected void processMetaSubs(Subscription<Pu,Pr>[] subs) {

        for (Subscription<Pu,Pr> sub : subs) {
            // Cache user in the topic as well.
            Subscription<Pu,Pr> cached = mSubs.get(sub.topic);
            if (cached != null) {
                cached.merge(sub);
            } else {
                cached = sub;
                mSubs.put(cached.topic, cached);
            }

            // TODO(gene): Save the object to global cache.
            if (mListener != null) {
                mListener.onMetaSub(cached);
            }
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }

    @Override
    protected void routePres(MsgServerPres pres) {
        Subscription<Pu,Pr> sub = mSubs.get(pres.src);
        if (sub != null) {
            MsgServerPres.What what = MsgServerPres.parseWhat(pres.what);
            switch(what) {
                case ON: // topic came online
                    sub.online = true;
                    break;

                case OFF: // topic went offline
                    sub.online = false;
                    if (sub.seen == null) {
                        sub.seen = new LastSeen();
                    }
                    sub.seen.when = new Date();
                    break;

                case MSG: // new message received
                    sub.seq = pres.seq;
                    break;

                case UPD: // desc updated
                    // TODO(gene): request updated description
                    break;

                case UA: // user agent changed
                    sub.seen = new LastSeen(new Date(),pres.ua);
                    break;

                case RECV: // user's other session marked some messges as received
                    sub.recv = Math.max(sub.recv, pres.seq);
                    break;

                case READ: // user's other session marked some messages as read
                    sub.read = Math.max(sub.read, pres.seq);
                    break;

                case DEL: // messages or topic deleted in other session
                    // TODO(gene): add handling for del
            }

            if (mListener != null) {
                mListener.onContactUpdate(pres.what, sub);
            }
        }

        if (mListener != null) {
            mListener.onPres(pres);
        }
    }

    /**
     * Set read value, update listener
     *
     * @param topicName name of the contact to update
     * @param seq the 'read' value
     */
    protected void setRead(String topicName, int seq) {
        Subscription<Pu,Pr> sub = mSubs.get(topicName);
        boolean ret = false;
        if (sub != null && sub.read < seq) {
            sub.read = seq;
            if (mListener != null) {
                mListener.onContactUpdate("read", sub);
            }
        }
    }

    /**
     * Set recv value, update listener
     *
     * @param topicName name of the contact to update
     * @param seq the 'recv' value
     */
    protected void setRecv(String topicName, int seq) {
        Subscription<Pu,Pr> sub = mSubs.get(topicName);
        if (sub != null && sub.recv < seq) {
            sub.recv = seq;
            if (mListener != null) {
                mListener.onContactUpdate("recv", sub);
            }
        }
    }

    /**
     * Set message count for a contact, update listener
     *
     * @param topicName name of the contact to update
     * @param seq the 'msg' (SeqID -- sequential message ID) value
     */
    protected void setMsgSeq(String topicName, int seq) {
        Subscription<Pu,Pr> sub = mSubs.get(topicName);
        if (sub != null) {
            if (sub.seq < seq) {
                sub.seq = seq;
            }
            if (sub.recv < seq) {
                sub.recv = seq;
                if (mListener != null) {
                    mListener.onContactUpdate("msg", sub);
                }
            }
        }
    }

    /**
     *
     * @param topicName name of the contact to check
     * @return The SeqID of the contact
     */
    protected int getMsgSeq(String topicName) {
        Subscription<Pu,Pr> sub = mSubs.get(topicName);
        if (sub != null) {
            return sub.seq;
        }
        return 0;
    }
}
