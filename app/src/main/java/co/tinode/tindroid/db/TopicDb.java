package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;

import co.tinode.tindroid.Cache;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgSetMeta;
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
     * Minimum message id available
     */
    public static final String COLUMN_NAME_CLEAR = "clear";
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


    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_ACCOUNT_ID = 1;
    static final int COLUMN_IDX_TOPIC = 2;
    static final int COLUMN_IDX_TYPE = 3;
    static final int COLUMN_IDX_WITH = 4;
    static final int COLUMN_IDX_CREATED = 5;
    static final int COLUMN_IDX_UPDATED = 6;
    static final int COLUMN_IDX_DELETED = 7;
    static final int COLUMN_IDX_READ = 8;
    static final int COLUMN_IDX_RECV = 9;
    static final int COLUMN_IDX_SEQ = 10;
    static final int COLUMN_IDX_CLEAR = 11;
    static final int COLUMN_IDX_ACCESSMODE = 12;
    static final int COLUMN_IDX_LASTUSED = 13;
    static final int COLUMN_IDX_PUBLIC = 14;
    static final int COLUMN_IDX_PRIVATE = 15;

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
                    COLUMN_NAME_CLEAR + " INT," +
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
    public static long insert(SQLiteDatabase db, StoredTopic topic) {
        Log.d(TAG, "Inserting sub for " + topic.getName());

        // Convert topic description to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_ACCOUNT_ID, BaseDb.getAccountId());
        values.put(COLUMN_NAME_TOPIC, topic.getName());
        values.put(COLUMN_NAME_TYPE, topic.getTopicType().val());
        values.put(COLUMN_NAME_WITH, topic.getWith());
        // values.put(COLUMN_NAME_CREATED, NULL);
        values.put(COLUMN_NAME_UPDATED, topic.getUpdated().getTime());
        // values.put(COLUMN_NAME_DELETED, NULL);
        values.put(COLUMN_NAME_READ, topic.getRead());
        values.put(COLUMN_NAME_RECV, topic.getRecv());
        values.put(COLUMN_NAME_SEQ, topic.getSeq());
        values.put(COLUMN_NAME_CLEAR, topic.getClear());
        values.put(COLUMN_NAME_ACCESSMODE, topic.getMode().toString());
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(topic.getPub()));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(topic.getPriv()));

        values.put(COLUMN_NAME_LASTUSED, new Date().getTime());

        long id = db.insert(TABLE_NAME, null, values);
        topic.setId(id);

        return id;
    }

    /**
     * Update topic description
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, StoredTopic topic) {

        // Convert topic description to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_UPDATED, topic.getUpdated().getTime());
        // values.put(COLUMN_NAME_DELETED, NULL);
        values.put(COLUMN_NAME_READ, topic.getRead());
        values.put(COLUMN_NAME_RECV, topic.getRecv());
        values.put(COLUMN_NAME_SEQ, topic.getSeq());
        values.put(COLUMN_NAME_CLEAR, topic.getClear());
        values.put(COLUMN_NAME_ACCESSMODE, topic.getMode().toString());
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(topic.getPub()));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(topic.getPriv()));

        values.put(COLUMN_NAME_LASTUSED, new Date().getTime());
        int updated = db.update(TABLE_NAME, values,
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() +
                        " AND " + COLUMN_NAME_TOPIC + "='" + topic.getName() + "'",
                null);

        Log.d(TAG, "Update row, accid=" + BaseDb.getAccountId() + " name=" + topic.getName() + " returned " + updated);

        return updated > 0;
    }

    /**
     * Update stored topic on setMeta (local user-initiated update).
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, String topicName, Date timestamp, MsgSetMeta meta) {
        if (meta.desc == null) {
            // Nothing to update
            return true;
        }

        ContentValues values = new ContentValues();

        values.put(COLUMN_NAME_UPDATED, timestamp.getTime());
        if (meta.desc.defacs != null) {
            // do something?
        }
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(meta.desc.pub));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(meta.desc.priv));
        values.put(COLUMN_NAME_LASTUSED, new Date().getTime());
        int updated = db.update(TABLE_NAME, values,
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() +
                        " AND " + COLUMN_NAME_TOPIC + "='" + topicName + "'",
                null);

        Log.d(TAG, "Update row by meta.desc, accid=" + BaseDb.getAccountId() + " name=" + topicName + " returned " + updated);

        return updated > 0;
    }

    /**
     * Update stored topic on {meta} (remote user-initiated update).
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, String topicName, Date timestamp, Description desc) {
        ContentValues values = new ContentValues();

        values.put(COLUMN_NAME_UPDATED, desc.updated.getTime());
        if (desc.created != null) {
            values.put(COLUMN_NAME_CREATED, desc.created.getTime());
        }
        if (desc.deleted != null) {
            values.put(COLUMN_NAME_DELETED, desc.deleted.getTime());
        }

        if (desc.defacs != null) {
            // Maybe do something here
        }

        // P2P only
        values.put(COLUMN_NAME_READ, desc.read);
        values.put(COLUMN_NAME_RECV, desc.recv);
        values.put(COLUMN_NAME_SEQ, desc.seq);
        values.put(COLUMN_NAME_CLEAR, desc.clear);
        if (desc.acs != null) {
            values.put(COLUMN_NAME_ACCESSMODE, desc.acs.toString());
        }
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(desc.pub));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(desc.priv));
        if (desc.with != null) {
            values.put(COLUMN_NAME_WITH, desc.with);
        }
        values.put(COLUMN_NAME_LASTUSED, new Date().getTime());

        int updated = db.update(TABLE_NAME, values,
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() +
                        " AND " + COLUMN_NAME_TOPIC + "='" + topicName + "'",
                null);

        Log.d(TAG, "Update row by meta.desc, accid=" + BaseDb.getAccountId() + " name=" + topicName + " returned " + updated);

        return updated > 0;
    }

    /**
     * Save or update a topic
     *
     * @return Id of the newly inserted topic or 0 if the topic was updated
     */
    public static long upsert(SQLiteDatabase db, StoredTopic topic) {
        if (!update(db, topic)) {
            return insert(db, topic);
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
    public static <Pu,Pr,T> StoredTopic<Pu,Pr,T> readOne(Cursor c) {

        String name = c.getString(COLUMN_IDX_TOPIC);
        StoredTopic<Pu,Pr,T> topic = new StoredTopic<>(Cache.getTinode(), name, null);
        topic.deserialize(c);

        return topic;
    }

    /**
     * Read Subscription given topic name
     *
     * @param name Name of the topic to read
     * @return Subscription
     */
    public static <Pu,Pr,T> StoredTopic<Pu,Pr,T> readOne(SQLiteDatabase db, String name) {
        StoredTopic<Pu,Pr,T> topic = null;
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() + " AND " +
                COLUMN_NAME_TOPIC + "='" + name + "'";
        Cursor c = db.rawQuery(sql, null);
        if (c != null) {
            if (c.moveToFirst()) {
                topic = readOne(c);
            }
            c.close();
        }
        return topic;
    }

    public static int delete(SQLiteDatabase db, String topic) {
        String where = "WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() + " AND " +
                COLUMN_NAME_TOPIC + "='" + topic + "'";
        return db.delete(TABLE_NAME, where, null);
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
