package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.Credential;
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

    public PrivateType getPriv() {
        return null;
    }
    public void setPriv(PrivateType priv) { /* do nothing */ }


    public Credential[] getCreds() {
        return mCreds != null ? mCreds.toArray(new Credential[]{}) : null;
    }

    @Override
    public Date getSubsUpdated() {
        return mTinode.getTopicsUpdated();
    }

    /**
     * Delete credential.
     *
     * @param meth  credential method (i.e. "tel" or "email").
     * @param val   value of the credential being deleted, i.e. "alice@example.com".
     */
    public PromisedReply<ServerMessage> delCredential(String meth, String val) {
        if (mAttached) {
            final Credential cred = new Credential(meth, val);
            return mTinode.delCredential(cred).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    if (mCreds == null) {
                        return null;
                    }

                    int idx = findCredential(cred, false);
                    if (idx >= 0) {
                        mCreds.remove(idx);
                    }

                    // Notify listeners
                    if (mListener != null && mListener instanceof MeListener) {
                        ((MeListener) mListener).onCredUpdated(mCreds.toArray(new Credential[]{}));
                    }
                    return null;
                }
            });
        }

        if (mTinode.isConnected()) {
            return new PromisedReply<>(new NotSubscribedException());
        }

        return new PromisedReply<>(new NotConnectedException());
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
    private void processOneSub(Subscription<DP, PrivateType> sub) {
        // Handle topic.
        Topic topic = mTinode.getTopic(sub.topic);
        if (topic != null) {
            // This is an existing topic.
            if (sub.deleted != null) {
                // Expunge deleted topic
                mTinode.stopTrackingTopic(sub.topic);
                topic.persist(false);
                topic = null;
            } else {
                // Update its record in memory and in the database.
                topic.update(sub);
                // Notify topic to update self.
                if (topic.mListener != null) {
                    topic.mListener.onContUpdated(sub.topic);
                }
            }
        } else if (sub.deleted == null) {
            // This is a new topic. Register it and write to DB.
            topic = mTinode.newTopic(sub);
            topic.persist(true);
        } else {
            Log.i(TAG, "Request to delete an unknown topic: " + sub.topic);
        }

        // Convert p2p topics to users.
        if (topic !=  null && topic.getTopicType() == TopicType.P2P && mStore != null) {
            // Use P2P description to generate and update user
            User user = mTinode.getUser(topic.getName());
            if (user == null) {
                user = mTinode.addUser(topic.getName());
            }
            if (user.merge(topic.mDesc)) {
                mStore.userUpdate(user);
            }
        }

        if (mListener != null) {
            mListener.onMetaSub(sub);
        }
    }

    private int findCredential(Credential other, boolean anyUnconfirmed) {
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

        if (cred.val != null) {
            if (mCreds == null) {
                // Empty list. Create and add.
                mCreds = new ArrayList<>();
                mCreds.add(cred);
            } else {
                // Try finding this credential among confirmed or not.
                int idx = findCredential(cred, false);
                if (idx < 0) {
                    // Not found.
                    if (!cred.isDone()) {
                        // Unconfirmed credential replaces previous unconfirmed credential of the same method.
                        idx = findCredential(cred, true);
                        if (idx >= 0) {
                            // Remove previous unconfirmed credential.
                            mCreds.remove(idx);
                        }
                    }
                    mCreds.add(cred);
                } else {
                    // Found. Maybe change 'done' status.
                    Credential el = this.mCreds.get(idx);
                    el.done = cred.isDone();
                }
            }
        } else if (cred.resp != null) {
            // Handle credential confirmation.
            int idx = findCredential(cred, true);
            if (idx >= 0) {
                Credential el = this.mCreds.get(idx);
                el.done = true;
            }
        }
    }

    @Override
    protected void routeMetaCred(Credential cred) {
        processOneCred(cred);

        if (mListener != null && mListener instanceof MeListener) {
            ((MeListener) mListener).onCredUpdated(mCreds.toArray(new Credential[]{}));
        }
    }


    @Override
    protected void routeMetaCred(Credential[] creds) {
        mCreds = new ArrayList<>();
        for (Credential cred : creds) {
            if (cred.meth != null && cred.val != null) {
                mCreds.add(cred);
            }
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
                        if (topic.updateAccessMode(pres.dacs) && mStore != null) {
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
                        mTinode.stopTrackingTopic(pres.src);
                        topic.persist(false);
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
        Topic topic = mTinode.getTopic(topicName);
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

        if (mListener != null) {
            mListener.onContUpdated(topicName);
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
        public void onContUpdated(String contact) {
        }
        /** Called by MeTopic when credentials are updated */
        public void onCredUpdated(Credential[] cred) {}
    }

}
