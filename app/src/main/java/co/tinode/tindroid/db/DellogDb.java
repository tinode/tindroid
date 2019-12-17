package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgRange;

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
                    COLUMN_NAME_HIGH + ")";

    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_TOPIC_ID = 1;
    static final int COLUMN_IDX_DEL_ID = 2;
    static final int COLUMN_IDX_LOW = 3;
    static final int COLUMN_IDX_HIGH = 4;

    /**
     * Save multiple deleted range records to DB.
     *
     * @return number of successfully inserted ranges.
     */
    static int insert(SQLiteDatabase db, Topic topic, int delId, MsgRange[] ranges) {
        int count = 0;
        db.beginTransaction();
        try {
            long topic_id = StoredTopic.getId(topic);

            if (topic_id <= 0) {
                Log.w(TAG, "Failed to insert deletion log " + delId);
                return -1;
            }

            for (MsgRange r : ranges) {
                if (insert(db, topic, delId, r.low, r.hi != null && r.hi != 0 ? r.hi : r.low) > 0) {
                    count++;
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception ex) {
            Log.w(TAG, "Insert failed", ex);
            count = -1;
        } finally {
            db.endTransaction();
        }

        return count;
    }

    /**
     * Insert a new range possibly collapsing existing ranges.
     *
     * @param db writable database reference to use
     * @param topic topic being modified
     * @param delId ID of the delete record being inserted
     * @param low low seq value in the range
     * @param high high seq value in the range
     * @return database id of the inserted record or -1
     */
    static long insert(SQLiteDatabase db, Topic topic, int delId, int low, int high) {
        long id = -1;
        long topic_id = StoredTopic.getId(topic);
        if (topic_id <= 0) {
            return id;
        }

        // Condition which selects earlier ranges fully or partially overlapped by the current range.
        final String rangeSelector = COLUMN_NAME_TOPIC_ID + "=" + topic_id +
                " AND " + COLUMN_NAME_DEL_ID + "<" + delId +
                " AND " + COLUMN_NAME_LOW + "<=" + high +
                " AND " + COLUMN_NAME_HIGH + ">=" + low;

        db.beginTransaction();
        try {
            // Find bounds of existing ranges which overlap with the new range.
            Cursor cursor = db.rawQuery("SELECT " +
                            "MIN(" + COLUMN_NAME_LOW + "),MAX(" + COLUMN_NAME_HIGH + ")" +
                    " FROM " + TABLE_NAME +
                    " WHERE " + rangeSelector, null);
            if (cursor != null) {
                // Cursor coulnd be empty if nothing overlaps.
                if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                    // Read the bounds.
                    int min_low = cursor.getInt(0);
                    int max_high = cursor.getInt(1);

                    // Expand current range to overlap earlier ranges.
                    low = min_low < low ? min_low : low;
                    high = max_high > high ? max_high : high;

                    // Delete ranges which are being replaced by the new range.
                    db.delete(TABLE_NAME, rangeSelector, null);
                }
                cursor.close();
            }

            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, topic_id);
            values.put(COLUMN_NAME_DEL_ID, delId);
            values.put(COLUMN_NAME_LOW, low);
            values.put(COLUMN_NAME_HIGH, high);

            id = db.insertOrThrow(TABLE_NAME, null, values);

            db.setTransactionSuccessful();
        } catch (Exception ex) {
            Log.w(TAG, "Failed to collapse range", ex);
            id = -1;
        } finally {
            db.endTransaction();
        }

        return id;
    }
}
