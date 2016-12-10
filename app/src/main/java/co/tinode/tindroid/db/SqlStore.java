package co.tinode.tindroid.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
    private static String sMyUid = null;
    private static SQLiteDatabase sDb;

    SqlStore(SQLiteDatabase db, String uid) {
        sDb = db;
        sMyUid = uid;
    }

    @Override
    public String getMyUid() {
        return sMyUid;
    }

    @Override
    public boolean setMyUid(String uid) {
        sMyUid = uid;
        return true;
    }

    @Override
    public Topic[] topicGetAll() {
        Cursor c = TopicDb.query(sDb);
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
        return TopicDb.insert(sDb, topic);
    }

    @Override
    public boolean topicUpdate(Topic topic) {
        return TopicDb.update(sDb, topic);
    }

    @Override
    public boolean topicDelete(Topic topic) {
        return TopicDb.delete(sDb, topic) > 0;
    }

    @Override
    public boolean setRead(Topic topic, int read) {
        boolean result = false;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.mId > 0) {
            result = TopicDb.updateRead(sDb, st.mId, read);
        }
        return result;
    }

    @Override
    public boolean setRecv(Topic topic, int recv) {
        boolean result = false;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.mId > 0) {
            result = TopicDb.updateRecv(sDb, st.mId, recv);
        }
        return result;
    }

    @Override
    public <Pu, Pr> long subAdd(Topic topic, Subscription<Pu, Pr> sub) {
        return SubscriberDb.insert(sDb, StoredTopic.getId(topic), sub);
    }

    @Override
    public <Pu, Pr> boolean subUpdate(Topic topic, Subscription<Pu, Pr> sub) {
        return SubscriberDb.update(sDb, sub);
    }


    @Override
    public Collection<Subscription> getSubscriptions(Topic topic) {
        Cursor c = SubscriberDb.query(sDb, StoredTopic.getId(topic));
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
        // FIXME: try reading them from cache
        msg.topicId = -1;
        msg.userId = -1;
        msg.senderIdx = -1;

        msg.deliveryStatus = StoredMessage.STATUS_NONE;
        return MessageDb.insert(sDb, msg);
    }

    @Override
    public <T> long msgSend(String topicName, T data) {
        StoredMessage<T> msg = new StoredMessage<>();

        msg.topic = topicName;
        msg.from = sMyUid;
        msg.ts = new Date();
        // Set seq to 0, update it later.
        msg.seq = 0;
        msg.content = data;

        // FIXME(gene): try reading them from cache
        msg.topicId = -1;
        msg.userId = -1;

        msg.senderIdx = 0;
        msg.deliveryStatus = StoredMessage.STATUS_NONE;
        return MessageDb.insert(sDb, msg);
    }

    @Override
    public boolean msgDelivered(long id, Date timestamp, int seq) {
        return MessageDb.setStatus(sDb, id, timestamp, seq, StoredMessage.STATUS_SENT);
    }

    @Override
    public int msgMarkToDelete(Topic topic, int before) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(sDb, st.mId, 0, before, true);
    }

    @Override
    public int msgDelete(Topic topic, int before) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(sDb, st.mId, 0, before, false);
    }

    @Override
    public boolean msgRecvByRemote(Subscription sub, int recv) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.mId > 0) {
            result = SubscriberDb.updateRecv(sDb, ss.mId, recv);
        }
        return result;
    }

    @Override
    public boolean msgReadByRemote(Subscription sub, int read) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.mId > 0) {
            result = SubscriberDb.updateRead(sDb, ss.mId, read);
        }
        return result;
    }
}
