package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.content.CursorLoader;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import co.tinode.tinodesdk.Topic;

/**
 * Storage structure for messages:
 * public String id -- not stored
 * public String topic -> as topic_id
 * public String from; -> as user_id
 * public Map head -- not stored yet
 * public Date ts;
 * public int seq;
 * public T content;
 */
public class MessageDb implements BaseColumns {
    private static final String TAG = "MessageDb";

    /**
     * The name of the main table.
     */
    static final String TABLE_NAME = "messages";

    /**
     * Content URI for retrieving messages (content://co.tinode.tindroid/messages)
     */
    static final Uri CONTENT_URI = Uri.withAppendedPath(BaseDb.BASE_CONTENT_URI, TABLE_NAME);

    /**
     * Topic ID, references topics._ID
     */
    private static final String COLUMN_NAME_TOPIC_ID = "topic_id";
    /**
     * Id of the originator of the message, references users._ID
     */
    private static final String COLUMN_NAME_USER_ID = "user_id";
    /**
     * Status of the message: unsent, delivered, deleted
     */
    private static final String COLUMN_NAME_STATUS = "status";
    /**
     * Uid as string. Deserialized here to avoid a join.
     */
    private static final String COLUMN_NAME_SENDER = "sender";
    /**
     * Message timestamp
     */
    private static final String COLUMN_NAME_TS = "ts";
    /**
     * Server-issued sequence ID, integer, indexed
     */
    private static final String COLUMN_NAME_SEQ = "seq";
    /**
     * Serialized message content
     */
    private static final String COLUMN_NAME_CONTENT = "content";


    /**
     * SQL statement to create Messages table
     */
    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_TOPIC_ID
                    + " REFERENCES " + TopicDb.TABLE_NAME + "(" + TopicDb._ID + ")," +
                    COLUMN_NAME_USER_ID
                    + " REFERENCES " + UserDb.TABLE_NAME + "(" + UserDb._ID + ")," +
                    COLUMN_NAME_STATUS + " INT," +
                    COLUMN_NAME_SENDER + " TEXT," +
                    COLUMN_NAME_TS + " INT," +
                    COLUMN_NAME_SEQ + " INT," +
                    COLUMN_NAME_CONTENT + " TEXT)";
    /**
     * SQL statement to drop Messages table.
     */
    static final String DROP_TABLE =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    /**
     * The name of index: messages by topic and sequence.
     */
    private static final String INDEX_NAME = "message_topic_id_seq";
    /**
     * Drop the index too
     */
    static final String DROP_INDEX =
            "DROP INDEX IF EXISTS " + INDEX_NAME;
    /**
     * Add index on account_id-topic-seq, in descending order
     */
    static final String CREATE_INDEX =
            "CREATE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_TS + " DESC)";

    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_TOPIC_ID = 1;
    static final int COLUMN_IDX_USER_ID = 2;
    static final int COLUMN_IDX_STATUS = 3;
    static final int COLUMN_IDX_SENDER = 4;
    static final int COLUMN_IDX_TS = 5;
    static final int COLUMN_IDX_SEQ = 6;
    static final int COLUMN_IDX_CONTENT = 7;

    /**
     * Save message to DB
     *
     * @return ID of the newly added message
     */
    static long insert(SQLiteDatabase db, Topic topic, StoredMessage msg) {
        if (msg.id > 0) {
            return msg.id;
        }

        db.beginTransaction();
        try {
            if (msg.topicId <= 0) {
                msg.topicId = TopicDb.getId(db, msg.topic);
            }
            if (msg.userId <= 0) {
                msg.userId = UserDb.getId(db, msg.from);
            }

            if (msg.userId <= 0 || msg.topicId <= 0) {
                Log.d(TAG, "Failed to insert message " + msg.seq);
                return -1;
            }

            int status;
            if (msg.seq == 0) {
                msg.seq = TopicDb.getNextUnsentSeq(db, topic);
                status = msg.status == BaseDb.STATUS_UNDEFINED ? BaseDb.STATUS_QUEUED : msg.status;
            } else {
                status = BaseDb.STATUS_SYNCED;
            }

            // Convert message to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, msg.topicId);
            values.put(COLUMN_NAME_USER_ID, msg.userId);
            values.put(COLUMN_NAME_STATUS, status);
            values.put(COLUMN_NAME_SENDER, msg.from);
            values.put(COLUMN_NAME_TS, msg.ts.getTime());
            values.put(COLUMN_NAME_SEQ, msg.seq);
            values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(msg.content));

            msg.id = db.insertOrThrow(TABLE_NAME, null, values);
            db.setTransactionSuccessful();
        } catch (Exception ex) {
            Log.e(TAG, "Insert failed", ex);
        } finally {
            db.endTransaction();
        }

        return msg.id;
    }

    static boolean updateStatusAndContent(SQLiteDatabase db, long msgId, int status, Object content) {
        ContentValues values = new ContentValues();
        if (status != BaseDb.STATUS_UNDEFINED) {
            values.put(COLUMN_NAME_STATUS, status);
        }
        if (content != null) {
            values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(content));
        }

        if (values.size() > 0) {
            return db.update(TABLE_NAME, values, _ID + "=" + msgId, null) > 0;
        }
        return false;
    }

    static boolean delivered(SQLiteDatabase db, long msgId, Date timestamp, int seq) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_STATUS, BaseDb.STATUS_SYNCED);
        values.put(COLUMN_NAME_TS, timestamp.getTime());
        values.put(COLUMN_NAME_SEQ, seq);
        int updated = db.update(TABLE_NAME, values, _ID + "=" + msgId, null);
        return updated > 0;
    }

    /**
     * Query messages. To select all messages set <b>from</b> and <b>to</b> equal to -1.
     *
     * @param db      database to select from;
     * @param topicId Tinode topic ID (topics._id) to select from
     * @param from    minimum seq value to select, exclusive
     * @param to      maximum seq value to select, inclusive
     * @return cursor with the messages
     */
    public static Cursor query(SQLiteDatabase db, long topicId, int from, int to, int limit) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_TOPIC_ID + "=" + topicId +
                (from > 0 ? " AND " + COLUMN_NAME_SEQ + ">" + from : "") +
                (to > 0 ? " AND " + COLUMN_NAME_SEQ + "<=" + to : "") +
                " AND " + COLUMN_NAME_STATUS + "<=" + BaseDb.STATUS_VISIBLE +
                " ORDER BY " + COLUMN_NAME_TS +
                (limit > 0 ? " LIMIT " + limit : "");

        // Log.d(TAG, "Sql=[" + sql + "]");

        return db.rawQuery(sql, null);
    }

    /**
     * Query messages. To select all messages set <b>from</b> and <b>to</b> equal to -1.
     *
     * @param db        database to select from;
     * @param topicId   Tinode topic ID (topics._id) to select from
     * @param pageCount number of pages to return
     * @param pageSize  number of messages per page
     * @return cursor with the messages.
     */
    public static Cursor query(SQLiteDatabase db, long topicId, int pageCount, int pageSize) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_TOPIC_ID + "=" + topicId +
                " AND " + COLUMN_NAME_STATUS + "<=" + BaseDb.STATUS_VISIBLE +
                " ORDER BY " + COLUMN_NAME_TS + " DESC LIMIT " + (pageCount * pageSize);

        // Log.d(TAG, "Sql=[" + sql + "]");

        return db.rawQuery(sql, null);
    }

    /**
     * Query messages. To select all messages set <b>from</b> and <b>to</b> equal to -1.
     *
     * @param db     database to select from;
     * @param msgId  _id of the message to retrieve.
     * @return cursor with the message.
     */
    public static Cursor getMessageById(SQLiteDatabase db, long msgId) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE _id=" + msgId;

        // Log.d(TAG, "Sql=[" + sql + "]");

        return db.rawQuery(sql, null);
    }

    /**
     * Query messages which are ready for sending but has not been sent yet.
     *
     * @param db      database to select from;
     * @param topicId Tinode topic ID (topics._id) to select from
     * @return cursor with the messages
     */
    public static Cursor queryUnsent(SQLiteDatabase db, long topicId) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_TOPIC_ID + "=" + topicId +
                " AND " + COLUMN_NAME_STATUS + "=" + BaseDb.STATUS_QUEUED +
                " ORDER BY " + COLUMN_NAME_TS;
        // Log.d(TAG, "Sql=[" + sql + "]");

        return db.rawQuery(sql, null);
    }

    /**
     * Query messages marked for deletion but not deleted yet.
     *
     * @param db      database to select from;
     * @param topicId Tinode topic ID (topics._id) to select from;
     * @param hard    if true to return hard-deleted messages, soft-deleted otherwise.
     * @return cursor with the message seqIDs
     */
    public static Cursor queryDeleted(SQLiteDatabase db, long topicId, boolean hard) {
        int status = hard ? BaseDb.STATUS_DELETED_HARD : BaseDb.STATUS_DELETED_SOFT;

        String sql = "SELECT " + COLUMN_NAME_SEQ + " FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId +
                " AND " + COLUMN_NAME_STATUS + "=" + status +
                " ORDER BY " + COLUMN_NAME_TS;

        // Log.d(TAG, "Sql=[" + sql + "]");

        return db.rawQuery(sql, null);
    }

    /**
     * Mark sent messages as deleted without actually deleting them. Delete unsent messages.
     *
     * @param db            Database to use.
     * @param doDelete      delete messages instead of marking them deleted.
     * @param topicId       Tinode topic ID to delete messages from.
     * @param fromId        minimum seq value to delete, inclusive (closed).
     * @param toId          maximum seq value to delete, exclusive (open).
     * @param list          list of message IDs to delete.
     * @param markAsHard    mark messages as hard-deleted.
     * @return true if some messages were updated or deleted, false otherwise
     */
    private static boolean deleteOrMarkDeleted(SQLiteDatabase db, boolean doDelete, long topicId,
                                               int fromId, int toId, List<Integer> list, boolean markAsHard) {
        int affected = 0;
        db.beginTransaction();
        String messageSelector;
        if (list != null) {
            StringBuilder sb = new StringBuilder();
            for (int i : list) {
                sb.append(",");
                sb.append(i);
            }
            sb.deleteCharAt(0);
            messageSelector = COLUMN_NAME_SEQ + " IN (" + sb.toString() + ")";
        } else {
            ArrayList<String> parts = new ArrayList<>();
            if (fromId > 0) {
                parts.add(COLUMN_NAME_SEQ + ">=" + fromId);
            }
            if (toId != -1) {
                parts.add(COLUMN_NAME_SEQ + "<" + toId);
            }
            messageSelector = TextUtils.join(" AND ", parts);
        }

        if (!TextUtils.isEmpty(messageSelector)) {
            messageSelector = " AND " + messageSelector;
        }

        try {
            if (!doDelete) {
                // Mark sent messages as deleted
                ContentValues values = new ContentValues();
                values.put(COLUMN_NAME_STATUS, markAsHard ? BaseDb.STATUS_DELETED_HARD : BaseDb.STATUS_DELETED_SOFT);
                affected = db.update(TABLE_NAME, values, COLUMN_NAME_TOPIC_ID + "=" + topicId +
                        messageSelector +
                        " AND " + COLUMN_NAME_STATUS + "=" + BaseDb.STATUS_SYNCED, null);
            }
            // Unsent messages are deleted.
            affected += db.delete(TABLE_NAME, COLUMN_NAME_TOPIC_ID + "=" + topicId +
                    messageSelector +
                    // Either delete all messages or just unsent+draft messages.
                    (doDelete ? "" : " AND " + COLUMN_NAME_STATUS + "<=" + BaseDb.STATUS_QUEUED), null);
            db.setTransactionSuccessful();
        } catch (SQLException ex) {
            Log.d(TAG, "Delete failed", ex);
        } finally {
            db.endTransaction();
        }
        return affected > 0;
    }

    /**
     * Mark sent messages as deleted without actually deleting them. Delete unsent messages.
     *
     * @param db            Database to use.
     * @param topicId       Tinode topic ID to delete messages from.
     * @param list          list of message IDs to delete.
     * @param markAsHard    mark messages as hard-deleted.
     * @return true if some messages were updated or deleted, false otherwise
     */
    public static boolean markDeleted(SQLiteDatabase db, long topicId, List<Integer> list, boolean markAsHard) {
        return deleteOrMarkDeleted(db, false, topicId, Integer.MAX_VALUE, 0, list, markAsHard);
    }

    /**
     * Mark sent messages as deleted without actually deleting them. Delete unsent messages.
     *
     * @param db            Database to use.
     * @param topicId       Tinode topic ID to delete messages from.
     * @param fromId        minimum seq value to delete, inclusive (closed).
     * @param toId          maximum seq value to delete, exclusive (open).
     * @param markAsHard    mark messages as hard-deleted.
     * @return true if some messages were updated or deleted, false otherwise
     */
    public static boolean markDeleted(SQLiteDatabase db, long topicId, int fromId, int toId, boolean markAsHard) {
        return deleteOrMarkDeleted(db, false, topicId, fromId, toId, null, markAsHard);
    }

    /**
     * Delete messages between 'from' and 'to'. To delete all messages make before equal to -1.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @param fromId  minimum seq value to delete, inclusive (closed).
     * @param toId    maximum seq value to delete, exclusive (open)
     * @return number of deleted messages
     */
    public static boolean delete(SQLiteDatabase db, long topicId, int fromId, int toId) {
        return deleteOrMarkDeleted(db, true, topicId, fromId, toId, null, false);
    }

    /**
     * Delete messages between 'from' and 'to'. To delete all messages make from and to equal to -1.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @param list    maximum seq value to delete, inclusive.
     * @return number of deleted messages
     */
    public static boolean delete(SQLiteDatabase db, long topicId, List<Integer> list) {
        return deleteOrMarkDeleted(db, true, topicId, Integer.MAX_VALUE, 0, list, false);
    }

    /**
     * Delete messages by database ID.
     *
     * @param db      Database to use.
     * @param msgId   Database ID of the message (_id).
     * @return true on success, false on failure
     */
    static boolean delete(SQLiteDatabase db, long msgId) {
        return db.delete(TABLE_NAME, _ID + "=" + msgId, null) > 0;
    }

    /**
     * Get locally-unique ID of the message (content of _ID field).
     *
     * @param cursor Cursor to query
     * @return _id of the message at the current position.
     */
    public static long getLocalId(Cursor cursor) {
        return cursor.isClosed() ? -1 : cursor.getLong(0);
    }

    /**
     * Get locally-unique ID of the message (content of _ID field).
     *
     * @param cursor Cursor to query
     * @return _id of the message at the current position.
     */
    public static long getId(Cursor cursor) {
        return cursor.getLong(0);
    }

    public static class Loader extends CursorLoader {
        SQLiteDatabase mDb;

        private long topicId;
        private int pageCount;
        private int pageSize;

        public Loader(Context context, String topic, int pageCount, int pageSize) {
            super(context);

            mDb = BaseDb.getInstance().getReadableDatabase();
            this.topicId = TopicDb.getId(mDb, topic);
            this.pageCount = pageCount;
            this.pageSize = pageSize;
        }

        @Override
        public Cursor loadInBackground() {
            return query(mDb, topicId, pageCount, pageSize);
        }
    }
}
