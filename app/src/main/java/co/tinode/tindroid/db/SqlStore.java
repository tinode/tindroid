package co.tinode.tindroid.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Collection;
import java.util.Date;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Persistence for Tinode.
 */
class SqlStore implements Storage {
    private BaseDb mDbh;
    private long mMyId = -1;

    SqlStore(BaseDb dbh) {
        mDbh = dbh;
    }

    @Override
    public String getMyUid() {
        return mDbh.getUid();
    }

    @Override
    public void setMyUid(String uid) {
        mDbh.setUid(uid);
    }

    public boolean isReady() {
        return mDbh.isReady();
    }

    public void logout() {
        AccountDb.deactivateAll(mDbh.getWritableDatabase());
    }

    @Override
    public Topic[] topicGetAll() {
        Cursor c = TopicDb.query(mDbh.getReadableDatabase());
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
    public long topicAdd(Topic topic) {
        return TopicDb.insert(mDbh.getWritableDatabase(), topic);
    }

    @Override
    public boolean topicUpdate(Topic topic) {
        return TopicDb.update(mDbh.getWritableDatabase(), topic);
    }

    @Override
    public boolean topicDelete(Topic topic) {
        return TopicDb.delete(mDbh.getWritableDatabase(), topic) > 0;
    }

    @Override
    public boolean setRead(Topic topic, int read) {
        boolean result = false;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.mId > 0) {
            result = TopicDb.updateRead(mDbh.getWritableDatabase(), st.mId, read);
        }
        return result;
    }

    @Override
    public boolean setRecv(Topic topic, int recv) {
        boolean result = false;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.mId > 0) {
            result = TopicDb.updateRecv(mDbh.getWritableDatabase(), st.mId, recv);
        }
        return result;
    }

    @Override
    public <Pu, Pr> long subAdd(Topic topic, Subscription<Pu, Pr> sub) {
        return SubscriberDb.insert(mDbh.getWritableDatabase(), StoredTopic.getId(topic), sub);
    }

    @Override
    public <Pu, Pr> boolean subUpdate(Topic topic, Subscription<Pu, Pr> sub) {
        return SubscriberDb.update(mDbh.getWritableDatabase(), sub);
    }


    @Override
    public Collection<Subscription> getSubscriptions(Topic topic) {
        Cursor c = SubscriberDb.query(mDbh.getReadableDatabase(), StoredTopic.getId(topic));
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

        return MessageDb.insert(mDbh.getWritableDatabase(), msg);
    }

    @Override
    public <T> long msgSend(Topic topic, T data) {
        StoredMessage<T> msg = new StoredMessage<>();
        SQLiteDatabase db = mDbh.getWritableDatabase();

        msg.topic = topic.getName();
        msg.from = getMyUid();
        msg.ts = new Date();
        // Set seq to 0, update it later.
        msg.seq = 0;
        msg.content = data;

        msg.topicId = StoredTopic.getId(topic);
        if (mMyId < 0) {
            mMyId = UserDb.getId(db, msg.from);
        }
        msg.userId = mMyId;
        msg.senderIdx = 0;

        return MessageDb.insert(db, msg);
    }

    @Override
    public boolean msgDelivered(long id, Date timestamp, int seq) {
        return MessageDb.delivered(mDbh.getWritableDatabase(), id, timestamp, seq);
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, int before) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDbh.getWritableDatabase(), st.mId, before, true);
    }

    @Override
    public boolean msgDelete(Topic topic, int before) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDbh.getWritableDatabase(), st.mId, before, false);
    }

    @Override
    public boolean msgRecvByRemote(Subscription sub, int recv) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.mId > 0) {
            result = SubscriberDb.updateRecv(mDbh.getWritableDatabase(), ss.mId, recv);
        }
        return result;
    }

    @Override
    public boolean msgReadByRemote(Subscription sub, int read) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.mId > 0) {
            result = SubscriberDb.updateRead(mDbh.getWritableDatabase(), ss.mId, read);
        }
        return result;
    }
}
