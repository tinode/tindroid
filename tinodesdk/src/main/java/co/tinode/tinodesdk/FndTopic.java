package co.tinode.tinodesdk;

import java.util.Collection;
import java.util.HashMap;

import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

// Public is not used for Fnd, Private is a String.
public class FndTopic extends Topic<String,String> {
    private static final String TAG = "FndTopic";

    public FndTopic(Tinode tinode, Listener<String,String> l) {
        super(tinode, Tinode.TOPIC_FND, l);
    }

    @Override
    public PromisedReply<ServerMessage> publish(Drafty content) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PromisedReply<ServerMessage> publish(String content) {
        throw new UnsupportedOperationException();
    }

    /**
     * Add subscription to cache. Needs to be overriden in FndTopic because it keeps subs indexed
     * by either user or topic value.
     *
     * @param sub subscription to add to cache
     */
    @Override
    protected void addSubToCache(Subscription sub) {
        if (mSubs == null) {
            mSubs = new HashMap<>();
        }
        mSubs.put(sub.user != null ? sub.user : sub.topic, sub);
    }

    @Override
    @SuppressWarnings("un-checked")
    protected void routeMetaSub(MsgServerMeta meta) {
        for (Subscription upd : meta.sub) {
            Subscription sub = getSubscription(upd.user != null ? upd.user : upd.topic);
            if (sub != null) {
                sub.merge(upd);
            } else {
                sub = upd;
                addSubToCache(sub);
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
    @SuppressWarnings("unchecked")
    public Subscription getSubscription(String key) {
        return mSubs != null ? mSubs.get(key) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection getSubscriptions() {
        return mSubs != null ? mSubs.values() : null;
    }
}
