package co.tinode.tindroid.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.security.InvalidParameterException;
import java.util.Collection;
import java.util.Date;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Persistence for Tinode.
 */
public class SqlStore implements Storage {
    private String mMyUid = null;
    private SQLiteDatabase mDb;
    private long mMyId = -1;

    SqlStore(SQLiteDatabase db) {
        mDb = db;
    }

    public void setDb(SQLiteDatabase db) {
        mDb = db;
    }

    @Override
    public String getMyUid() {
        return mMyUid;
    }

    @Override
    public boolean setMyUid(String uid) {
        if (mMyUid == null) {
            mMyUid = uid;
            BaseDb.setAccount(uid);
        } else if (!mMyUid.equals(uid)) {
            throw new IllegalStateException("Illegal attempt to change UID");
        }
        return true;
    }

    public boolean isReady() {
        return mDb != null && mMyUid != null;
    }

    @Override
    public Topic[] topicGetAll() {
        Cursor c = TopicDb.query(mDb);
        if (c != null && c.moveToFirst()) {
            Topic[] list = new Topic[c.getCount()];
            int i = 0;
            do {
                Topic t = TopicDb.readOne(c);
                list[i++] = t;
            } while (c.moveToNext());
            return list;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public long topicAdd(Topic topic) {
        return TopicDb.insert(mDb, topic);
    }

    @Override
    public boolean topicUpdate(Topic topic) {
        return TopicDb.update(mDb, topic);
    }

    @Override
    public boolean topicDelete(Topic topic) {
        return TopicDb.delete(mDb, topic) > 0;
    }

    @Override
    public boolean setRead(Topic topic, int read) {
        boolean result = false;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.mId > 0) {
            result = TopicDb.updateRead(mDb, st.mId, read);
        }
        return result;
    }

    @Override
    public boolean setRecv(Topic topic, int recv) {
        boolean result = false;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.mId > 0) {
            result = TopicDb.updateRecv(mDb, st.mId, recv);
        }
        return result;
    }

    @Override
    public <Pu, Pr> long subAdd(Topic topic, Subscription<Pu, Pr> sub) {
        return SubscriberDb.insert(mDb, StoredTopic.getId(topic), sub);
    }

    @Override
    public <Pu, Pr> boolean subUpdate(Topic topic, Subscription<Pu, Pr> sub) {
        return SubscriberDb.update(mDb, sub);
    }


    @Override
    public Collection<Subscription> getSubscriptions(Topic topic) {
        Cursor c = SubscriberDb.query(mDb, StoredTopic.getId(topic));
        if (c == null) {
            return null;
        }
        Collection<Subscription> result = SubscriberDb.readAll(c);
        c.close();
        return result;
    }

    @Override
    public <T> long msgReceived(Subscription sub, MsgServerData<T> m) {
        StoredMessage<T> msg = new StoredMessage<>(m);
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss == null) {
           return -1;
        }

        msg.topicId = ss.topicId;
        msg.userId = ss.userId;
        msg.senderIdx = ss.senderIdx;

        return MessageDb.insert(mDb, msg);
    }

    @Override
    public <T> long msgSend(Topic topic, T data) {
        StoredMessage<T> msg = new StoredMessage<>();

        msg.topic = topic.getName();
        msg.from = mMyUid;
        msg.ts = new Date();
        // Set seq to 0, update it later.
        msg.seq = 0;
        msg.content = data;

        msg.topicId = StoredTopic.getId(topic);
        if (mMyId < 0) {
            mMyId = UserDb.getId(mDb, mMyUid);
        }
        msg.userId = mMyId;
        msg.senderIdx = 0;

        return MessageDb.insert(mDb, msg);
    }

    @Override
    public boolean msgDelivered(long id, Date timestamp, int seq) {
        return MessageDb.delivered(mDb, id, timestamp, seq);
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, int before) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDb, st.mId, before, true);
    }

    @Override
    public boolean msgDelete(Topic topic, int before) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDb, st.mId, before, false);
    }

    @Override
    public boolean msgRecvByRemote(Subscription sub, int recv) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.mId > 0) {
            result = SubscriberDb.updateRecv(mDb, ss.mId, recv);
        }
        return result;
    }

    @Override
    public boolean msgReadByRemote(Subscription sub, int read) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.mId > 0) {
            result = SubscriberDb.updateRead(mDb, ss.mId, read);
        }
        return result;
    }
}
