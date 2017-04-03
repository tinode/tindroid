package co.tinode.tindroid.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Collection;
import java.util.Date;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Invitation;
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
    public Topic[] topicGetAll(Tinode tinode) {
        Cursor c = TopicDb.query(mDbh.getReadableDatabase());
        if (c != null && c.moveToFirst()) {
            Topic[] list = new Topic[c.getCount()];
            int i = 0;
            do {
                list[i++] = TopicDb.readOne(tinode, c);
            } while (c.moveToNext());
            return list;
        }
        return null;
    }

    @Override
    public long topicAdd(Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return TopicDb.insert(mDbh.getWritableDatabase(), topic);
        } else {
            return st.id;
        }
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
    public Range getCachedMessagesRange(Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null) {
            return new Range(st.maxLocalSeq, st.maxLocalSeq);
        }
        return null;
    }

    @Override
    public boolean setRead(Topic topic, int read) {
        boolean result = false;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.id > 0) {
            result = TopicDb.updateRead(mDbh.getWritableDatabase(), st.id, read);
        }
        return result;
    }

    @Override
    public boolean setRecv(Topic topic, int recv) {
        boolean result = false;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.id > 0) {
            result = TopicDb.updateRecv(mDbh.getWritableDatabase(), st.id, recv);
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
    public <T> long msgReceived(Topic topic, Subscription sub, MsgServerData<T> m) {
        StoredMessage<T> msg = new StoredMessage<>(m);
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss == null) {
           return -1;
        }

        msg.topicId = ss.topicId;
        msg.userId = ss.userId;
        msg.senderIdx = ss.senderIdx;

        return MessageDb.insert(mDbh.getWritableDatabase(), topic, msg);
    }

    @Override
    public <Pu,T> long inviteReceived(Topic topic, MsgServerData<Invitation<Pu,T>> m) {
        StoredMessage<Invitation<Pu,T>> msg = new StoredMessage<>(m);
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return -1;
        }

        SQLiteDatabase db = mDbh.getWritableDatabase();

        db.beginTransaction();
        msg.userId = UserDb.getId(db, m.from);
        if (msg.userId < 0) {
            msg.userId = UserDb.insert(db, m.from, m.content.pub);
        }
        if (msg.userId > 0) {
            db.setTransactionSuccessful();
        }
        db.endTransaction();

        if (msg.userId <= 0) {
            return -1;
        }

        msg.topicId = st.id;

        // Use the same sender index for all invites.
        msg.senderIdx = 1;

        return MessageDb.insert(db, topic, msg);
    }

    @Override
    public <T> long msgSend(Topic topic, T data) {
        StoredMessage<T> msg = new StoredMessage<>();
        SQLiteDatabase db = mDbh.getWritableDatabase();

        msg.topic = topic.getName();
        msg.from = getMyUid();
        msg.ts = new Date();
        // Set seq to zero. MessageDb will assign a unique temporary (nagative int) seq.
        // The temp seq will be updated later, when the message is received by the server.
        msg.seq = 0;
        msg.content = data;

        msg.topicId = StoredTopic.getId(topic);
        if (mMyId < 0) {
            mMyId = UserDb.getId(db, msg.from);
        }
        msg.userId = mMyId;
        msg.senderIdx = 0;

        return MessageDb.insert(db, topic, msg);
    }

    @Override
    public boolean msgDelivered(long id, Date timestamp, int seq) {
        return MessageDb.delivered(mDbh.getWritableDatabase(), id, timestamp, seq);
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, int before) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDbh.getWritableDatabase(), st.id, before, true);
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, int[] list) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDbh.getWritableDatabase(), st.id, list, true);
    }

    @Override
    public boolean msgDelete(Topic topic, int before) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDbh.getWritableDatabase(), st.id, before, false);
    }

    @Override
    public boolean msgDelete(Topic topic, int[] list) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDbh.getWritableDatabase(), st.id, list, false);
    }

    @Override
    public boolean msgRecvByRemote(Subscription sub, int recv) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.id > 0) {
            result = SubscriberDb.updateRecv(mDbh.getWritableDatabase(), ss.id, recv);
        }
        return result;
    }

    @Override
    public boolean msgReadByRemote(Subscription sub, int read) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.id > 0) {
            result = SubscriberDb.updateRead(mDbh.getWritableDatabase(), ss.id, read);
        }
        return result;
    }
}
