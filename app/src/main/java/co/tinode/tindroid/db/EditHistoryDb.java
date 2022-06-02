package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;

/**
 * Message edit history.
 */
public class EditHistoryDb implements BaseColumns {
    private static final String TAG = "EditHistoryDb";

    static final String TABLE_NAME = "edit_history";

    /**
     * Topic ID, references topics._ID
     */
    static final String COLUMN_NAME_TOPIC_ID = "topic_id";
    // Timestamp when the record was created.
    static final String COLUMN_NAME_WHEN = "replaced_when";
    // Original seq value: if one message was edited several times, possibly recursively,
    // this is the seq ID of the very first message.
    static final String COLUMN_NAME_ORIG_SEQ = "orig_seq";
    // The seq of the old message.
    static final String COLUMN_NAME_OLD_SEQ = "old_seq";
    // The seq of the new message.
    static final String COLUMN_NAME_NEW_SEQ = "new_seq";
    // Old headers.
    static final String COLUMN_NAME_HEAD = "head";
    // Old content.
    static final String COLUMN_NAME_CONTENT = "content";

    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_TOPIC_ID = 1;
    static final int COLUMN_IDX_WHEN = 2;
    static final int COLUMN_IDX_ORIG_SEQ = 3;
    static final int COLUMN_IDX_OLD_SEQ = 4;
    static final int COLUMN_IDX_NEW_SEQ = 5;

    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_TOPIC_ID
                    + " REFERENCES " + TopicDb.TABLE_NAME + "(" + TopicDb._ID + ")," +
                    COLUMN_NAME_WHEN + " INT," +
                    COLUMN_NAME_ORIG_SEQ + " INT," +
                    COLUMN_NAME_OLD_SEQ + " INT," +
                    COLUMN_NAME_NEW_SEQ + " INT," +
                    COLUMN_NAME_HEAD + " TEXT," +
                    COLUMN_NAME_CONTENT + " TEXT)";
    /**
     * SQL statement to drop Edit History table.
     */
    static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;
    /**
     * The name of index: messages by topic and sequence.
     */
    private static final String INDEX_NAME = "edit_history_topic_id_orig_seq";
    /**
     * Drop the index too
     */
    static final String DROP_INDEX = "DROP INDEX IF EXISTS " + INDEX_NAME;
    /**
     * Add index on topic - original seq.
     */
    static final String CREATE_INDEX =
            "CREATE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_ORIG_SEQ + ")";

    /**
     * The name of index: messages by topic and sequence.
     */
    private static final String INDEX_NAME_2 = "edit_history_topic_id_new_seq";
    /**
     * Drop the index too
     */
    static final String DROP_INDEX_2 = "DROP INDEX IF EXISTS " + INDEX_NAME_2;
    /**
     * Add index on topic - original seq.
     */
    static final String CREATE_INDEX_2 =
            "CREATE INDEX " + INDEX_NAME_2 +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_NEW_SEQ + ")";

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
     * Save headers and content of a message which has been replaced.
     * The message could be null. In such a case create a placeholder to be filled later.
     *
     * @param db        Database to use.
     * @param topicId   Topic ID which owns the messages.
     * @param oldMsg    The replaced message to save to history log (could be null).
     * @param newSeq    The seq ID of the new message which replaced the old one being saved.
     * @param ts        Timestamp of the new message.
     */
    static void upsert(SQLiteDatabase db, long topicId, StoredMessage oldMsg, int oldSeq, int newSeq, Date ts) {
        // Check if message placeholder already exists in history.
        HistoryRec rec = getRecord(db, topicId, oldSeq);
        if (rec != null) {
            // Record exists: update it with the values from the message.
            // oldMsg must not be null in this case.
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_ORIG_SEQ, oldMsg.seq);
            values.put(COLUMN_NAME_HEAD, BaseDb.serialize(oldMsg.head));
            values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(oldMsg.content));
            db.update(TABLE_NAME, values, _ID + "=" + rec.id, null);
        } else {
            // No record fund, create.
            // oldMsg could be NULL.
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, topicId);
            values.put(COLUMN_NAME_WHEN, ts != null ? ts.getTime() : null);
            values.put(COLUMN_NAME_ORIG_SEQ, oldSeq);
            values.put(COLUMN_NAME_OLD_SEQ, oldSeq);
            values.put(COLUMN_NAME_NEW_SEQ, newSeq);
            if (oldMsg != null) {
                values.put(COLUMN_NAME_HEAD, BaseDb.serialize(oldMsg.head));
                values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(oldMsg.content));
            }

            db.insertOrThrow(TABLE_NAME, null, values);
            rec = new HistoryRec();
            rec.oldSeq = oldSeq;
        }

        // The original ID may have to change now: we may have received an older message.
        if (oldMsg != null) {
            int origId = oldMsg.getReplacementSeqId();
            if (origId > 0 && origId < rec.oldSeq) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_NAME_ORIG_SEQ, origId);
                db.update(TABLE_NAME, values,
                        COLUMN_NAME_TOPIC_ID + "=" + topicId +
                                " AND " +
                                COLUMN_NAME_ORIG_SEQ + "=" + rec.oldSeq, null);
            }
        }
    }

    private static HistoryRec getRecord(SQLiteDatabase db, long topicId, int oldSeq) {
        HistoryRec rec = null;
        Cursor c = db.rawQuery(
                "SELECT " +
                        _ID + "," +
                        COLUMN_NAME_TOPIC_ID + "," +
                        COLUMN_NAME_WHEN + "," +
                        COLUMN_NAME_ORIG_SEQ + "," +
                        COLUMN_NAME_OLD_SEQ + "," +
                        COLUMN_NAME_NEW_SEQ +
                        " FROM " + TABLE_NAME +
                        " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId +
                        " AND " + COLUMN_NAME_OLD_SEQ + "=" + oldSeq, null);
        if (c != null) {
            if (c.moveToFirst()) {
                rec = new HistoryRec();
                rec.id = c.getLong(COLUMN_IDX_ID);
                rec.topicId = c.getLong(COLUMN_IDX_TOPIC_ID);
                rec.when = new Date(c.getLong(COLUMN_IDX_WHEN));
                rec.origSeq = c.getInt(COLUMN_IDX_ORIG_SEQ);
                rec.oldSeq = c.getInt(COLUMN_IDX_OLD_SEQ);
                rec.newSeq = c.getInt(COLUMN_IDX_NEW_SEQ);
            }
            c.close();
        }
        return rec;
    }

    private static class HistoryRec {
        long id;
        long topicId;
        Date when;
        int origSeq;
        int oldSeq;
        int newSeq;
    }
}
