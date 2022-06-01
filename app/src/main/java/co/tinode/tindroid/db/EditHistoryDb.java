package co.tinode.tindroid.db;

import android.content.ContentValues;
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
    // The seq of the old message.
    static final String COLUMN_NAME_OLD_SEQ = "old_seq";
    // The seq of the new message.
    static final String COLUMN_NAME_NEW_SEQ = "new_seq";
    // Old headers.
    static final String COLUMN_NAME_HEAD = "head";
    // Old content.
    static final String COLUMN_NAME_CONTENT = "content";

    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_TOPIC_ID
                    + " REFERENCES " + TopicDb.TABLE_NAME + "(" + TopicDb._ID + ")," +
                    COLUMN_NAME_WHEN + " INT," +
                    COLUMN_NAME_OLD_SEQ + " INT," +
                    COLUMN_NAME_NEW_SEQ + " INT," +
                    COLUMN_NAME_HEAD + " TEXT," +
                    COLUMN_NAME_CONTENT + " TEXT)";
    /**
     * SQL statement to drop Edit History table.
     */
    static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

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
     * Save headers and content of a message into history.
     *
     * @param db      Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     */
    static void insert(SQLiteDatabase db, long topicId, StoredMessage oldMsg, int newSeq, Date ts) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_TOPIC_ID, topicId);
        values.put(COLUMN_NAME_WHEN, ts != null ? ts.getTime() : null);
        values.put(COLUMN_NAME_OLD_SEQ, newSeq);
        values.put(COLUMN_NAME_NEW_SEQ, oldMsg.seq);
        values.put(COLUMN_NAME_HEAD, BaseDb.serialize(oldMsg.head));
        values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(oldMsg.content));
        db.insertOrThrow(TABLE_NAME, null, values);
    }
}
