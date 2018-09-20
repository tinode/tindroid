package co.tinode.tindroid.db;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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
    public void updateDeviceToken(String token) {
        AccountDb.updateDeviceToken(mDbh.getWritableDatabase(), token);
    }

    @Override
    public String getDeviceToken() {
        return AccountDb.getDeviceToken(mDbh.getReadableDatabase());
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

                MessageDb.delete(db, st.id, 0, -1);
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
    public long subAdd(Topic topic, Subscription sub) {
        return SubscriberDb.insert(mDbh.getWritableDatabase(), StoredTopic.getId(topic), BaseDb.STATUS_SYNCED, sub);
    }

    @Override
    public long subNew(Topic topic, Subscription sub) {
        return SubscriberDb.insert(mDbh.getWritableDatabase(), StoredTopic.getId(topic), BaseDb.STATUS_QUEUED, sub);
    }

    @Override
    public boolean subUpdate(Topic topic, Subscription sub) {
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
    public User userGet(String uid) {
        return UserDb.readOne(mDbh.getReadableDatabase(), uid);
    }

    @Override
    public long userAdd(User user) {
        return UserDb.insert(mDbh.getWritableDatabase(), user);
    }

    @Override
    public boolean userUpdate(User user) {
        return UserDb.update(mDbh.getWritableDatabase(), user);
    }

    @Override
    public long msgReceived(Topic topic, Subscription sub, MsgServerData m) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        long topicId, userId;
        StoredSubscription ss = sub != null ? (StoredSubscription) sub.getLocal() : null;
        if (ss == null) {
            Log.d(TAG, "Message from an unknown subscriber " + m.from);
            StoredTopic st = (StoredTopic) topic.getLocal();
            topicId = st.id;
            userId = UserDb.getId(db, m.from);
        } else {
            topicId = ss.topicId;
            userId = ss.userId;
        }

        if (topicId < 0 || userId < 0) {
            Log.d(TAG, "Failed to save message, topicId=" + topicId + ", userId=" + userId);
            return -1;
        }

        StoredMessage msg = new StoredMessage(m);
        msg.topicId = topicId;
        msg.userId = userId;

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

    private long insertMessage(Topic topic, Drafty data, int initialStatus) {
        StoredMessage msg = new StoredMessage();
        SQLiteDatabase db = mDbh.getWritableDatabase();

        msg.topic = topic.getName();
        msg.from = getMyUid();
        msg.ts = new Date(new Date().getTime() + mTimeAdjustment);
        // Set seq to zero. MessageDb will assign a unique temporary (nagative int) seq.
        // The temp seq will be updated later, when the message is received by the server.
        msg.seq = 0;
        msg.status = initialStatus;
        msg.content = data;

        msg.topicId = StoredTopic.getId(topic);
        if (mMyId < 0) {
            mMyId = UserDb.getId(db, msg.from);
        }
        msg.userId = mMyId;

        return MessageDb.insert(db, topic, msg);
    }

    @Override
    public long msgSend(Topic topic, Drafty data) {
        return insertMessage(topic, data, BaseDb.STATUS_UNDEFINED);
    }

    @Override
    public long msgDraft(Topic topic, Drafty data) {
        return insertMessage(topic, data, BaseDb.STATUS_DRAFT);
    }

    @Override
    public boolean msgDraftUpdate(Topic topic, long messageDbId, Drafty data) {
        return MessageDb.updateStatusAndContent(mDbh.getWritableDatabase(), messageDbId, BaseDb.STATUS_UNDEFINED, data);
    }

    @Override
    public boolean msgReady(Topic topic, long messageDbId, Drafty data) {
        return MessageDb.updateStatusAndContent(mDbh.getWritableDatabase(), messageDbId, BaseDb.STATUS_QUEUED, data);
    }

    public boolean msgDiscard(Topic topic, long messageDbId) {
        return MessageDb.delete(mDbh.getWritableDatabase(), messageDbId);
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
            Log.d(TAG, "Exception while updating message", ex);
        } finally {
            db.endTransaction();
        }
        return result;
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, int fromId, int toId, boolean markAsHard) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.markDeleted(mDbh.getWritableDatabase(), st.id, fromId, toId, markAsHard);
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, List<Integer> list, boolean markAsHard) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return MessageDb.markDeleted(mDbh.getWritableDatabase(), st.id, list, markAsHard);
    }

    @Override
    public boolean msgDelete(Topic topic, int delId, int fromId, int toId) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        StoredTopic st = (StoredTopic) topic.getLocal();
        boolean result = false;
        try {
            db.beginTransaction();

            if (TopicDb.msgDeleted(db, topic, delId) &&
                MessageDb.delete(mDbh.getWritableDatabase(), st.id, fromId, toId)) {
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
    public boolean msgDelete(Topic topic, int delId, List<Integer> list) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        StoredTopic st = (StoredTopic) topic.getLocal();
        boolean result = false;
        try {
            db.beginTransaction();

            if (TopicDb.msgDeleted(db, topic, delId) &&
                    MessageDb.delete(mDbh.getWritableDatabase(), st.id, list)) {
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

    @Override
    public <T extends Storage.Message> T getMessageById(Topic topic, long dbMessageId) {
        Storage.Message msg = null;
        Cursor c = MessageDb.getMessageById(mDbh.getReadableDatabase(), dbMessageId);
        if (c != null && c.moveToFirst()) {
            msg = StoredMessage.readMessage(c);
            c.close();
        }
        return (T) msg;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R extends Iterator<Message> & Closeable> R getQueuedMessages(Topic topic) {
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

    @Override
    public List<Integer> getQueuedMessageDeletes(Topic topic, boolean hard) {
        StoredTopic st = (StoredTopic)topic.getLocal();
        List<Integer> list = null;
        if (st != null && st.id > 0) {
            Cursor c = MessageDb.queryDeleted(mDbh.getReadableDatabase(), st.id, hard);
            if (c != null && c.moveToFirst()) {
                list = new ArrayList<>(c.getCount());
                int i = 0;
                do {
                    list.add(StoredMessage.readSeqId(c));
                } while(c.moveToNext());
                c.close();
            }
        }
        return list;
    }

    private static class MessageList implements Iterator<Message>, Closeable {
        private Cursor mCursor;

        MessageList(Cursor cursor) {
            mCursor = cursor;
            mCursor.moveToFirst();
        }

        @Override
        public void close() {
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
