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
        return false;
    }

    @Override
    public boolean topicUpdate(String name, Date timestamp, Description desc) {
        return false;
    }

    @Override
    public <Pu, Pr> boolean topicUpdate(String name, Date timestamp, Subscription<Pu, Pr>[] subs) {
        return false;
    }

    @Override
    public <Pu, Pr> Collection<Subscription<Pu, Pr>> getSubscriptions(String topic) {
        return null;
    }

    @Override
    public long msgReceived(Subscription sub, MsgServerData msg) {
        return 0;
    }

    @Override
    public long msgSend(String topicName, Object data) {
        return 0;
    }

    @Override
    public boolean msgDelivered(long id, Date timestamp) {
        return false;
    }

    @Override
    public int msgMarkToDelete(String topicName, int before) {
        return 0;
    }

    @Override
    public int msgDelete(String topicName, int before) {
        return 0;
    }
}
