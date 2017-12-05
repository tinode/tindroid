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

import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.User;
import co.tinode.tinodesdk.model.Drafty;
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
        mDbh.setUid(null);
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

                MessageDb.delete(db, st.id, -1,0, -1, false);
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
            return new Range(st.minLocalSeq, st.maxLocalSeq);
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
    public <Pu> long userAdd(User<Pu> user) {
        return UserDb.insert(mDbh.getWritableDatabase(), user);
    }

    @Override
    public <Pu> boolean userUpdate(User<Pu> user) {
        return UserDb.update(mDbh.getWritableDatabase(), user);
    }

    @Override
    public long msgReceived(Topic topic, Subscription sub, MsgServerData m) {
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        if (ss == null) {
           return -1;
        }

        StoredMessage msg = new StoredMessage(m);
        msg.topicId = ss.topicId;
        msg.userId = ss.userId;

        SQLiteDatabase db = mDbh.getWritableDatabase();
        try {
            db.beginTransaction();

            msg.id = MessageDb.insert(db, topic, msg);

            if (msg.id > 0 && TopicDb.msgReceived(db, topic, msg.ts, msg.seq)) {
                db.setTransactionSuccessful();
            }

        } catch (SQLException ex) {
            Log.d(TAG, "Failed to save message", ex);
        } finally {
            db.endTransaction();
        }

        return msg.id;
    }

    @Override
    public long msgSend(Topic topic, Drafty data) {
        StoredMessage msg = new StoredMessage();
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

        return MessageDb.insert(db, topic, msg);
    }

    @Override
    public boolean msgDelivered(Topic topic, long messageDbId, Date timestamp, int seq) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        boolean result = false;
        try {
            db.beginTransaction();

            if (MessageDb.delivered(mDbh.getWritableDatabase(), messageDbId, timestamp, seq) &&
                    TopicDb.msgReceived(db, topic, timestamp, seq)) {
                db.setTransactionSuccessful();
                result = true;
            }
        } catch (SQLException ex) {
            Log.d(TAG, "Exception while inserting message", ex);
        } finally {
            db.endTransaction();
        }
        return result;
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, int fromId, int toId) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDbh.getWritableDatabase(), st.id, -1, fromId, toId, true);
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, int[] list) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.delete(mDbh.getWritableDatabase(), st.id, -1, list, true);
    }

    @Override
    public boolean msgDelete(Topic topic, int delId, int fromId, int toId) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        StoredTopic st = (StoredTopic) topic.getLocal();
        boolean result = false;
        try {
            db.beginTransaction();

            if (TopicDb.msgDeleted(db, topic, delId) &&
                MessageDb.delete(mDbh.getWritableDatabase(), st.id, delId, fromId, toId, false)) {
                db.setTransactionSuccessful();
                result = true;
            }
        } catch (SQLException ex) {
            Log.d(TAG, "Exception while deleting message range", ex);
        } finally {
            db.endTransaction();
        }

        return result;
    }

    @Override
    public boolean msgDelete(Topic topic, int delId, int[] list) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        StoredTopic st = (StoredTopic) topic.getLocal();
        boolean result = false;
        try {
            db.beginTransaction();

            if (TopicDb.msgDeleted(db, topic, delId) &&
                    MessageDb.delete(mDbh.getWritableDatabase(), st.id, delId, list, false)) {
                db.setTransactionSuccessful();
                result = true;
            }
        } catch (SQLException ex) {
            Log.d(TAG, "Exception while deleting message list", ex);
        } finally {
            db.endTransaction();
        }
        return result;
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
    public <R extends Iterator<Message> & Closeable> R getUnsentMessages(Topic topic) {
        MessageList list = null;
        StoredTopic st = (StoredTopic)topic.getLocal();
        if (st != null && st.id > 0) {
            Cursor c = MessageDb.queryUnsent(mDbh.getReadableDatabase(), st.id);
            if (c != null) {
                list = new MessageList(c);
            }
        }
        return (R) list;
    }

    private static class MessageList implements Iterator<Message>, Closeable {
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
        public StoredMessage next() {
            StoredMessage msg = StoredMessage.readMessage(mCursor);
            mCursor.moveToNext();
            return msg;
        }
    }
}
