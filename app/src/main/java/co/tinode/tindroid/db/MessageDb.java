package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;

import androidx.loader.content.CursorLoader;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgRange;

/**
 * The table contains messages synchronized with the server and not yet synchronized.
 * It also contains message deletion markers, synchronized and not yet synchronized.
 * <p>
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

    static final int MESSAGE_PREVIEW_LENGTH = 80;
    /**
     * The name of the main table.
     */
    static final String TABLE_NAME = "messages";
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
     * If the message replaces another message, the ID of the message being replaced (from head).
     */
    private static final String COLUMN_NAME_REPLACES_SEQ ="repl_seq";
    /**
     * Timestamp of the original message this message has replaced
     * (could be the same as tc, if it does not replace anything).
     */
    private static final String COLUMN_NAME_EFFECTIVE_TS ="eff_ts";
    /**
     * If not NULL, then this message is the latest in edit history and this is the seq ID of the message it replaced.
     */
    private static final String COLUMN_NAME_EFFECTIVE_SEQ ="eff_seq";
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
                    COLUMN_NAME_REPLACES_SEQ + " INT," +
                    COLUMN_NAME_EFFECTIVE_TS + " INT," +
                    COLUMN_NAME_EFFECTIVE_SEQ + " INT," +
                    COLUMN_NAME_HEAD + " TEXT," +
                    COLUMN_NAME_CONTENT + " TEXT)";

    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_TOPIC_ID = 1;
    static final int COLUMN_IDX_USER_ID = 2;
    static final int COLUMN_IDX_STATUS = 3;
    static final int COLUMN_IDX_SENDER = 4;
    // static final int COLUMN_IDX_TS = 5;
    static final int COLUMN_IDX_SEQ = 6;
    static final int COLUMN_IDX_HIGH = 7;
    static final int COLUMN_IDX_DEL_ID = 8;
    // static final int COLUMN_IDX_REPLACES_SEQ = 9;
    static final int COLUMN_IDX_REPLACES_TS = 10;
    static final int COLUMN_IDX_EFFECTIVE_SEQ = 11;
    static final int COLUMN_IDX_HEAD = 12;
    static final int COLUMN_IDX_CONTENT = 13;
    // Used in JOIN.
    static final int COLUMN_IDX_TOPIC_NAME = 14;

    /**
     * SQL statement to drop Messages table.
     */
    static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;
    /**
     * The name of index: messages by topic and sequence.
     */
    private static final String INDEX_NAME = "message_topic_id_seq";
    private static final String INDEX_NAME_2 = "message_topic_id_eff_seq";
    /**
     * Drop the indexes too
     */
    static final String DROP_INDEX = "DROP INDEX IF EXISTS " + INDEX_NAME;
    static final String DROP_INDEX_2 = "DROP INDEX IF EXISTS " + INDEX_NAME_2;
    /**
     * Add unique index on topic-seq, in descending order
     */
    static final String CREATE_INDEX =
            "CREATE UNIQUE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_SEQ + " DESC)";

    static final String CREATE_INDEX_2 =
            "CREATE UNIQUE INDEX " + INDEX_NAME_2 +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_EFFECTIVE_SEQ + " DESC) WHERE " + COLUMN_NAME_EFFECTIVE_SEQ + " IS NOT NULL";
    /**
     * Save message to DB.
     *
     * @return ID of the newly added message
     */
    static long insert(SQLiteDatabase db, Topic topic, StoredMessage msg) {
        if (msg.id > 0) {
            // Message is already inserted.
            return msg.id;
        }

        if (msg.topicId <= 0) {
            msg.topicId = TopicDb.getId(db, msg.topic);
        }

        if (msg.topicId <= 0) {
            Log.w(TAG, "Failed to insert message (topic not found) " + msg.seq);
            return -1;
        }

        db.beginTransaction();
        try {
            int effSeq = msg.getReplacementSeqId();
            long effTs = -1;
            if (effSeq > 0) {
                // This is a replacement message. Two cases:
                // 1. The original message is already received and stored in DB. It should be replaced with this one.
                // 2. The original message is not in the DB and thus this message should not be shown to the user.
                Cursor c = getMessageBySeq(db, msg.topicId, effSeq);
                StoredMessage latestMsg = null;
                if (c.moveToFirst()) {
                    latestMsg = StoredMessage.readMessage(c, 0);
                    effTs = latestMsg.ts.getTime();
                }
                c.close();

                // Replacement message.
                if (latestMsg != null && (msg.seq == 0 || msg.seq > latestMsg.seq)) {
                    // Case 1: newer version while the original is found.
                    // Clear the effective_seq (invalidate) of all older effective message records.
                    deactivateMessageVersion(db, msg.topicId, effSeq);
                } else {
                    // Case 2: original not found. Do not set effective seq.
                    effSeq = -1;
                }
            } else {
                // This is not a replacement message. Three cases:
                // 1. This is a never edited message.
                // 2. Edited message but edits are not in the database.
                // 3. Edited and edits are in the database already.
                effTs = msg.ts != null ? msg.ts.getTime() : -1;
                effSeq = msg.seq;
                if (msg.seq > 0) {
                    // Check if there are newer versions of this message and activate the latest one.
                    if (activateMessageVersion(db, msg.topicId, msg.seq, effTs)) {
                        // If activated, then this message has been replaced by a newer one.
                        effSeq = -1;
                    }
                }
            }

            msg.id = insertRaw(db, topic, msg, effSeq, effTs);
            if (msg.id > 0) {
                db.setTransactionSuccessful();
            }
        } catch (SQLiteConstraintException ex) {
            // This may happen when concurrent {sub} requests are sent.
            Log.i(TAG, "Duplicate message topic='" + topic.getName() + "' id=" + msg.seq);
        } catch (Exception ex) {
            Log.w(TAG, "Insert failed", ex);
        } finally {
            db.endTransaction();
        }

        return msg.id;
    }

    /**
     * Save message to DB
     *
     * @return ID of the newly added message
     */
    private static long insertRaw(SQLiteDatabase db, Topic topic, StoredMessage msg, int withEffSeq, long withEffTs) {
        if (msg.userId <= 0) {
            msg.userId = UserDb.getId(db, msg.from);
        }

        if (msg.userId <= 0) {
            Log.w(TAG, "Failed to insert message (invalid user ID) " + msg.seq);
            return -1;
        }

        BaseDb.Status status;
        if (msg.seq == 0) {
            msg.seq = TopicDb.getNextUnsentSeq(db, topic);
            if (withEffSeq <= 0) {
                withEffSeq = msg.seq;
            }
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
        if (withEffTs > 0) {
            values.put(COLUMN_NAME_EFFECTIVE_TS, withEffTs);
        }
        values.put(COLUMN_NAME_SEQ, msg.seq);
        int replacesSeq = msg.getReplacementSeqId();
        if (replacesSeq > 0) {
            values.put(COLUMN_NAME_REPLACES_SEQ, replacesSeq);
        }
        if (withEffSeq > 0) {
            values.put(COLUMN_NAME_EFFECTIVE_SEQ, withEffSeq);
        }
        values.put(COLUMN_NAME_HEAD, BaseDb.serialize(msg.head));
        values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(msg.content));

        return db.insertOrThrow(TABLE_NAME, null, values);
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

    static void delivered(SQLiteDatabase db, long msgId, Date timestamp, int seq) {
        String sql = "UPDATE " + TABLE_NAME + " SET " +
                COLUMN_NAME_STATUS + "=" + BaseDb.Status.SYNCED.value + "," +
                COLUMN_NAME_TS + "=" + timestamp.getTime() + "," +
                COLUMN_NAME_SEQ + "=" + seq + "," +
                COLUMN_NAME_EFFECTIVE_TS +
                    "=CASE WHEN " + COLUMN_NAME_EFFECTIVE_TS + " IS NULL THEN " +
                        timestamp.getTime() + " ELSE " + COLUMN_NAME_EFFECTIVE_TS + " END," +
                COLUMN_NAME_EFFECTIVE_SEQ +
                    "=CASE WHEN " + COLUMN_NAME_REPLACES_SEQ + " IS NOT NULL THEN "
                        + COLUMN_NAME_REPLACES_SEQ + " ELSE " + seq + " END " +
                "WHERE " + _ID + "=" + msgId;
        db.execSQL(sql);
    }

    // Clear COLUMN_NAME_EFFECTIVE_SEQ to remove message from display.
    private static void deactivateMessageVersion(SQLiteDatabase db, long topicId, int effSeq) {
        ContentValues values = new ContentValues();
        values.putNull(COLUMN_NAME_EFFECTIVE_SEQ);
        db.update(TABLE_NAME, values,
                COLUMN_NAME_TOPIC_ID + "=" + topicId + " AND " +
                        COLUMN_NAME_EFFECTIVE_SEQ + "=" + effSeq,
                null);
    }

    // Find the newest version of a message and make it visible
    // by setting COLUMN_NAME_EFFECTIVE_SEQ to the given seq value.
    private static boolean activateMessageVersion(SQLiteDatabase db, long topicId, int seqId, long effTs) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_EFFECTIVE_SEQ, seqId);
        values.put(COLUMN_NAME_EFFECTIVE_TS, effTs);
        return db.update(TABLE_NAME, values,
                // Select the newest message with the given COLUMN_NAME_REPLACES_SEQ.
                _ID + "=" +
                        "(SELECT " + _ID + " FROM " + TABLE_NAME +
                            " WHERE " + COLUMN_NAME_REPLACES_SEQ + "=" + seqId + " AND " +
                                COLUMN_NAME_TOPIC_ID + "=" + topicId +
                            " ORDER BY " + COLUMN_NAME_SEQ + " DESC LIMIT 1)",
                null) > 0;
    }

    // Find all version of an edited message (if any). The versions are sorted from newest to oldest.
    // Does not return the original message id (seq).
    public static int[] getAllVersions(SQLiteDatabase db, long topicId, int seq, int limit) {
        Cursor cursor = db.rawQuery("SELECT " + COLUMN_NAME_SEQ + " FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId + " AND " +
                COLUMN_NAME_REPLACES_SEQ + "=" + seq +
                " ORDER BY " + COLUMN_NAME_SEQ + " DESC" +
                (limit > 0 ? " LIMIT " + limit : ""), null);
        ArrayList<Integer> ids = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                int repl = cursor.getInt(0);
                ids.add(repl);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return ids.stream().mapToInt(i->i).toArray();
    }

    /**
     * Query latest messages
     * Returned cursor must be closed after use.
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
                " AND "
                + COLUMN_NAME_EFFECTIVE_SEQ + " IS NOT NULL" +
                " ORDER BY "
                + COLUMN_NAME_EFFECTIVE_SEQ + " DESC" +
                " LIMIT " + (pageCount * pageSize);

        return db.rawQuery(sql, null);
    }

    /**
     * Load a single message by database ID.
     * Cursor must be closed after use.
     *
     * @param db    database to select from;
     * @param msgId _id of the message to retrieve.
     * @return cursor with the message (close after use!).
     */
    static Cursor getMessageById(SQLiteDatabase db, long msgId) {
        return db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE _id=" + msgId, null);
    }

    /**
     * Load a single message by topic and seq IDs.
     * Cursor must be closed after use.
     *
     * @param db    database to select from;
     * @param topicId _id of the topic which owns the message.
     * @param effSeq effective seq ID of the message to get.
     * @return cursor with the message (close after use!).
     */
    static Cursor getMessageBySeq(SQLiteDatabase db, long topicId, int effSeq) {
        return db.rawQuery("SELECT * FROM " + TABLE_NAME +
                    " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId + " AND " +
                    COLUMN_NAME_EFFECTIVE_SEQ + "=" + effSeq, null);
    }
    /**
     * Get a list of the latest message for every topic, sent or received.
     * See explanation here: https://stackoverflow.com/a/2111420
     */
    static Cursor getLatestMessages(SQLiteDatabase db) {
        final String sql = "SELECT m1.*, t." + TopicDb.COLUMN_NAME_TOPIC + " AS topic" +
                " FROM " + TABLE_NAME + " AS m1" +
                " LEFT JOIN " + TABLE_NAME + " AS m2" +
                    " ON (m1." + COLUMN_NAME_TOPIC_ID + "=m2." + COLUMN_NAME_TOPIC_ID +
                        " AND m1." + COLUMN_NAME_EFFECTIVE_SEQ + "<m2." + COLUMN_NAME_EFFECTIVE_SEQ + ")" +
                " LEFT JOIN " + TopicDb.TABLE_NAME + " AS t" +
                    " ON m1." + COLUMN_NAME_TOPIC_ID + "=t." + TopicDb._ID +
                " WHERE m1." + COLUMN_NAME_DEL_ID + " IS NULL" +
                    " AND m2." + COLUMN_NAME_DEL_ID + " IS NULL" +
                    " AND m2." + _ID + " IS NULL" +
                    " AND m1." + COLUMN_NAME_EFFECTIVE_SEQ + " IS NOT NULL";

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
     * @param db      database to select from;
     * @param topicId Tinode topic ID (topics._id) to select from;
     * @return range of missing IDs if found, null if either all messages are present or no messages are found.
     */
    static MsgRange getNextMissingRange(SQLiteDatabase db, long topicId) {
        int high = 0;
        // Find the greatest seq present in the DB.
        final String sqlHigh = "SELECT MAX(m1." + COLUMN_NAME_SEQ + ") AS highest" +
                " FROM " + TABLE_NAME + " AS m1" +
                " LEFT JOIN " + TABLE_NAME + " AS m2" +
                " ON m1." + COLUMN_NAME_SEQ + "=IFNULL(m2." + COLUMN_NAME_HIGH + ", m2." + COLUMN_NAME_SEQ + "+1)" +
                " AND m1." + COLUMN_NAME_TOPIC_ID + "= m2." + COLUMN_NAME_TOPIC_ID +
                " WHERE m2." + COLUMN_NAME_SEQ + " IS NULL" +
                " AND m1." + COLUMN_NAME_SEQ + ">1" +
                " AND m1." + COLUMN_NAME_TOPIC_ID + "=" + topicId;

        Cursor c = db.rawQuery(sqlHigh, null);
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
                " WHERE " + COLUMN_NAME_SEQ + "<" + high +
                " AND " + COLUMN_NAME_TOPIC_ID + "=" + topicId;
        int low = 1;
        c = db.rawQuery(sqlLow, null);
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
     * @param db         Database to use.
     * @param topicId    Tinode topic ID to delete messages from.
     * @param delId      Server-issued delete record ID. If delId <= 0, the operation is not
     *                   yet synced with the server.
     * @param fromId     minimum seq value to delete, inclusive (closed).
     * @param toId       maximum seq value to delete, exclusive (open).
     * @param markAsHard mark messages as hard-deleted.
     * @return true if some messages were updated or deleted, false otherwise
     */
    private static boolean deleteOrMarkDeleted(SQLiteDatabase db, long topicId, int delId, int fromId, int toId,
                                               boolean markAsHard) {
        // 1. Delete all messages within the given range (sent, unsent, failed).
        // 2. Delete all unsynchronized (soft and hard) deletion ranges fully within this range
        // (no point in synchronizing them, they are superseded).
        // 3.1 If server record, consume older partially overlapping server records.
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

        // Selector of effective message versions which are fully within the range.
        parts.clear();
        String effectiveSeqSelector = COLUMN_NAME_TOPIC_ID + "=" + topicId;
        if (fromId > 0) {
            parts.add(COLUMN_NAME_EFFECTIVE_SEQ + ">=" + fromId);
        }
        parts.add(COLUMN_NAME_EFFECTIVE_SEQ + "<" + toId);
        effectiveSeqSelector += " AND " + TextUtils.join(" AND ", parts);

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
            db.delete(TABLE_NAME, messageSelector, null);

            // 2. Delete all deletion records fully within the new range.
            db.delete(TABLE_NAME, rangeDeleteSelector, null);

            // 3. Delete edited records fully  within the range.
            db.delete(TABLE_NAME, effectiveSeqSelector, null);

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
            rangeWide += " AND " + TextUtils.join(" AND ", parts);
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
     * @param db         Database to use.
     * @param topicId    Tinode topic ID to delete messages from.
     * @param delId      Server-issued delete record ID. If delId <= 0, the operation is not
     *                   yet synced with the server.
     * @param ranges     array of ranges to delete.
     * @param markAsHard mark messages as hard-deleted.
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
     * @param db         Database to use.
     * @param topicId    Tinode topic ID to delete messages from.
     * @param ranges     ranges of message IDs to delete.
     * @param markAsHard mark messages as hard-deleted.
     * @return true if some messages were updated or deleted, false otherwise
     */
    static boolean markDeleted(SQLiteDatabase db, long topicId, MsgRange[] ranges, boolean markAsHard) {
        return deleteOrMarkDeleted(db, topicId, -1, ranges, markAsHard);
    }

    /**
     * Mark sent messages as deleted without actually deleting them. Delete unsent messages.
     *
     * @param db         Database to use.
     * @param topicId    Tinode topic ID to delete messages from.
     * @param fromId     minimum seq value to delete, inclusive (closed).
     * @param toId       maximum seq value to delete, exclusive (open).
     * @param markAsHard mark messages as hard-deleted.
     * @return true if some messages were updated or deleted, false otherwise
     */
    static boolean markDeleted(SQLiteDatabase db, long topicId, int fromId, int toId, boolean markAsHard) {
        return deleteOrMarkDeleted(db, topicId, -1, fromId, toId, markAsHard);
    }

    /**
     * Delete messages between 'from' and 'to'. To delete all messages make 'before' equal to -1.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @param fromId  minimum seq value to delete, inclusive (closed).
     * @param toId    maximum seq value to delete, exclusive (open)
     * @return true if any messages were deleted.
     */
    static boolean delete(SQLiteDatabase db, long topicId, int delId, int fromId, int toId) {
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
    static boolean delete(SQLiteDatabase db, long topicId, int delId, MsgRange[] ranges) {
        return deleteOrMarkDeleted(db, topicId, delId, ranges, false);
    }

    /**
     * Delete single message by database ID.
     *
     * @param db    Database to use.
     * @param msgId Database ID of the message (_id).
     * @return true on success, false on failure
     */
    static boolean delete(SQLiteDatabase db, long msgId) {
        return db.delete(TABLE_NAME, _ID + "=" + msgId, null) > 0;
    }

    /**
     * Delete single message by topic ID and seq.
     *
     * @param db    Database to use.
     * @param topicId Database ID of the topic with the message.
     * @param seq Seq ID of the message to delete.
     * @return true on success, false on failure.
     */
    static boolean delete(SQLiteDatabase db, long topicId, int seq) {
        return db.delete(TABLE_NAME, COLUMN_NAME_TOPIC_ID + "=" + topicId +
                " AND " + COLUMN_NAME_SEQ + "=" + seq, null) > 0;
    }

    /**
     * Delete all messages in a given topic, no exceptions. Use only when deleting the topic.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     */
    static void deleteAll(SQLiteDatabase db, long topicId) {
        try {
            db.delete(TABLE_NAME, COLUMN_NAME_TOPIC_ID + "=" + topicId, null);
        } catch (SQLException ex) {
            Log.w(TAG, "Delete failed", ex);
        }
    }

    /**
     * Deletes all records from 'messages' table.
     *
     * @param db Database to use.
     */
    static void truncateTable(SQLiteDatabase db) {
        try {
            // 'DELETE FROM table' in SQLite is equivalent to truncation.
            db.delete(TABLE_NAME, null, null);
        } catch (SQLException ex) {
            Log.w(TAG, "Delete failed", ex);
        }
    }

    /**
     * Delete failed messages in a given topic.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @return true if any messages were deleted.
     */
    static boolean deleteFailed(SQLiteDatabase db, long topicId) {
        int affected = 0;
        try {
            affected = db.delete(TABLE_NAME, COLUMN_NAME_TOPIC_ID + "=" + topicId +
                    " AND " + COLUMN_NAME_STATUS + "=" + BaseDb.Status.FAILED.value, null);
        } catch (SQLException ex) {
            Log.w(TAG, "Delete failed", ex);
        }
        return affected > 0;
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

    /**
     * Message Loader for loading messages in background.
     */
    public static class Loader extends CursorLoader {
        private final long topicId;
        private final int pageCount;
        private final int pageSize;
        final SQLiteDatabase mDb;

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
