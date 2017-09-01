package co.tinode.tinodesdk;

import android.util.Log;

import com.fasterxml.jackson.databind.JavaType;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import co.tinode.tinodesdk.model.Announcement;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * MeTopic handles invites and manages contact list
 */
public class MeTopic<Pu, Pr, T> extends Topic<Pu, Pr> {
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

    /**
     * This method has to be overridden because Subscription generally does not exists for invited senders.
     */
    @Override
    protected void routeData(final MsgServerData data) {
        if (data.seq > mDesc.seq) {
            mDesc.seq = data.seq;
        }

        Topic receiver = null;
        if (data.content != null) {
            final Announcement content = (Announcement) data.content;
            // Fetch the topic & user descriptions, if missing.
            receiver = mTinode.getTopic(content.topic);
            if (receiver == null) {
                receiver = mTinode.newTopic(content.topic, null);
                mTinode.registerTopic(receiver);
                try {
                    receiver.getMeta(MsgGetMeta.desc()).thenApply(null,
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                                    if (err instanceof ServerResponseException) {
                                        // Delete the topic if server responded with "404 NOT FOUND".
                                        if (((ServerResponseException) err).getCode() ==
                                                ServerMessage.STATUS_NOT_FOUND) {
                                            mTinode.unregisterTopic(content.topic);
                                        }
                                    }
                                    return null;
                                }
                            });
                } catch (Exception ignored) {
                }
            }

            User user = mTinode.getUser(content.user);
            if (user == null) {
                mTinode.addUser(content.user);
                mTinode.getMeta(content.user, MsgGetMeta.desc());
            }
        }

        if (mStore != null) {
            if (mStore.annReceived(this, receiver, data) > 0) {
                noteRecv();
            }
        } else {
            noteRecv();
        }


        if (mListener != null) {
            mListener.onData(data);
        }
    }

    @Override
    protected void routeMetaSub(MsgServerMeta<Pu, Pr> meta) {
        Log.d(TAG, "Me:routeMetaSub");
        for (Subscription<Pu, Pr> sub : meta.sub) {
            Topic<Pu, Pr> topic = mTinode.getTopic(sub.topic);
            if (topic != null) {
                // This is an existing topic.
                if (sub.deleted != null) {
                    // Expunge deleted topic
                    mTinode.unregisterTopic(sub.topic);
                } else {
                    // Update its record in memory and in the database.
                    topic.update(sub);
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
                    // TODO(gene): issue a request for an updated description
                    // topic.getMeta(...);
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

                case DEL: // messages deleted in other session
                    // TODO(gene): add handling for del
                    break;

                case GONE:
                    // Handle it below, even if the topic is not found.
                    break;
            }
        } else {
            Log.d(TAG, "Topic not found in me.routePres: " + pres.src);
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
