package co.tinode.tinodesdk;

import com.fasterxml.jackson.databind.JavaType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

// Topic's Public and Private are String. Subscription Public is VCard, Private is String[].
public class FndTopic<SP> extends Topic<String, String, SP, String[]> {
    @SuppressWarnings("unused")
    private static final String TAG = "FndTopic";

    @SuppressWarnings("WeakerAccess")
    public FndTopic(Tinode tinode, Listener<String,String,SP,String[]> l) {
        super(tinode, Tinode.TOPIC_FND, l);
    }

    @SuppressWarnings("unused")
    public void setTypes(JavaType typeOfSubPu) {
        mTinode.setFndTypeOfMetaPacket(typeOfSubPu);
    }

    @Override
    public PromisedReply<ServerMessage> setMeta(final MsgSetMeta<String, String> meta) {
        if (mSubs != null) {
            mSubs = null;
            mSubsUpdated = null;

            if (mListener != null) {
                mListener.onSubsUpdated();
            }
        }
        return super.setMeta(meta);
    }

    @Override
    protected PromisedReply<ServerMessage> publish(Drafty content, Map<String, Object> head, long id) {
        throw new UnsupportedOperationException();
    }

    /**
     * Add subscription to cache. Needs to be overridden in FndTopic because it keeps subs indexed
     * by either user or topic value.
     *
     * @param sub subscription to add to cache
     */
    @Override
    protected void addSubToCache(Subscription<SP,String[]> sub) {
        if (mSubs == null) {
            mSubs = new HashMap<>();
        }
        mSubs.put(sub.getUnique(), sub);
    }

    @Override
    protected void routeMetaSub(MsgServerMeta<String, String,SP,String[]> meta) {
        for (Subscription<SP,String[]> upd : meta.sub) {
            Subscription<SP,String[]> sub = getSubscription(upd.getUnique());
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
    public Subscription<SP,String[]> getSubscription(String key) {
        return mSubs != null ? mSubs.get(key) : null;
    }

    @Override
    public Collection<Subscription<SP,String[]>> getSubscriptions() {
        return mSubs != null ? mSubs.values() : null;
    }

    @Override
    protected void setStorage(Storage store) {
        /* Do nothing: all fnd data is transient. */
    }

    /**
     * Check if the given tag is unique by asking the server.
     * @param tag tag to check.
     * @return promise to be resolved with true if the tag is unique, false otherwise.
     */
    public PromisedReply<Boolean> checkTagUniqueness(final String tag, final String caller) {
        PromisedReply<Boolean> result = new PromisedReply<>();
        subscribe(null, null)
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage unused) {
                        return setDescription(tag, null, null);
                    }
                })
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage unused) {
                        return getMeta(getMetaGetBuilder().withTags().build());
                    }
                })
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage response) throws Exception {
                        if (response.meta == null || response.meta.tags == null) {
                            result.resolve(true);
                            return null;
                        }
                        String[] tags = response.meta.tags;
                        for (String t : tags) {
                            if (t != null && !t.equals(caller)) {
                                result.resolve(false);
                                return null;
                            }
                        }
                        result.resolve(true);
                        return null;
                    }
                })
                .thenCatch(new PromisedReply.FailureListener<>() {
                    @Override
                    public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) throws Exception {
                        result.reject(err);
                        return null;
                    }
                });
        return result;
    }

    public static class FndListener<SP> implements Listener<String, String, SP, String[]> {
        /** {meta} message received */
        public void onMeta(MsgServerMeta<String, String, SP, String[]> meta) {}
    }
}
