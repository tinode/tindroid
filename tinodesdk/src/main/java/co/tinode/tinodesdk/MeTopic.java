package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.AcsHelper;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MetaSetSub;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * MeTopic manages contact list. MeTopic::Private is unused.
 */
public class MeTopic<DP> extends Topic<DP,PrivateType,DP,PrivateType> {
    private static final String TAG = "MeTopic";

    @SuppressWarnings("WeakerAccess")
    protected ArrayList<Credential> mCreds;

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
    public PromisedReply<ServerMessage> publish(Drafty content) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PromisedReply<ServerMessage> publish(String content) {
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

    @Override
    public Date getSubsUpdated() {
        return mTinode.getTopicsUpdated();
    }

    /**
     * Get current user's credentials, such as emails and phone numbers.
     */
    public Credential[] getCreds() {
        return mCreds != null ? mCreds.toArray(new Credential[]{}) : null;
    }

    public void setCreds(Credential[] creds) {
        if (creds == null) {
            mCreds = null;
        } else {
            mCreds = new ArrayList<>();
            for (Credential cred : creds) {
                if (cred.meth != null && cred.val != null) {
                    mCreds.add(cred);
                }
            }
            Collections.sort(mCreds);
        }
    }

    /**
     * Delete credential.
     *
     * @param meth  credential method (i.e. "tel" or "email").
     * @param val   value of the credential being deleted, i.e. "alice@example.com".
     */
    public PromisedReply<ServerMessage> delCredential(String meth, String val) {
        if (mAttached <= 0) {
            if (mTinode.isConnected()) {
                return new PromisedReply<>(new NotSubscribedException());
            }
            return new PromisedReply<>(new NotConnectedException());
        }

        final Credential cred = new Credential(meth, val);
        return mTinode.delCredential(cred).thenApply(new PromisedReply.SuccessListener<>() {
            @Override
            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                if (mCreds == null) {
                    return null;
                }

                int idx = findCredIndex(cred, false);
                if (idx >= 0) {
                    mCreds.remove(idx);

                    if (mStore != null) {
                        mStore.topicUpdate(MeTopic.this);
                    }

                    // Notify listeners
                    if (mListener != null && mListener instanceof MeListener) {
                        ((MeListener) mListener).onCredUpdated(mCreds.toArray(new Credential[]{}));
                    }
                }
                return null;
            }
        });
    }

    public PromisedReply<ServerMessage> confirmCred(final String meth, final String resp) {
        return setMeta(new MsgSetMeta.Builder<DP,PrivateType>()
                .with(new Credential(meth, null, resp, null)).build());
    }

    @Override
    public PromisedReply<ServerMessage> updateMode(final String update) {
        if (mDesc.acs == null) {
            mDesc.acs = new Acs();
        }

        final AcsHelper mode = mDesc.acs.getWantHelper();
        if (mode.update(update)) {
            return setSubscription(new MetaSetSub(null, mode.toString()));
        }
        // The state is unchanged, return resolved promise.
        return new PromisedReply<>((ServerMessage) null);
    }

    /**
     * Topic sent an update to subscription, got a confirmation.
     *
     * @param params {ctrl} parameters returned by the server (could be null).
     * @param sSub   updated topic parameters.
     */
    @Override
    protected void update(Map<String, Object> params, MetaSetSub sSub) {
        //noinspection unchecked
        Map<String, String> acsMap = params != null ? (Map<String, String>) params.get("acs") : null;
        Acs acs;
        if (acsMap != null) {
            acs = new Acs(acsMap);
        } else {
            acs = new Acs();
            acs.setWant(sSub.mode);
        }

        boolean changed;
        if (mDesc.acs == null) {
            mDesc.acs = acs;
            changed = true;
        } else {
            changed = mDesc.acs.merge(acs);
        }

        if (changed && mStore != null) {
            mStore.topicUpdate(this);
        }
    }

    /**
     * Topic sent an update to description or subscription, got a confirmation, now
     * update local data with the new info.
     *
     * @param ctrl {ctrl} packet sent by the server
     * @param meta original {meta} packet updated topic parameters
     */
    @Override
    protected void update(MsgServerCtrl ctrl, MsgSetMeta<DP,PrivateType> meta) {
        if (meta.desc != null) {
            updatePinnedTopics(meta.desc.priv);
        }

        super.update(ctrl, meta);

        if (meta.cred != null) {
            routeMetaCred(meta.cred);
        }
    }

    @Override
    protected void routeMeta(MsgServerMeta<DP,PrivateType,DP,PrivateType> meta) {
        if (meta.cred != null) {
            routeMetaCred(meta.cred);
        }

        if (meta.desc != null) {
            // Create or update 'me' user in storage.
            User userMe = mTinode.getUser(mTinode.getMyId());
            boolean changed;
            if (userMe == null) {
                userMe = mTinode.addUser(mTinode.getMyId(), meta.desc);
                changed = true;
            } else {
                //noinspection unchecked
                changed = userMe.merge(meta.desc);
            }
            if (changed && mStore != null) {
                mStore.userUpdate(userMe);
            }
            updatePinnedTopics(meta.desc.priv);
        }

        super.routeMeta(meta);
    }

    @Override
    public int getPinnedRank() {
        return 0;
    }

    @Override
    public void setPinnedRank(int pinned) {
        /* do nothing */
    }

    /**
     * Pin topic to the top of the contact list.
     *
     * @param topicName - Name of the topic to pin.
     * @param pin - If true, pin the topic, otherwise unpin.
     *
     * @return Promise to be resolved/rejected when the server responds to request.
     */
    @Override
    public PromisedReply<ServerMessage> pinTopic(final @NotNull String topicName, boolean pin) {
        if (mAttached <= 0) {
            if (mTinode.isConnected()) {
                return new PromisedReply<>(new NotSubscribedException());
            }
            return new PromisedReply<>(new NotConnectedException());
        }

        if (!isUserType(topicName)) {
            return new PromisedReply<>(new IllegalArgumentException("Invalid topic type to pin"));
        }

        List<String> pinned = getPriv() != null ? getPriv().getPinnedTopics() : null;
        ArrayList<String> tpin = pinned != null ?
                // Creating a copy to leave original list unchanged.
                new ArrayList<>(pinned) :
                // New empty list.
                new ArrayList<>();

        boolean found = tpin.contains(topicName);
        if ((pin && found) || (!pin && !found)) {
            // Nothing to do, return resolved promise.
            return new PromisedReply<>(null);
        }

        if (pin) {
            // Add topic to the top of the pinned list.
            tpin.add(0, topicName);
        } else {
            // Remove topic from the pinned list.
            tpin.remove(topicName);
        }
        final PrivateType priv = new PrivateType();
        priv.setPinnedTopics(tpin);
        return setDescription(null, priv, null);
    }

    /**
     * Get the rank of the pinned topic.
     * @param topicName - Name of the topic to check.
     *
     * @return numeric rank of the pinned topic in the range 1..N (N being the top,
     *      N - the number of pinned topics) or 0 if not pinned.
     */
    @Override
    public int pinnedTopicRank(final @NotNull String topicName) {
        PrivateType priv = getPriv();
        if (priv == null) {
            return 0;
        }
        return priv.getPinnedRank(topicName);
    }

    /**
     * Get the rank of the pinned topic.
     * @param topicName - Name of the topic to check.
     *
     * @return numeric rank of the pinned topic in the range 1..N (N being the top,
     *      N - the number of pinned topics) or 0 if not pinned.
     */
    public boolean isPinned(final @NotNull String topicName) {
        PrivateType priv = getPriv();
        if (priv == null) {
            return false;
        }
        return priv.getPinnedRank(topicName) > 0;
    }

    private void updatePinnedTopics(final PrivateType priv) {
        List<String> newPins = priv != null ? priv.getPinnedTopics() : null;
        if (newPins == null) {
            return;
        }
        // Update pinned rank for all pinned topics.
        int rank = newPins.size();
        for (String topicName : newPins) {
            Topic topic = mTinode.getTopic(topicName);
            if (topic != null) {
                topic.setPinnedRank(rank);
                if (mStore != null) {
                    mStore.topicUpdate(topic);
                }
            }
            rank --;
        }
        List<String> thesePins = getPriv() != null ? getPriv().getPinnedTopics() : null;
        if (thesePins == null || thesePins.isEmpty()) {
            return;
        }
        // Unpin topics that were removed from the pinned list.
        for (String topicName : thesePins) {
            if (!newPins.contains(topicName)) {
                Topic topic = mTinode.getTopic(topicName);
                if (topic != null) {
                    topic.setPinnedRank(0);
                    if (mStore != null) {
                        mStore.topicUpdate(topic);
                    }
                }
            }
        }
    }

    @Override
    protected void routeMetaSub(MsgServerMeta<DP,PrivateType,DP,PrivateType> meta) {

        for (Subscription<DP,PrivateType> sub : meta.sub) {
            processOneSub(sub);
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }
    }

    @SuppressWarnings("unchecked")
    private void processOneSub(Subscription<DP,PrivateType> sub) {
        // Handle topic.
        Topic topic = mTinode.getTopic(sub.topic);
        if (topic != null) {
            // This is an existing topic.
            if (sub.deleted != null) {
                // Expunge deleted topic
                if (topic.isDeleted()) {
                    mTinode.stopTrackingTopic(sub.topic);
                    topic.expunge(true);
                } else {
                    topic.expunge(false);
                }
                topic = null;
            } else {
                // Update its record in memory and in the database.
                if (topic.update(sub) && topic.mListener != null) {
                    // Notify topic to update self.
                    topic.mListener.onMetaDesc(topic.mDesc);
                }
            }
        } else if (sub.deleted == null) {
            // This is a new topic. Register it and write to DB.
            topic = mTinode.newTopic(sub);
            topic.persist();
        } else {
            Log.w(TAG, "Request to delete an unknown topic: " + sub.topic);
        }

        if (mStore != null && topic != null) {
            int pinnedRank = pinnedTopicRank(sub.topic);
            if (topic.getPinnedRank() != pinnedRank) {
                topic.setPinnedRank(pinnedRank);
                mStore.topicUpdate(topic);
            }
            // Use p2p topic to update user's record.
            if (topic.getTopicType() == TopicType.P2P) {
                // Use P2P description to generate and update user
                User user = mTinode.getUser(topic.getName());
                boolean changed;
                if (user == null) {
                    user = mTinode.addUser(topic.getName(), topic.mDesc);
                    changed = true;
                } else {
                    changed = user.merge(topic.mDesc);
                }
                if (changed) {
                    mStore.userUpdate(user);
                }
            }
        }
        if (mListener != null) {
            mListener.onMetaSub(sub);
        }
    }

    private int findCredIndex(Credential other, boolean anyUnconfirmed) {
        int i = 0;
        for (Credential cred : mCreds) {
            if (cred.meth.equals(other.meth) && ((anyUnconfirmed && !cred.isDone()) || cred.val.equals(other.val))) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private void processOneCred(Credential cred) {
        if (cred.meth == null) {
            // Skip invalid method;
            return;
        }

        boolean changed = false;
        if (cred.val != null) {
            if (mCreds == null) {
                // Empty list. Create and add.
                mCreds = new ArrayList<>();
                mCreds.add(cred);
            } else {
                // Try finding this credential among confirmed or not.
                int idx = findCredIndex(cred, false);
                if (idx < 0) {
                    // Not found.
                    if (!cred.isDone()) {
                        // Unconfirmed credential replaces previous unconfirmed credential of the same method.
                        idx = findCredIndex(cred, true);
                        if (idx >= 0) {
                            // Remove previous unconfirmed credential.
                            mCreds.remove(idx);
                        }
                    }
                    mCreds.add(cred);
                } else {
                    // Found. Maybe change 'done' status.
                    Credential el = mCreds.get(idx);
                    el.done = cred.isDone();
                }
            }
            changed = true;
        } else if (cred.resp != null && mCreds != null) {
            // Handle credential confirmation.
            int idx = findCredIndex(cred, true);
            if (idx >= 0) {
                Credential el = mCreds.get(idx);
                el.done = true;
                changed = true;
            }
        }

        if (changed) {
            if (mCreds != null) {
                Collections.sort(mCreds);
            }

            if (mStore != null) {
                mStore.topicUpdate(this);
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void routeMetaCred(Credential cred) {
        processOneCred(cred);

        if (mListener != null && mListener instanceof MeListener) {
            ((MeListener) mListener).onCredUpdated(mCreds.toArray(new Credential[]{}));
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void routeMetaCred(Credential[] creds) {
        mCreds = new ArrayList<>();
        for (Credential cred : creds) {
            if (cred.meth != null && cred.val != null) {
                mCreds.add(cred);
            }
        }
        Collections.sort(mCreds);

        if (mStore != null) {
            mStore.topicUpdate(this);
        }

        if (mListener != null && mListener instanceof MeListener) {
            ((MeListener) mListener).onCredUpdated(creds);
        }
    }

    @Override
    protected void routePres(MsgServerPres pres) {
        MsgServerPres.What what = MsgServerPres.parseWhat(pres.what);
        if (what == MsgServerPres.What.TERM) {
            super.routePres(pres);
            return;
        }

        if (what == MsgServerPres.What.UPD) {
            if (Tinode.TOPIC_ME.equals(pres.src)) {
                // Update to me topic itself.
                getMeta(getMetaGetBuilder().withDesc().build());
            } else {
                // pub/priv updated: fetch subscription update.
                getMeta(getMetaGetBuilder().withSub(pres.src).build());
            }
        } else {
            Topic topic = mTinode.getTopic(pres.src);
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
                        topic.setSeqAndFetch(pres.seq);
                        if (pres.act == null || mTinode.isMe(pres.act)) {
                            // Message is sent by the current user.
                            assignRead(topic, pres.seq);
                        }
                        topic.setTouched(new Date());
                        break;

                    case ACS: // access mode changed
                        if (pres.tgt == null && topic.updateAccessMode(pres.dacs) && mStore != null) {
                            // tgt is null means permissions are for the current user.
                            mStore.topicUpdate(topic);
                        }
                        break;

                    case UA: // user agent changed
                        topic.setLastSeen(new Date(), pres.ua);
                        break;

                    case RECV: // user's other session marked some messages as received
                        assignRecv(topic, pres.seq);
                        break;

                    case READ: // user's other session marked some messages as read
                        assignRead(topic, pres.seq);
                        break;

                    case DEL: // messages deleted
                        // TODO(gene): add handling for del
                        break;

                    case GONE:
                        // If topic is unknown (==null), then we don't care to unregister it.
                        if (topic.isDeleted()) {
                            mTinode.stopTrackingTopic(pres.src);
                            topic.expunge( true);
                        } else {
                            topic.expunge(false);
                        }
                        break;
                }
            } else {
                switch (what) {
                    case ACS:
                        Acs acs = new Acs();
                        acs.update(pres.dacs);
                        if (acs.isModeDefined()) {
                            getMeta(getMetaGetBuilder().withSub(pres.src).build());
                        } else {
                            Log.d(TAG, "Unexpected access mode in presence: '" + pres.dacs.want + "'/'" + pres.dacs.given + "'");
                        }
                        break;
                    case TAGS:
                        // Tags in 'me' topic updated.
                        getMeta(getMetaGetBuilder().withTags().build());
                        break;
                    default:
                        Log.d(TAG, "Topic not found in me.routePres: " + pres.what + " in " + pres.src);
                        break;
                }
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
    protected void routeInfo(MsgServerInfo info) {
        if (info.src == null) {
            return;
        }

        switch (info.what) {
            case Tinode.NOTE_KP:
            case Tinode.NOTE_REC_AUDIO:
            case Tinode.NOTE_REC_VIDEO:
            case Tinode.NOTE_CALL:
                break;

            case Tinode.NOTE_RECV:
            case Tinode.NOTE_READ:
                Topic topic = mTinode.getTopic(info.src);
                if (topic != null) {
                    topic.setReadRecvByRemote(info.from, info.what, info.seq);
                }

                // If this is an update from the current user, update the contact with the new count too.
                if (mTinode.isMe(info.from)) {
                    setMsgReadRecv(info.src, info.what, info.seq);
                }
                break;

            default:
                // Unknown notification ignored.
        }

        if (mListener != null) {
            mListener.onInfo(info);
        }
    }

    private void assignRead(Topic topic, int seq) {
        if (topic.getRead() < seq) {
            topic.setRead(seq);
            if (mStore != null) {
                mStore.setRead(topic, seq);
            }
            assignRecv(topic, topic.getRead());
        }
    }

    private void assignRecv(Topic topic, int seq) {
        if (topic.getRecv() < seq) {
            topic.setRecv(seq);
            if (mStore != null) {
                mStore.setRecv(topic, seq);
            }
        }
    }

    void setMsgReadRecv(String topicName, String what, int seq) {
        if (seq > 0) {
            final Topic topic = mTinode.getTopic(topicName);
            if (topic == null) {
                return;
            }

            switch (what) {
                case Tinode.NOTE_RECV:
                    assignRecv(topic, seq);
                    break;
                case Tinode.NOTE_READ:
                    assignRead(topic, seq);
                    break;
                default:
            }
        }

        if (mListener != null) {
            mListener.onContUpdated(topicName);
        }
    }

    @Override
    protected void topicLeft(boolean unsub, int code, String reason) {
        super.topicLeft(unsub, code, reason);

        Collection<Topic> topics = mTinode.getTopics();
        if (topics != null) {
            for (Topic t : topics) {
                t.setOnline(false);
            }
        }
    }

    public static class MeListener<DP> implements Listener<DP,PrivateType,DP,PrivateType> {
        /** {meta} message received */
        public void onMeta(MsgServerMeta<DP,PrivateType,DP,PrivateType> meta) {}
        /** Called by MeTopic when credentials are updated */
        public void onCredUpdated(Credential[] cred) {}
    }

    @Override
    public MetaGetBuilder getMetaGetBuilder() {
        return new MetaGetBuilder(this);
    }

    public static class MetaGetBuilder extends Topic.MetaGetBuilder {
        MetaGetBuilder(MeTopic parent) {
            super(parent);
        }

        public MetaGetBuilder withCred() {
            meta.setCred();
            return this;
        }
    }
}
