package co.tinode.tindroid.db;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.User;
import co.tinode.tinodesdk.model.Announcement;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Persistence for Tinode.
 */
class SqlStore implements Storage {

    private static final String TAG = "SqlStore";

    private BaseDb mDbh;
    private long mMyId = -1;
    private long mTimeAdjustment = 0;

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

    @Override
    public void setTimeAdjustment(long adj) {
        mTimeAdjustment = adj;
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
        return (st == null) ? TopicDb.insert(mDbh.getWritableDatabase(), topic) : st.id;
    }

    @Override
    public boolean topicUpdate(Topic topic) {
        return TopicDb.update(mDbh.getWritableDatabase(), topic);
    }

    @Override
    public boolean topicDelete(Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        boolean success = false;
        if (st != null) {
            //Log.d(TAG, "deleting topic " + topic.getName());
            SQLiteDatabase db = mDbh.getWritableDatabase();

            try {
                db.beginTransaction();

                MessageDb.delete(db, st.id, -1, false);
                SubscriberDb.deleteForTopic(db, st.id);
                TopicDb.delete(db, st.id);

                db.setTransactionSuccessful();
                success = true;

                // Log.d(TAG, "SUCCESS deleting topic " + topic.getName());
            } catch (SQLException ignored) {
                // Log.e(TAG, "Topic deletion failed", ignored);
            }

            db.endTransaction();
        }

        return success;
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
        return SubscriberDb.insert(mDbh.getWritableDatabase(), StoredTopic.getId(topic), BaseDb.STATUS_SYNCED, sub);
    }

    @Override
    public <Pu, Pr> long subNew(Topic topic, Subscription<Pu, Pr> sub) {
        return SubscriberDb.insert(mDbh.getWritableDatabase(), StoredTopic.getId(topic), BaseDb.STATUS_QUEUED, sub);
    }

    @Override
    public <Pu, Pr> boolean subUpdate(Topic topic, Subscription<Pu, Pr> sub) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.id > 0) {
            result = SubscriberDb.update(mDbh.getWritableDatabase(), sub);
        }
        return result;
    }

    @Override
    public boolean subDelete(Topic topic, Subscription sub) {
        boolean result = false;
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss != null && ss.id > 0) {
            result = SubscriberDb.delete(mDbh.getWritableDatabase(), ss.id);
        }
        return result;
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
    public <Pu> User<Pu> userGet(String uid) {
        return UserDb.readOne(mDbh.getReadableDatabase(), uid);
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
    public <T> long inviteReceived(Topic topic, MsgServerData<Announcement<T>> m) {
        StoredMessage<Announcement<T>> msg = new StoredMessage<>(m);
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return -1;
        }

        SQLiteDatabase db = mDbh.getWritableDatabase();

        db.beginTransaction();
        msg.userId = UserDb.getId(db, m.from);
        if (msg.userId < 0) {
            msg.userId = UserDb.insert(db, m.from, null);
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
    public <T> long msgSend(Topic<?,?,T> topic, T data) {
        StoredMessage<T> msg = new StoredMessage<>();
        SQLiteDatabase db = mDbh.getWritableDatabase();

        msg.topic = topic.getName();
        msg.from = getMyUid();
        msg.ts = new Date(new Date().getTime() + mTimeAdjustment);
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
    public boolean msgDelivered(Topic topic, long messageDbId, Date timestamp, int seq) {
        return MessageDb.delivered(mDbh.getWritableDatabase(), topic, messageDbId, timestamp, seq);
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

    @SuppressWarnings("unchecked")
    @Override
    public <R extends Iterator<Message<T>> & Closeable, T> R getUnsentMessages(Topic<?, ?, T> topic) {
        MessageList<T> list = null;
        StoredTopic st = (StoredTopic)topic.getLocal();
        if (st != null && st.id > 0) {
            Cursor c = MessageDb.queryUnsent(mDbh.getReadableDatabase(), st.id);
            if (c != null) {
                list = new MessageList<>(c);
            }
        }
        return (R) list;
    }

    private static class MessageList<T> implements Iterator<Message<T>>, Closeable {
        private Cursor mCursor;

        MessageList(Cursor cursor) {
            mCursor = cursor;
            mCursor.moveToFirst();
        }

        @Override
        public void close() throws IOException {
            mCursor.close();
        }

        @Override
        public boolean hasNext() {
            return !mCursor.isAfterLast();
        }

        @SuppressWarnings("unchecked")
        @Override
        public StoredMessage<T> next() {
            StoredMessage<T> msg = MessageDb.readMessage(mCursor);
            mCursor.moveToNext();
            return msg;
        }
    }
}
