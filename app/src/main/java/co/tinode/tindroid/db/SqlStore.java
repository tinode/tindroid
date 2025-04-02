package co.tinode.tindroid.db;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteBlobTooBigException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.Closeable;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.User;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgRange;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Persistence for Tinode.
 */
public class SqlStore implements Storage {

    private static final String TAG = "SqlStore";

    private final BaseDb mDbh;
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
    public void setMyUid(String uid, String hostURI) {
        mDbh.setUid(uid, hostURI);
        mDbh.updateCredentials(null);
    }

    @Override
    public void updateCredentials(String[] credMethods) {
        mDbh.updateCredentials(credMethods);
    }

    @Override
    public void deleteAccount(String uid) {
        mDbh.deleteUid(uid);
    }

    @Override
    public String getServerURI() {
        return mDbh.getHostURI();
    }

    @Override
    public String getDeviceToken() {
        return AccountDb.getDeviceToken(mDbh.getReadableDatabase());
    }

    @Override
    public void saveDeviceToken(String token) {
        AccountDb.updateDeviceToken(mDbh.getWritableDatabase(), token);
    }

    @Override
    public void setTimeAdjustment(long adj) {
        mTimeAdjustment = adj;
    }

    public boolean isReady() {
        return mDbh.isReady();
    }

    public void logout() {
        // Clear the database.
        mDbh.setUid(null, null);
        mDbh.clearDb();
    }

    @Override
    public Topic[] topicGetAll(final Tinode tinode) {
        Cursor c = TopicDb.query(mDbh.getReadableDatabase());
        Topic[] list = null;
        if (c.moveToFirst()) {
            list = new Topic[c.getCount()];
            int i = 0;
            do {
                list[i++] = TopicDb.readOne(tinode, c);
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    @Override
    public Topic topicGet(final Tinode tinode, final String name) {
        return TopicDb.readOne(mDbh.getReadableDatabase(), tinode, name);
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
    public boolean topicDelete(Topic topic, boolean hard) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        boolean success = false;
        if (st != null) {
            SQLiteDatabase db = mDbh.getWritableDatabase();

            try {
                db.beginTransaction();

                if (hard) {
                    MessageDb.deleteAll(db, st.id);
                    SubscriberDb.deleteForTopic(db, st.id);
                    TopicDb.delete(db, st.id);
                } else {
                    TopicDb.markDeleted(db, st.id);
                }

                db.setTransactionSuccessful();
                success = true;

                topic.setLocal(null);
            } catch (SQLException ignored) {
            }

            db.endTransaction();
        }

        return success;
    }

    @Override
    public MsgRange[] msgIsCached(Topic topic, MsgRange[] ranges) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.id > 0) {
            return MessageDb.getCachedRanges(mDbh.getReadableDatabase(), st.id, ranges);
        }
        return null;
    }

    @Override
    public MsgRange getCachedMessagesRange(Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null) {
            return new MsgRange(st.minLocalSeq, st.maxLocalSeq + 1);
        }
        return null;
    }

    @Override
    public MsgRange[] getMissingRanges(Topic topic, int startFrom, int pageSize, boolean newer) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.id > 0) {
            return MessageDb.getMissingRanges(mDbh.getReadableDatabase(), st.id, startFrom, pageSize, newer);
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
        return SubscriberDb.insert(mDbh.getWritableDatabase(), StoredTopic.getId(topic), BaseDb.Status.SYNCED, sub);
    }

    @Override
    public long subNew(Topic topic, Subscription sub) {
        return SubscriberDb.insert(mDbh.getWritableDatabase(), StoredTopic.getId(topic), BaseDb.Status.QUEUED, sub);
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
    public Storage.Message msgReceived(Topic topic, Subscription sub, MsgServerData m) {
        final SQLiteDatabase db = mDbh.getWritableDatabase();
        long topicId, userId;
        StoredSubscription ss = sub != null ? (StoredSubscription) sub.getLocal() : null;
        if (ss == null) {
            Log.d(TAG, "Message from an unknown subscriber " + m.from);

            StoredTopic st = (StoredTopic) topic.getLocal();
            topicId = st.id;
            userId = UserDb.getId(db, m.from);
            if (userId < 0) {
                // Create a placeholder user to satisfy the foreign key constraint.
                if (sub != null) {
                    userId = UserDb.insert(db, sub);
                } else {
                    userId = UserDb.insert(db, m.from, m.ts, null);
                }
            }
        } else {
            topicId = ss.topicId;
            userId = ss.userId;
        }

        if (topicId < 0 || userId < 0) {
            Log.w(TAG, "Failed to save message, topicId=" + topicId + ", userId=" + userId);
            return null;
        }

        final StoredMessage msg = new StoredMessage(m);
        msg.topicId = topicId;
        msg.userId = userId;
        msg.status = BaseDb.Status.SYNCED;
        try {
            db.beginTransaction();

            msg.id = MessageDb.insert(db, topic, msg);

            if (msg.id > 0 && TopicDb.msgReceived(db, topic, msg.ts, msg.seq)) {
                db.setTransactionSuccessful();
            }

        } catch (SQLException ex) {
            Log.w(TAG, "Failed to save message", ex);
        } finally {
            db.endTransaction();
        }

        return msg;
    }

    private Storage.Message insertMessage(Topic topic, Drafty data, Map<String, Object> head,
                                          BaseDb.Status initialStatus) {
        StoredMessage msg = new StoredMessage();
        SQLiteDatabase db = mDbh.getWritableDatabase();

        if (topic == null) {
            Log.w(TAG, "Failed to insert message: topic is null");
            return null;
        }

        msg.topic = topic.getName();
        msg.from = getMyUid();
        msg.ts = new Date(new Date().getTime() + mTimeAdjustment);
        // Set seq to zero. MessageDb will assign a unique temporary (very large int, >= 2e9) seq.
        // The temp seq will be updated later, when the message is received by the server.
        msg.seq = 0;
        msg.status = initialStatus;
        msg.content = data;
        msg.head = head;

        msg.topicId = StoredTopic.getId(topic);
        if (mMyId < 0) {
            mMyId = UserDb.getId(db, msg.from);
        }
        msg.userId = mMyId;

        MessageDb.insert(db, topic, msg);

        return msg.id > 0 ? msg : null;
    }

    @Override
    public Storage.Message msgSend(Topic topic, Drafty data, Map<String, Object> head) {
        return insertMessage(topic, data, head, BaseDb.Status.SENDING);
    }

    @Override
    public Storage.Message msgDraft(Topic topic, Drafty data, Map<String, Object> head) {
        return insertMessage(topic, data, head, BaseDb.Status.DRAFT);
    }

    @Override
    public boolean msgDraftUpdate(Topic topic, long messageDbId, Drafty data) {
        return MessageDb.updateStatusAndContent(mDbh.getWritableDatabase(), messageDbId, BaseDb.Status.UNDEFINED, data);
    }

    @Override
    public boolean msgReady(Topic topic, long messageDbId, Drafty data) {
        return MessageDb.updateStatusAndContent(mDbh.getWritableDatabase(), messageDbId, BaseDb.Status.QUEUED, data);
    }

    @Override
    public boolean msgSyncing(Topic topic, long messageDbId, boolean sync) {
        return MessageDb.updateStatusAndContent(mDbh.getWritableDatabase(), messageDbId,
                sync ? BaseDb.Status.SENDING : BaseDb.Status.QUEUED, null);
    }

    @Override
    public boolean msgDiscard(Topic topic, long messageDbId) {
        return MessageDb.delete(mDbh.getWritableDatabase(), messageDbId);
    }

    @Override
    public boolean msgDiscardSeq(Topic topic, int seq) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }
        return MessageDb.delete(mDbh.getWritableDatabase(), st.id, seq);
    }

    @Override
    public boolean msgFailed(Topic topic, long messageDbId) {
        return MessageDb.updateStatusAndContent(mDbh.getWritableDatabase(), messageDbId,
                BaseDb.Status.FAILED, null);
    }

    @Override
    public boolean msgPruneFailed(Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }
        return MessageDb.deleteFailed(mDbh.getWritableDatabase(), st.id);
    }

    @Override
    public boolean msgDelivered(Topic topic, long messageDbId, Date timestamp, int seq) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        boolean result = false;
        try {
            db.beginTransaction();
            MessageDb.delivered(mDbh.getWritableDatabase(), messageDbId, timestamp, seq);
            TopicDb.msgReceived(db, topic, timestamp, seq);
            db.setTransactionSuccessful();
            result = true;
        } catch (SQLException ex) {
            Log.w(TAG, "Exception while updating message", ex);
        } finally {
            db.endTransaction();
        }
        return result;
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, int fromId, int toId, boolean markAsHard) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }
        return MessageDb.markDeleted(mDbh.getWritableDatabase(), st.id, fromId, toId, markAsHard);
    }

    @Override
    public boolean msgMarkToDelete(Topic topic, MsgRange[] ranges, boolean markAsHard) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }
        return MessageDb.markDeleted(mDbh.getWritableDatabase(), st.id, ranges, markAsHard);
    }

    @Override
    public boolean msgDelete(Topic topic, int delId, int fromId, int toId) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }
        if (toId <= 0) {
            toId = st.maxLocalSeq + 1;
        }
        boolean result = false;
        try {
            db.beginTransaction();

            if (TopicDb.msgDeleted(db, topic, delId, fromId, toId) &&
                    MessageDb.delete(db, st.id, delId, fromId, toId)) {
                db.setTransactionSuccessful();
                result = true;
            }
        } catch (Exception ex) {
            Log.w(TAG, "Exception while deleting message range", ex);
        } finally {
            db.endTransaction();
        }

        return result;
    }

    @Override
    public boolean msgDelete(Topic topic, int delId, MsgRange[] ranges) {
        SQLiteDatabase db = mDbh.getWritableDatabase();
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }
        ranges = MsgRange.collapse(ranges);
        MsgRange span = MsgRange.enclosing(ranges);
        boolean result = false;
        try {
            db.beginTransaction();

            if (TopicDb.msgDeleted(db, topic, delId, span.getLower(), span.getUpper()) &&
                    MessageDb.delete(db, st.id, delId, ranges)) {
                db.setTransactionSuccessful();
                result = true;
            }
        } catch (Exception ex) {
            Log.w(TAG, "Exception while deleting message list", ex);
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

    private <T extends Storage.Message> T messageById(long dbMessageId, int previewLength) {
        T msg = null;
        Cursor c = MessageDb.getMessageById(mDbh.getReadableDatabase(), dbMessageId);
        if (c.moveToFirst()) {
            //noinspection unchecked
            msg = (T) StoredMessage.readMessage(c, previewLength);
        }
        c.close();
        return msg;
    }

    @Override
    public <T extends Storage.Message> T getMessageById(long dbMessageId) {
        return messageById(dbMessageId, -1);
    }

    @Override
    public <T extends Storage.Message> T getMessageBySeq(Topic topic, int seq) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return null;
        }
        T msg = null;
        Cursor c = MessageDb.getMessageBySeq(mDbh.getReadableDatabase(), st.id, seq);
        if (c.moveToFirst()) {
            //noinspection unchecked
            msg = (T) StoredMessage.readMessage(c, -1);
        }
        c.close();
        return msg;
    }

    @Override
    public <T extends Storage.Message> T getMessagePreviewById(long dbMessageId) {
        return messageById(dbMessageId, MessageDb.MESSAGE_PREVIEW_LENGTH);
    }

    @Override
    public int[] getAllMsgVersions(Topic topic, int seq, int limit) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return null;
        }
        return MessageDb.getAllVersions(mDbh.getReadableDatabase(), st.id, seq, limit);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R extends Iterator<Message> & Closeable> R getQueuedMessages(Topic topic) {
        MessageList list = null;
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null && st.id > 0) {
            Cursor c = MessageDb.queryUnsent(mDbh.getReadableDatabase(), st.id);
            if (c.moveToFirst()) {
                list = new MessageList(c, -1);
            }
        }
        return (R) list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R extends Iterator<Message> & Closeable> R getLatestMessagePreviews() {
        MessageList list = null;
        Cursor c = MessageDb.getLatestMessages(mDbh.getReadableDatabase());
        try {
            if (c.moveToFirst()) {
                list = new MessageList(c, MessageDb.MESSAGE_PREVIEW_LENGTH);
            }
        } catch (SQLiteBlobTooBigException ex) {
            Log.w(TAG, "Failed to read message (misconfigured server):", ex);
        }
        return (R) list;
    }

    @Override
    public MsgRange[] getQueuedMessageDeletes(Topic topic, boolean hard) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        MsgRange[] range = null;
        if (st != null && st.id > 0) {
            Cursor c = MessageDb.queryDeleted(mDbh.getReadableDatabase(), st.id, hard);
            if (c.moveToFirst()) {
                range = new MsgRange[c.getCount()];
                int i = 0;
                do {
                    range[i++] = StoredMessage.readDelRange(c);
                } while (c.moveToNext());
            }
            c.close();
        }
        return range;
    }

    private static class MessageList implements Iterator<Message>, Closeable {
        private final Cursor mCursor;
        private final int mPreviewLength;

        MessageList(Cursor cursor, int previewLength) {
            mCursor = cursor;
            mPreviewLength = previewLength;
        }

        @Override
        public void close() {
            mCursor.close();
        }

        @Override
        public boolean hasNext() {
            return !mCursor.isAfterLast();
        }

        @Override
        public StoredMessage next() {
            StoredMessage msg = StoredMessage.readMessage(mCursor, mPreviewLength);
            mCursor.moveToNext();
            return msg;
        }
    }
}
