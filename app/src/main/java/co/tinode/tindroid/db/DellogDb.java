package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import co.tinode.tinodesdk.Topic;

/**
 * Log of deletions
 */
public class DellogDb implements BaseColumns {
    private static final String TAG = "DellogDb";

    /**
     * The name of the main table.
     */
    private static final String TABLE_NAME = "dellog";

    /**
     * Topic ID, references topics._ID
     */
    private static final String COLUMN_NAME_TOPIC_ID = "topic_id";
    /**
     * Server-issued ID of the record that deleted the range (not unique).
     */
    private static final String COLUMN_NAME_DEL_ID = "del_id";
    /**
     * Low seq ID value in the range.
     */
    private static final String COLUMN_NAME_LOW = "low";
    /**
     * High seq-id value in the range
     */
    private static final String COLUMN_NAME_HIGH = "high";


    /**
     * SQL statement to create Messages table
     */
    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_TOPIC_ID
                    + " REFERENCES " + TopicDb.TABLE_NAME + "(" + TopicDb._ID + ")," +
                    COLUMN_NAME_DEL_ID + " INT," +
                    COLUMN_NAME_LOW + " INT," +
                    COLUMN_NAME_HIGH + " INT)";
    /**
     * SQL statement to drop Messages table.
     */
    static final String DROP_TABLE =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    /**
     * The name of index: messages by topic and sequence.
     */
    private static final String INDEX_NAME = "dellog_topic_id_low_high";
    /**
     * Drop the index too
     */
    static final String DROP_INDEX =
            "DROP INDEX IF EXISTS " + INDEX_NAME;
    /**
     * Add index on topic-id, low, high
     */
    static final String CREATE_INDEX =
            "CREATE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_LOW + "," +
                    COLUMN_NAME_HIGH ;

    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_TOPIC_ID = 1;
    static final int COLUMN_IDX_DEL_ID = 2;
    static final int COLUMN_IDX_LOW = 3;
    static final int COLUMN_IDX_HIGH = 4;

    /**
     * Save message to DB
     *
     * @return ID of the newly added message
     */
    static long insert(SQLiteDatabase db, Topic topic, int del_id, int low, int high) {
        long id = -1;
        db.beginTransaction();
        try {
            long topic_id = StoredTopic.getId(topic);

            if (topic_id <= 0) {
                Log.w(TAG, "Failed to insert deletion log " + del_id);
                return -1;
            }

            // Convert message to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, topic_id);
            values.put(COLUMN_NAME_DEL_ID, del_id);
            values.put(COLUMN_NAME_LOW, low);
            values.put(COLUMN_NAME_HIGH, high);

            id = db.insertOrThrow(TABLE_NAME, null, values);
            db.setTransactionSuccessful();
        } catch (Exception ex) {
            Log.w(TAG, "Insert failed", ex);
        } finally {
            db.endTransaction();
        }

        return id;
    }

}
