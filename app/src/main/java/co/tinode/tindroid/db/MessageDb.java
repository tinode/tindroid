package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import androidx.loader.content.CursorLoader;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;

import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgRange;

/**
 * The table contains messages synchronized with the server and not yet synchronized.
 * It also contains message deletion markers, synchronized and not yet synchronized.
 *
 * Storage structure for messages:
 * public String id -> _id
 * public String topic -> as topic_id
 * public String from; -> as user_id
 * public Date ts;
 * public int seq;
 * public Map head -> serialized into JSON;
 * public T content -> serialized into JSON;
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
    static final String COLUMN_NAME_TOPIC_ID = "topic_id";
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
     * Server-issued sequence ID, integer, indexed. If the message represents
     * a deleted range, then <tt>seq</tt> is the lowest bound of the range;
     * the bound is closed (inclusive).
     */
    private static final String COLUMN_NAME_SEQ = "seq";
    /**
     * If message represents a deleted range, this is the upper bound of the range, NULL otherwise.
     * The bound is open (exclusive).
     */
    private static final String COLUMN_NAME_HIGH = "high";
    /**
     * If message represents a deleted range, ID of the deletion record.
     */
    private static final String COLUMN_NAME_DEL_ID = "del_id";
    /**
     * Serialized header.
     */
    private static final String COLUMN_NAME_HEAD = "head";
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
                    COLUMN_NAME_HIGH + " INT," +
                    COLUMN_NAME_DEL_ID + " INT," +
                    COLUMN_NAME_HEAD + " TEXT," +
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
     * Add unique index on topic-seq, in descending order
     */
    static final String CREATE_INDEX =
            "CREATE UNIQUE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_SEQ + " DESC)";

    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_TOPIC_ID = 1;
    static final int COLUMN_IDX_USER_ID = 2;
    static final int COLUMN_IDX_STATUS = 3;
    static final int COLUMN_IDX_SENDER = 4;
    static final int COLUMN_IDX_TS = 5;
    static final int COLUMN_IDX_SEQ = 6;
    static final int COLUMN_IDX_HIGH = 7;
    static final int COLUMN_IDX_DEL_ID = 8;
    static final int COLUMN_IDX_HEAD = 9;
    static final int COLUMN_IDX_CONTENT = 10;

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
                Log.w(TAG, "Failed to insert message " + msg.seq);
                return -1;
            }

            BaseDb.Status status;
            if (msg.seq == 0) {
                msg.seq = TopicDb.getNextUnsentSeq(db, topic);
                status = msg.status == BaseDb.Status.UNDEFINED ? BaseDb.Status.QUEUED : msg.status;
            } else {
                status = BaseDb.Status.SYNCED;
            }

            // Convert message to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, msg.topicId);
            values.put(COLUMN_NAME_USER_ID, msg.userId);
            values.put(COLUMN_NAME_STATUS, status.value);
            values.put(COLUMN_NAME_SENDER, msg.from);
            values.put(COLUMN_NAME_TS, msg.ts != null ? msg.ts.getTime() : null);
            values.put(COLUMN_NAME_SEQ, msg.seq);
            values.put(COLUMN_NAME_HEAD, BaseDb.serialize(msg.head));
            values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(msg.content));

            msg.id = db.insertOrThrow(TABLE_NAME, null, values);
            db.setTransactionSuccessful();
        } catch (SQLiteConstraintException ex) {
            // Duplicate topics_id - seq value? Try finding the original.
            msg.id = getId(db, msg.topicId, msg.seq);
            if (msg.id <= 0) {
                Log.w(TAG, "Insert failed", ex);
            }
        } catch (Exception ex) {
            Log.w(TAG, "Insert failed", ex);
        } finally {
            db.endTransaction();
        }

        return msg.id;
    }

    static boolean updateStatusAndContent(SQLiteDatabase db, long msgId, BaseDb.Status status, Object content) {
        ContentValues values = new ContentValues();
        if (status != BaseDb.Status.UNDEFINED) {
            values.put(COLUMN_NAME_STATUS, status.value);
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
        values.put(COLUMN_NAME_STATUS, BaseDb.Status.SYNCED.value);
        values.put(COLUMN_NAME_TS, timestamp.getTime());
        values.put(COLUMN_NAME_SEQ, seq);
        int updated = db.update(TABLE_NAME, values, _ID + "=" + msgId, null);
        return updated > 0;
    }


    /**
     * Query latest messages
     *
     * @param db        database to select from;
     * @param topicId   Tinode topic ID (topics._id) to select from
     * @param pageCount number of pages to return
     * @param pageSize  number of messages per page
     * @return cursor with the messages.
     */
    public static Cursor query(SQLiteDatabase db, long topicId, int pageCount, int pageSize) {
        final String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE "
                        + COLUMN_NAME_TOPIC_ID + "=" + topicId +
                " ORDER BY "
                    + COLUMN_NAME_SEQ + " DESC" +
                " LIMIT " + (pageCount * pageSize);

        return db.rawQuery(sql, null);
    }

    /**
     * Query messages. To select all messages set <b>from</b> and <b>to</b> equal to -1.
     *
     * @param db     database to select from;
     * @param msgId  _id of the message to retrieve.
     * @return cursor with the message.
     */
    static Cursor getMessageById(SQLiteDatabase db, long msgId) {
        final String sql = "SELECT * FROM " + TABLE_NAME + " WHERE _id=" + msgId;

        return db.rawQuery(sql, null);
    }

    /**
     * Query messages which are ready for sending but has not been sent yet.
     *
     * @param db      database to select from;
     * @param topicId Tinode topic ID (topics._id) to select from
     * @return cursor with the messages
     */
    static Cursor queryUnsent(SQLiteDatabase db, long topicId) {
        final String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_TOPIC_ID + "=" + topicId +
                " AND " + COLUMN_NAME_STATUS + "=" + BaseDb.Status.QUEUED.value +
                " ORDER BY " + COLUMN_NAME_TS;

        return db.rawQuery(sql, null);
    }

    /**
     * Query messages marked for deletion but not deleted yet.
     *
     * @param db      database to select from;
     * @param topicId Tinode topic ID (topics._id) to select from;
     * @param hard    if true to return hard-deleted messages, soft-deleted otherwise.
     * @return cursor with the ranges of deleted message seq IDs
     */
    static Cursor queryDeleted(SQLiteDatabase db, long topicId, boolean hard) {
        BaseDb.Status status = hard ? BaseDb.Status.DELETED_HARD : BaseDb.Status.DELETED_SOFT;

        final String sql = "SELECT " +
                COLUMN_NAME_DEL_ID + "," +
                COLUMN_NAME_SEQ + "," +
                COLUMN_NAME_HIGH +
                " FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId +
                " AND " + COLUMN_NAME_STATUS + "=" + status.value +
                " ORDER BY " + COLUMN_NAME_SEQ;

        return db.rawQuery(sql, null);
    }

    /**
     * Find the latest missing range of messages for fetching from the server.
     *
     * @param db database to select from;
     * @param topicId Tinode topic ID (topics._id) to select from;
     * @return range of missing IDs if found, null if either all messages are present or no messages are found.
     */
    static MsgRange getNextMissingRange(SQLiteDatabase db, long topicId) {
        int high = 0;
        final String sqlHigh = "SELECT MAX(m1." + COLUMN_NAME_SEQ + ") AS missing" +
                " FROM " + TABLE_NAME + " AS m1" +
                " LEFT JOIN " + TABLE_NAME + " AS m2" +
                " ON m1." + COLUMN_NAME_SEQ + "=IFNULL(m2." + COLUMN_NAME_HIGH + ", m2." + COLUMN_NAME_SEQ + "+1)" +
                    " AND m2." + COLUMN_NAME_TOPIC_ID + "=?" +
                " WHERE m2." + COLUMN_NAME_SEQ + " IS NULL" +
                    " AND m1." + COLUMN_NAME_SEQ + ">1" +
                    " AND m1." + COLUMN_NAME_TOPIC_ID + "=?";

        Cursor c = db.rawQuery(sqlHigh, new String[]{ Long.toString(topicId), Long.toString(topicId) });
        if (c != null) {
            if (c.moveToFirst()) {
                high = c.getInt(0);
            }
            c.close();
        }

        if (high <= 0) {
            // No gap is found.
            return null;
        }
        // Find the first present message with ID less than the 'high'.
        final String sqlLow = "SELECT MAX(IFNULL(" + COLUMN_NAME_HIGH + "-1," + COLUMN_NAME_SEQ + ")) AS present" +
                " FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME_SEQ + "<?" +
                " AND " + COLUMN_NAME_TOPIC_ID + "=?";
        int low = 1;
        c = db.rawQuery(sqlLow, new String[]{ Integer.toString(high), Long.toString(topicId) });
        if (c != null) {
            if (c.moveToFirst()) {
                low = c.getInt(0) + 1; // Low is inclusive thus +1.
            }
            c.close();
        }

        return new MsgRange(low, high);
    }

    /**
     * Delete messages replacing them with deletion markers.
     *
     * @param db            Database to use.
     * @param topicId       Tinode topic ID to delete messages from.
     * @param delId         Server-issued delete record ID. If delId <= 0, the operation is not
     *                      yet synced with the server.
     * @param fromId        minimum seq value to delete, inclusive (closed).
     * @param toId          maximum seq value to delete, exclusive (open).
     * @param markAsHard    mark messages as hard-deleted.
     * @return true if some messages were updated or deleted, false otherwise
     */
    private static boolean deleteOrMarkDeleted(SQLiteDatabase db, long topicId, int delId, int fromId, int toId,
                                               boolean markAsHard) {
        // 1. Delete all messages within the give range (sent and unsent).
        // 2. Delete all unsynchronized (soft and hard) deletion ranges fully within this range
        // (no point in synchronizing them, they are superseded).
        // 3.1 If server record, consume older partially overlapping server records,
        // 3.2 If client hard-record, consume partially overlapping client hard-records.
        // 3.3 If client soft-record, consume partially overlapping client soft records.
        // 4. Expand current record to consumed range.

        boolean success = false;

        // Message selector: all messages in a given topic with seq between fromId and toId [inclusive, exclusive).
        String messageSelector = COLUMN_NAME_TOPIC_ID + "=" + topicId;
        ArrayList<String> parts = new ArrayList<>();
        if (fromId > 0) {
            parts.add(COLUMN_NAME_SEQ + ">=" + fromId);
        }
        parts.add(COLUMN_NAME_SEQ + "<" + toId);
        messageSelector += " AND " + TextUtils.join(" AND ", parts) +
                " AND " + COLUMN_NAME_STATUS + "<=" + BaseDb.Status.SYNCED.value;

        // Selector of ranges which are fully within the new range.
        parts.clear();
        String rangeDeleteSelector = COLUMN_NAME_TOPIC_ID + "=" + topicId;
        if (fromId > 0) {
            parts.add(COLUMN_NAME_SEQ + ">=" + fromId);
        }
        parts.add(COLUMN_NAME_HIGH + "<=" + toId);
        // All types: server, soft and hard.
        rangeDeleteSelector += " AND " + TextUtils.join(" AND ", parts) +
                " AND " + COLUMN_NAME_STATUS + ">=" + BaseDb.Status.DELETED_HARD.value;

        // Selector of partially overlapping deletion ranges. Find bounds of existing deletion ranges of the same type
        // which partially overlap with the new deletion range.
        String rangeConsumeSelector = COLUMN_NAME_TOPIC_ID + "=" + topicId;
        BaseDb.Status status;
        if (delId > 0) {
            rangeConsumeSelector += " AND " + COLUMN_NAME_DEL_ID + "<" + delId;
            status = BaseDb.Status.DELETED_SYNCED;
        } else {
            status = markAsHard ? BaseDb.Status.DELETED_HARD : BaseDb.Status.DELETED_SOFT;
        }
        rangeConsumeSelector += " AND " + COLUMN_NAME_STATUS + "=" + status.value;

        String rangeNarrow = "";
        parts.clear();
        if (fromId > 0) {
            parts.add(COLUMN_NAME_HIGH + ">=" + fromId);
        }
        parts.add(COLUMN_NAME_SEQ + "<=" + toId);
        rangeNarrow += " AND " + TextUtils.join(" AND ", parts);

        db.beginTransaction();
        try {
            // 1. Delete all messages in the range.
            db.delete(TABLE_NAME, messageSelector +
                    " AND " + COLUMN_NAME_STATUS + "<=" + BaseDb.Status.SYNCED.value, null);

            // 2. Delete all deletion records fully within the new range.
            db.delete(TABLE_NAME, rangeDeleteSelector, null);

            // Finds the maximum continuous range which overlaps with the current range.
            Cursor cursor = db.rawQuery("SELECT " +
                    "MIN(" + COLUMN_NAME_SEQ + "),MAX(" + COLUMN_NAME_HIGH + ")" +
                    " FROM " + TABLE_NAME +
                    " WHERE " + rangeConsumeSelector + rangeNarrow, null);
            if (cursor != null) {
                if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                    // Read the bounds and use them to expand the current range to overlap earlier ranges.
                    if (!cursor.isNull(0)) {
                        int min_low = cursor.getInt(0);
                        fromId = Math.min(min_low, fromId);
                    }
                    if (!cursor.isNull(1)) {
                        int max_high = cursor.getInt(1);
                        toId = Math.max(max_high, toId);
                    }
                }
                cursor.close();
            }

            // 3. Consume partially overlapped ranges. They will be replaced with the new expanded range.
            String rangeWide = "";
            parts.clear();
            if (fromId > 0) {
                parts.add(COLUMN_NAME_HIGH + ">=" + fromId);
            } else {
                fromId = 1;
            }
            parts.add(COLUMN_NAME_SEQ + "<=" + toId);
            rangeWide +=  " AND " + TextUtils.join(" AND ", parts);
            db.delete(TABLE_NAME, rangeConsumeSelector + rangeWide, null);

            // 4. Insert new range.
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, topicId);
            values.put(COLUMN_NAME_DEL_ID, delId);
            values.put(COLUMN_NAME_SEQ, fromId);
            values.put(COLUMN_NAME_HIGH, toId);
            values.put(COLUMN_NAME_STATUS, status.value);
            db.insertOrThrow(TABLE_NAME, null, values);

            db.setTransactionSuccessful();
            success = true;
        } catch (Exception ex) {
            Log.w(TAG, "Delete failed", ex);
        } finally {
            db.endTransaction();
        }
        return success;
    }

    /**
     * Delete messages in the given ranges.
     *
     * @param db            Database to use.
     * @param topicId       Tinode topic ID to delete messages from.
     * @param delId         Server-issued delete record ID. If delId <= 0, the operation is not
     *                      yet synced with the server.
     * @param ranges        array of ranges to delete.
     * @param markAsHard    mark messages as hard-deleted.
     * @return true on success, false otherwise.
     */
    private static boolean deleteOrMarkDeleted(SQLiteDatabase db, long topicId, int delId, MsgRange[] ranges,
                                               boolean markAsHard) {
        boolean success = false;
        db.beginTransaction();
        try {
            for (MsgRange r : ranges) {
                if (!deleteOrMarkDeleted(db, topicId, delId, r.getLower(), r.getUpper(), markAsHard)) {
                    throw new SQLException("error while deleting range " + r);
                }
            }
            db.setTransactionSuccessful();
            success = true;
        } catch (Exception ex) {
            Log.w(TAG, "Delete failed", ex);
        } finally {
            db.endTransaction();
        }
        return success;
    }

    /**
     * Mark sent messages as deleted without actually deleting them. Delete unsent messages.
     *
     * @param db            Database to use.
     * @param topicId       Tinode topic ID to delete messages from.
     * @param ranges        ranges of message IDs to delete.
     * @param markAsHard    mark messages as hard-deleted.
     * @return true if some messages were updated or deleted, false otherwise
     */
    static boolean markDeleted(SQLiteDatabase db, long topicId, MsgRange[] ranges, boolean markAsHard) {
        return deleteOrMarkDeleted(db, topicId, -1, ranges, markAsHard);
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
    static boolean markDeleted(SQLiteDatabase db, long topicId, int fromId, int toId, boolean markAsHard) {
        return deleteOrMarkDeleted(db, topicId, -1, fromId, toId, markAsHard);
    }

    /**
     * Delete messages between 'from' and 'to'. To delete all messages make before equal to -1.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @param fromId  minimum seq value to delete, inclusive (closed).
     * @param toId    maximum seq value to delete, exclusive (open)
     * @return true if any messages were deleted.
     */
    public static boolean delete(SQLiteDatabase db, long topicId, int delId, int fromId, int toId) {
        return deleteOrMarkDeleted(db, topicId, delId, fromId, toId, false);
    }

    /**
     * Delete messages between 'from' and 'to'. To delete all messages make from and to equal to -1.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @param ranges  message ranges to delete.
     * @return true if any messages were deleted.
     */
    public static boolean delete(SQLiteDatabase db, long topicId, int delId, MsgRange[] ranges) {
        return deleteOrMarkDeleted(db, topicId, delId, ranges, false);
    }

    /**
     * Delete all messages in a given topic, no exceptions. Use only when deleting the topic.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @return  true if any messages were deleted.
     */
    static boolean deleteAll(SQLiteDatabase db, long topicId) {
        int affected = 0;
        try {
            affected = db.delete(TABLE_NAME, COLUMN_NAME_TOPIC_ID + "=" + topicId, null);
        } catch (SQLException ex) {
            Log.w(TAG, "Delete failed", ex);
        }
        return affected > 0;
    }

    /**
     * Delete single message by database ID.
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

    private static long getId(SQLiteDatabase db, long topicId, int seq) {
        long id = -1;
        Cursor c = db.query(
                TABLE_NAME, new String[]{ _ID },
                COLUMN_NAME_TOPIC_ID + "=?" + " AND " + COLUMN_NAME_SEQ + "=?",
                new String[] { Long.toString(topicId), Integer.toString(seq) },
                null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
            c.close();
        }
        return id;
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
            if (topicId < 0) {
                Log.w(TAG, "Topic not found '" + topic + "'");
            }
        }

        @Override
        public Cursor loadInBackground() {
            return query(mDb, topicId, pageCount, pageSize);
        }
    }
}
