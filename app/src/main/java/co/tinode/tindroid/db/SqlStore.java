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
            StoredTopic[] list = new StoredTopic[c.getCount()];
            int i = 0;
            do {
                StoredTopic t = TopicDb.readOne(c);
                list[i++] = t;
            } while (c.moveToNext());
            return list;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public long topicAdd(Topic topic) {
        return TopicDb.insert(sDb, new StoredTopic(topic));
    }

    @Override
    public boolean topicDelete(String name) {
        return TopicDb.delete(sDb, name) > 0;
    }

    @Override
    public boolean topicUpdate(String name, Date timestamp, MsgSetMeta meta) {
        return TopicDb.update(sDb, name, timestamp, meta);
    }

    @Override
    public boolean topicUpsert(String name, Date timestamp, Description desc) {
        return TopicDb.update(sDb, name, timestamp, desc);
    }

    @Override
    public <Pu, Pr> boolean topicUpsert(String name, Date timestamp, Subscription<Pu, Pr>[] subs) {
        return false;
    }

    @Override
    public <Pu, Pr> Collection<? extends Subscription<Pu, Pr>> getSubscriptions(String topic) {
        SubscriberDb.getSenders()
        return null;
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
    public int msgMarkToDelete(String topicName, int before) {
        long topicId = TopicDb.getId(sDb, topicName);
        return MessageDb.delete(sDb, topicId, 0, before, true);
    }

    @Override
    public int msgDelete(String topicName, int before) {
        long topicId = TopicDb.getId(sDb, topicName);
        return MessageDb.delete(sDb, topicId, 0, before, false);
    }

    @Override
    public int setRecv(Subscription sub, int recv) {

    }

    @Override
    public int setRead(StoredSubscription sub, int read) {
        int result = -1;
        sDb.beginTransaction();
        try {
            TopicDb.updateRead(sDb, , read);
            MessageDb.setStatus(sDb,...,StoredMessage.STATUS_READ);
            sDb.setTransactionSuccessful();
        } catch (Exception ignored) {
        }
        sDb.endTransaction();
    }
}
