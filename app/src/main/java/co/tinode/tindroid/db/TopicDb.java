package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;

import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.Subscription;

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
     * For P2P topics UID of the other party
     */
    public static final String COLUMN_NAME_WITH = "with_uid";
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
    private static final int COLUMN_IDX_TOPIC = 2;
    private static final int COLUMN_IDX_TYPE = 3;
    private static final int COLUMN_IDX_WITH = 4;
    private static final int COLUMN_IDX_CREATED = 5;
    private static final int COLUMN_IDX_UPDATED = 6;
    private static final int COLUMN_IDX_DELETED = 7;
    private static final int COLUMN_IDX_READ = 8;
    private static final int COLUMN_IDX_RECV = 9;
    private static final int COLUMN_IDX_SEQ = 10;
    private static final int COLUMN_IDX_ACCESSMODE = 11;
    private static final int COLUMN_IDX_LASTUSED = 12;
    private static final int COLUMN_IDX_PUBLIC = 13;
    private static final int COLUMN_IDX_PRIVATE = 14;

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
                    COLUMN_NAME_WITH + " TEXT," +
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
            "CREATE UNIQUE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_ACCOUNT_ID + "," + COLUMN_NAME_TOPIC + ")";

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
     * Save topic description to DB
     *
     * @return ID of the newly added message
     */
    public static long insert(SQLiteDatabase db, Subscription sub) {
        Log.d(TAG, "Inserting sub for " + sub.topic);

        // Convert topic description to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_ACCOUNT_ID, BaseDb.getAccountId());
        values.put(COLUMN_NAME_TOPIC, sub.topic);
        Topic.TopicType tp = Topic.getTopicTypeByName(sub.topic);
        values.put(COLUMN_NAME_TYPE, tp.val());
        if (tp == Topic.TopicType.P2P) {
            values.put(COLUMN_NAME_WITH, sub.with);
        }
        // values.put(COLUMN_NAME_CREATED, NULL);
        values.put(COLUMN_NAME_UPDATED, sub.updated.getTime());
        // values.put(COLUMN_NAME_DELETED, NULL);
        values.put(COLUMN_NAME_READ, sub.read);
        values.put(COLUMN_NAME_RECV, sub.recv);
        values.put(COLUMN_NAME_SEQ, sub.seq);
        values.put(COLUMN_NAME_ACCESSMODE, sub.mode);
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(sub.pub));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(sub.priv));

        values.put(COLUMN_NAME_LASTUSED, new Date().getTime());
        return db.insert(TABLE_NAME, null, values);
    }

    /**
     * Update topic description
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, Subscription sub) {

        // Convert topic description to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_UPDATED, sub.updated.getTime());
        // values.put(COLUMN_NAME_DELETED, NULL);
        values.put(COLUMN_NAME_READ, sub.read);
        values.put(COLUMN_NAME_RECV, sub.recv);
        values.put(COLUMN_NAME_SEQ, sub.seq);
        values.put(COLUMN_NAME_ACCESSMODE, sub.mode);
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(sub.pub));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(sub.priv));

        values.put(COLUMN_NAME_LASTUSED, new Date().getTime());
        int updated = db.update(TABLE_NAME, values,
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() +
                        " AND " + COLUMN_NAME_TOPIC + "='" + sub.topic + "'",
                null);
                //COLUMN_NAME_ACCOUNT_ID + "= ? AND " + COLUMN_NAME_TOPIC + "= ?",
                //new String[] { String.valueOf(BaseDb.getAccountId()), "'" + name + "'" });

        Log.d(TAG, "Update row, accid=" + BaseDb.getAccountId() + " name=" + sub.topic + " returned " + updated);

        return updated > 0;
    }

    /**
     * Save or update a topic
     *
     * @return Id of the newly inserted topic or 0 if the topic was updated
     */
    public static long upsert(SQLiteDatabase db, Subscription sub) {
        if (!update(db, sub)) {
            return insert(db, sub);
        }
        return 0;
    }

    /**
     * Query topics.
     *
     * @param db database to select from;
     * @return cursor with topics
     */
    public static Cursor query(SQLiteDatabase db) {
        Log.d(TAG, "Querying");

        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() +
                " ORDER BY " + COLUMN_NAME_LASTUSED + " DESC";

        Log.d(TAG, sql);
        return db.rawQuery(sql, null);
    }

    /**
     * Read Subscription at the current cursor position
     *
     * @param c Cursor to read from
     * @return Subscription
     */
    public static <Pu,Pr> StoredTopic<Pu,Pr> readOne(Cursor c) {
        StoredTopic<Pu,Pr> sub = new StoredTopic<>();

        sub.id = c.getLong(COLUMN_IDX_ID);
        sub.name = c.getString(COLUMN_IDX_TOPIC);
        sub.type = Topic.getTopicTypeByName(sub.name);
        sub.with = c.getString(COLUMN_IDX_WITH);
        sub.updated = new Date(c.getLong(COLUMN_IDX_UPDATED));
        sub.deleted = new Date(c.getLong(COLUMN_IDX_DELETED));
        sub.read = c.getInt(COLUMN_IDX_READ);
        sub.recv = c.getInt(COLUMN_IDX_RECV);
        sub.seq = c.getInt(COLUMN_IDX_SEQ);
        sub.mode = c.getString(COLUMN_IDX_ACCESSMODE);

        sub.pub = BaseDb.deserialize(c.getBlob(COLUMN_IDX_PUBLIC));
        sub.priv = BaseDb.deserialize(c.getBlob(COLUMN_IDX_PRIVATE));

        sub.lastUsed = new Date(c.getLong(COLUMN_IDX_LASTUSED));

        return sub;
    }

    /**
     * Read Subscription given topic name
     *
     * @param topic Topic name to read
     * @return Subscription
     */
    public static <Pu,Pr> StoredTopic<Pu,Pr> readOne(SQLiteDatabase db, String topic) {
        StoredTopic<Pu, Pr> sub = null;
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() + " AND " +
                COLUMN_NAME_TOPIC + "='" + topic + "'";
        Cursor c = db.rawQuery(sql, null);
        if (c != null) {
            if (c.moveToFirst()) {
                sub = readOne(c);
            }
            c.close();
        }
        return sub;
    }

    /**
     * Given topic name, get it's database _id
     *
     * @param db database
     * @param topic topic name
     * @return _id of the topic
     */
    public static long getId(SQLiteDatabase db, String topic) {
        return db.compileStatement("SELECT " + _ID + " FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() + " AND " +
                COLUMN_NAME_TOPIC + "='" + topic + "'").simpleQueryForLong();
    }

    private static boolean updateCounter(SQLiteDatabase db, String topic, String column, int counter) {
        ContentValues values = new ContentValues();
        values.put(column, counter);
        return db.update(TABLE_NAME, values,
                COLUMN_NAME_ACCOUNT_ID + "=? AND " + COLUMN_NAME_TOPIC + "=? AND " + column + "<?",
                new String[] { String.valueOf(BaseDb.getAccountId()), topic, String.valueOf(counter) }) > 0;
    }

    public static boolean updateRead(SQLiteDatabase db, String topic, int read) {
        return updateCounter(db, topic, COLUMN_NAME_READ, read);
    }

    public static boolean updateRecv(SQLiteDatabase db, String topic, int read) {
        return updateCounter(db, topic, COLUMN_NAME_RECV, read);
    }

    public static boolean updateSeq(SQLiteDatabase db, String topic, int read) {
        return updateCounter(db, topic, COLUMN_NAME_SEQ, read);
    }
}
