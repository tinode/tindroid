package co.tinode.tinodesdk;

import com.fasterxml.jackson.databind.JavaType;

import java.util.Collection;
import java.util.HashMap;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

// Topic's Public and Private are String. Subscription Public is VCard, Private is String[].
public class FndTopic<SP> extends Topic<String,String,SP,String[]> {
    private static final String TAG = "FndTopic";

    public FndTopic(Tinode tinode, Listener<String,String,SP,String[]> l) {
        super(tinode, Tinode.TOPIC_FND, l);
    }

    public void setTypes(JavaType typeOfSubPu) {
        mTinode.setFndTypeOfMetaPacket(typeOfSubPu);
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
     * Add subscription to cache. Needs to be overridden in FndTopic because it keeps subs indexed
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
    public Subscription<SP,String[]> getSubscription(String key) {
        return mSubs != null ? mSubs.get(key) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<Subscription<SP,String[]>> getSubscriptions() {
        return mSubs != null ? mSubs.values() : null;
    }

    public static class FndListener<SP> extends Listener<String,String,SP,String[]> {
        /** {meta} message received */
        public void onMeta(MsgServerMeta<String,String,SP,String[]> meta) {}
        /** {meta what="sub"} message received, and this is one of the subs */
        public void onMetaSub(Subscription<SP,String[]> sub) {}
        /** {meta what="desc"} message received */
        public void onMetaDesc(Description<String,String> desc) {}
    }
}
