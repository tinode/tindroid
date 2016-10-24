package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;

import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgServerMeta;

/**
 * Store for topics
 */
public class TopicDb implements BaseColumns {
    private static final String TAG = "TopicsDb";

    /**
     * The name of the main table.
     */
    public static final String TABLE_NAME = "topics";
    /**
     * The name of index: topic by account id and topic name.
     */
    public static final String INDEX_NAME = "topic_account_name";
    /**
     * Account ID, references accounts._ID
     */
    public static final String COLUMN_NAME_ACCOUNT_ID = "account_id";
    /**
     * Topic name, indexed
     */
    public static final String COLUMN_NAME_TOPIC = "name";
    /**
     * Topic type
     */
    public static final String COLUMN_NAME_TYPE = "type";
    /**
     * When the topic was created
     */
    public static final String COLUMN_NAME_CREATED = "created";
    /**
     * When the topic was created
     */
    public static final String COLUMN_NAME_UPDATED = "updated";
    /**
     * When the topic was created
     */
    public static final String COLUMN_NAME_DELETED = "deleted";
    /**
     * Sequence ID marked as read, integer
     */
    public static final String COLUMN_NAME_READ = "read";
    /**
     * Sequence ID marked as received, integer
     */
    public static final String COLUMN_NAME_RECV = "recv";
    /**
     * Server-issued sequence ID, integer, indexed
     */
    public static final String COLUMN_NAME_SEQ = "seq";
    /**
     * Access mode, string
     */
    public static final String COLUMN_NAME_ACCESSMODE = "mode";
    /**
     * Timestamp of the last message
     */
    public static final String COLUMN_NAME_LASTUSED = "last_used";
    /**
     * Public topic description, blob
     */
    public static final String COLUMN_NAME_PUBLIC = "pub";
    /**
     * Private topic description, blob
     */
    public static final String COLUMN_NAME_PRIVATE = "priv";


    private static final int COLUMN_IDX_ID = 0;
    private static final int COLUMN_IDX_ACCOUNT_ID = 1;
    private static final int COLUMN_IDX_NAME = 2;
    private static final int COLUMN_IDX_TYPE = 3;
    private static final int COLUMN_IDX_CREATED = 4;
    private static final int COLUMN_IDX_UPDATED = 5;
    private static final int COLUMN_IDX_DELETED = 6;
    private static final int COLUMN_IDX_READ = 7;
    private static final int COLUMN_IDX_RECV = 8;
    private static final int COLUMN_IDX_SEQ = 9;
    private static final int COLUMN_IDX_ACCESSMODE = 10;
    private static final int COLUMN_IDX_LASTUSED = 11;
    private static final int COLUMN_IDX_PUBLIC = 12;
    private static final int COLUMN_IDX_PRIVATE = 13;

    /**
     * SQL statement to create Messages table
     */
    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_ACCOUNT_ID
                    + " REFERENCES " + AccountDb.TABLE_NAME + "(" + AccountDb._ID + ")," +
                    COLUMN_NAME_TOPIC + " TEXT," +
                    COLUMN_NAME_TYPE + " INT," +
                    COLUMN_NAME_CREATED + " INT," +
                    COLUMN_NAME_UPDATED + " INT," +
                    COLUMN_NAME_DELETED + " INT," +
                    COLUMN_NAME_READ + " INT," +
                    COLUMN_NAME_RECV + " INT," +
                    COLUMN_NAME_SEQ + " INT," +
                    COLUMN_NAME_ACCESSMODE + " TEXT," +
                    COLUMN_NAME_LASTUSED + " INT," +
                    COLUMN_NAME_PUBLIC + " BLOB," +
                    COLUMN_NAME_PRIVATE + " BLOB)";
    /**
     * Add index on account_id-topic name, in descending order
     */
    static final String CREATE_INDEX =
            "CREATE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_ACCOUNT_ID + ")";

    /**
     * SQL statement to drop the table.
     */
    static final String DROP_TABLE =
            "DROP TABLE IF EXISTS " + TABLE_NAME;
    /**
     * Drop the index too
     */
    static final String DROP_INDEX =
            "DROP INDEX IF EXISTS " + INDEX_NAME;

    /**
     * Save message to DB
     *
     * @return ID of the newly added message
     */
    public static long insert(SQLiteDatabase db, String name, MsgServerMeta topic) {
        // Convert topic description to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_ACCOUNT_ID, BaseDb.getAccountId());
        values.put(COLUMN_NAME_TOPIC, name);
        values.put(COLUMN_NAME_TYPE, Topic.getTopicTypeByName(name).val());
        if (topic.desc != null) {
            values.put(COLUMN_NAME_CREATED, topic.desc.created.getTime());
            values.put(COLUMN_NAME_UPDATED, topic.desc.updated.getTime());
            // values.put(COLUMN_NAME_DELETED, NULL);
            values.put(COLUMN_NAME_READ, topic.desc.read);
            values.put(COLUMN_NAME_RECV, topic.desc.recv);
            values.put(COLUMN_NAME_SEQ, topic.desc.seq);
            values.put(COLUMN_NAME_ACCESSMODE, topic.desc.acs.mode);
            values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(topic.desc.pub));
            values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(topic.desc.priv));
        }
        values.put(COLUMN_NAME_LASTUSED, new Date().getTime());
        return db.insert(TABLE_NAME, null, values);
    }

    /**
     * Query topics.
     *
     * @param db database to select from;
     * @return cursor with topics
     */
    public static Cursor query(SQLiteDatabase db) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() +
                " ORDER BY " + COLUMN_NAME_LASTUSED + " DESC";
        Log.d(TAG, "Sql=[" + sql + "]");

        return db.rawQuery(sql, null);
    }
}
