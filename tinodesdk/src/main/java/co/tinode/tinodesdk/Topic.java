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
import java.util.HashMap;
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
    public PromisedReply subscribe() throws IOException {
        if (!mSubscribed) {
            MsgGetMeta getParams = null;
            if (mDescription == null || mDescription.updated == null) {
                getParams = new MsgGetMeta();
                getParams.what = "desc sub data";
            }
            return mTinode.subscribe(getName(), null, getParams);
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
    public PromisedReply Publish(T content) throws IOException {
        return mTinode.publish(getName(), content);
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

    public Subscription<Pu,Pr> getSubscription(String key) {
        return mSubs.get(key);
    }

    protected void setListener(Listener<Pu,Pr,T> l) {
        mListener = l;
    }

    public boolean isSubscribed() {
        return mSubscribed;
    }

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
        // TODO(gene): cache/save message
        // TODO(gene): cache/save sender

        if (data.seq > mDescription.seq) {
            mDescription.seq = data.seq;
        }
        mListener.onData(data);
    }

    protected void routePres(MsgServerPres pres) {
        mListener.onPres(pres);
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

    public interface Listener<Pu,Pr,T> {
        void onSubscribe(int code, String text);
        void onLeave(int code, String text);
        void onData(MsgServerData<T> data);
        void onPres(MsgServerPres pres);
        void onInfo(MsgServerInfo info);
        void onMeta(MsgServerMeta<Pu,Pr> meta);
        void onMetaSub(Subscription<Pu,Pr> sub);
        void onMetaDesc(Description<Pu,Pr> desc);
        void onSubsUpdated();
    }
}
