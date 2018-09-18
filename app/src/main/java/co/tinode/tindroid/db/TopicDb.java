package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;

import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;

/**
 * Store for topics
 */
@SuppressWarnings("WeakerAccess")
public class TopicDb implements BaseColumns {
    private static final String TAG = "TopicsDb";

    private static final int UNSENT_ID_START = 2000000000;

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
     * Topic sync status: queued, synced, deleted
     */
    public static final String COLUMN_NAME_STATUS = "status";
    /**
     * Topic name, indexed
     */
    public static final String COLUMN_NAME_TOPIC = "name";
    /**
     * Topic type
     */
    public static final String COLUMN_NAME_TYPE = "type";
    /**
     * Should the topic be visible to user, i.e. is it a p2p or grp topic?
     */
    public static final String COLUMN_NAME_VISIBLE = "visible";
    /**
     * When the topic was created
     */
    public static final String COLUMN_NAME_CREATED = "created";
    /**
     * When the topic was last updated
     */
    public static final String COLUMN_NAME_UPDATED = "updated";
    /**
     * Sequence ID marked as read by the current user, integer
     */
    public static final String COLUMN_NAME_READ = "read";
    /**
     * Sequence ID marked as received by the current user on any device (server-reported), integer
     */
    public static final String COLUMN_NAME_RECV = "recv";
    /**
     * Server-issued sequence ID, integer, indexed
     */
    public static final String COLUMN_NAME_SEQ = "seq";
    /**
     * Highest known ID of a delete transaction
     */
    public static final String COLUMN_NAME_CLEAR = "clear";
    /**
     * ID of the last applied delete transaction
     */
    public static final String COLUMN_NAME_MAX_DEL = "max_del";
    /**
     * Access mode, string
     */
    public static final String COLUMN_NAME_ACCESSMODE = "mode";
    /**
     * Default access mode (auth, anon)
     */
    public static final String COLUMN_NAME_DEFACS = "defacs";
    /**
     * Timestamp of the last message
     */
    public static final String COLUMN_NAME_LASTUSED = "last_used";
    /**
     * Minimum sequence ID received by the current device (self/locally-tracked), integer
     */
    public static final String COLUMN_NAME_MIN_LOCAL_SEQ = "min_local_seq";
    /**
     * Maximum sequence ID received by the current device (self/locally-tracked), integer
     */
    public static final String COLUMN_NAME_MAX_LOCAL_SEQ = "max_local_seq";
    /**
     * Seq ID to use for the next pending message.
     */
    public static final String COLUMN_NAME_NEXT_UNSENT_SEQ = "next_unsent_seq";
    /**
     * Topic tags, array of strings.
     */
    public static final String COLUMN_NAME_TAGS = "tags";
    /**
     * Public topic description, serialized as TEXT
     */
    public static final String COLUMN_NAME_PUBLIC = "pub";
    /**
     * Private topic description, serialized as TEXT
     */
    public static final String COLUMN_NAME_PRIVATE = "priv";


    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_ACCOUNT_ID = 1;
    static final int COLUMN_IDX_STATUS = 2;
    static final int COLUMN_IDX_TOPIC = 3;
    static final int COLUMN_IDX_TYPE = 4;
    static final int COLUMN_IDX_VISIBLE = 5;
    static final int COLUMN_IDX_CREATED = 6;
    static final int COLUMN_IDX_UPDATED = 7;
    static final int COLUMN_IDX_READ = 8;
    static final int COLUMN_IDX_RECV = 9;
    static final int COLUMN_IDX_SEQ = 10;
    static final int COLUMN_IDX_CLEAR = 11;
    static final int COLUMN_IDX_MAX_DEL = 12;
    static final int COLUMN_IDX_ACCESSMODE = 13;
    static final int COLUMN_IDX_DEFACS = 14;
    static final int COLUMN_IDX_LASTUSED = 15;
    static final int COLUMN_IDX_MIN_LOCAL_SEQ = 16;
    static final int COLUMN_IDX_MAX_LOCAL_SEQ = 17;
    static final int COLUMN_IDX_NEXT_UNSENT_SEQ =18;
    static final int COLUMN_IDX_TAGS = 19;
    static final int COLUMN_IDX_PUBLIC = 20;
    static final int COLUMN_IDX_PRIVATE = 21;

    /**
     * SQL statement to create Messages table
     */
    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_ACCOUNT_ID
                    + " REFERENCES " + AccountDb.TABLE_NAME + "(" + AccountDb._ID + ")," +
                    COLUMN_NAME_STATUS + " INT," +
                    COLUMN_NAME_TOPIC + " TEXT," +
                    COLUMN_NAME_TYPE + " INT," +
                    COLUMN_NAME_VISIBLE + " INT," +
                    COLUMN_NAME_CREATED + " INT," +
                    COLUMN_NAME_UPDATED + " INT," +
                    COLUMN_NAME_READ + " INT," +
                    COLUMN_NAME_RECV + " INT," +
                    COLUMN_NAME_SEQ + " INT," +
                    COLUMN_NAME_CLEAR + " INT," +
                    COLUMN_NAME_MAX_DEL + " INT," +
                    COLUMN_NAME_ACCESSMODE + " TEXT," +
                    COLUMN_NAME_DEFACS + " TEXT," +
                    COLUMN_NAME_LASTUSED + " INT," +
                    COLUMN_NAME_MIN_LOCAL_SEQ + " INT," +
                    COLUMN_NAME_MAX_LOCAL_SEQ + " INT," +
                    COLUMN_NAME_NEXT_UNSENT_SEQ + " INT," +
                    COLUMN_NAME_TAGS + " TEXT," +
                    COLUMN_NAME_PUBLIC + " TEXT," +
                    COLUMN_NAME_PRIVATE + " TEXT)";
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
    @SuppressWarnings("WeakerAccess")
    public static long insert(SQLiteDatabase db, Topic topic) {
        // Log.d(TAG, "Creating topic " + topic.getName());

        // Convert topic description to a map of values
        Date lastUsed = new Date();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_ACCOUNT_ID, BaseDb.getInstance().getAccountId());
        values.put(COLUMN_NAME_STATUS, topic.isNew() ? BaseDb.STATUS_QUEUED : BaseDb.STATUS_SYNCED);
        values.put(COLUMN_NAME_TOPIC, topic.getName());

        Topic.TopicType tp = topic.getTopicType();
        values.put(COLUMN_NAME_TYPE, tp.val());
        values.put(COLUMN_NAME_VISIBLE, tp == Topic.TopicType.GRP || tp == Topic.TopicType.P2P ? 1 : 0);
        values.put(COLUMN_NAME_CREATED, lastUsed.getTime());
        if (topic.getUpdated() != null) {
            // Updated is null at the topic creation time
            values.put(COLUMN_NAME_UPDATED, topic.getUpdated().getTime());
        }
        // values.put(COLUMN_NAME_DELETED, NULL);
        values.put(COLUMN_NAME_READ, topic.getRead());
        values.put(COLUMN_NAME_RECV, topic.getRecv());
        values.put(COLUMN_NAME_SEQ, topic.getSeq());
        values.put(COLUMN_NAME_CLEAR, topic.getClear());
        values.put(COLUMN_NAME_MAX_DEL, topic.getMaxDel());
        values.put(COLUMN_NAME_ACCESSMODE, BaseDb.serializeMode(topic.getAccessMode()));
        values.put(COLUMN_NAME_DEFACS, BaseDb.serializeDefacs(topic.getDefacs()));
        values.put(COLUMN_NAME_TAGS, BaseDb.serializeTags(topic.getTags()));
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(topic.getPub()));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(topic.getPriv()));

        values.put(COLUMN_NAME_LASTUSED, lastUsed.getTime());
        values.put(COLUMN_NAME_MIN_LOCAL_SEQ, 0);
        values.put(COLUMN_NAME_MAX_LOCAL_SEQ, 0);
        values.put(COLUMN_NAME_NEXT_UNSENT_SEQ, UNSENT_ID_START);

        long id = db.insert(TABLE_NAME, null, values);
        if (id > 0) {
            StoredTopic st = new StoredTopic();
            st.id = id;
            st.lastUsed = lastUsed;
            st.nextUnsentId = UNSENT_ID_START;
            topic.setLocal(st);
        }

        return id;
    }



    /**
     * Update topic description
     *
     * @return true if the record was updated, false otherwise
     */
    @SuppressWarnings("unchecked")
    public static boolean update(SQLiteDatabase db, Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }

        int status = st.status;
        // Convert topic description to a map of values
        ContentValues values = new ContentValues();
        if (st.status == BaseDb.STATUS_QUEUED && !topic.isNew()) {
            status = BaseDb.STATUS_SYNCED;
            values.put(COLUMN_NAME_STATUS, status);
            values.put(COLUMN_NAME_TOPIC, topic.getName());
        }
        if (topic.getUpdated() != null) {
            values.put(COLUMN_NAME_UPDATED, topic.getUpdated().getTime());
        }
        values.put(COLUMN_NAME_READ, topic.getRead());
        values.put(COLUMN_NAME_RECV, topic.getRecv());
        values.put(COLUMN_NAME_SEQ, topic.getSeq());
        values.put(COLUMN_NAME_CLEAR, topic.getClear());
        values.put(COLUMN_NAME_ACCESSMODE, BaseDb.serializeMode(topic.getAccessMode()));
        values.put(COLUMN_NAME_DEFACS, BaseDb.serializeDefacs(topic.getDefacs()));
        values.put(COLUMN_NAME_TAGS, BaseDb.serializeTags(topic.getTags()));
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(topic.getPub()));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(topic.getPriv()));

        Date lastUsed = new Date();
        values.put(COLUMN_NAME_LASTUSED, lastUsed.getTime());

        int updated = db.update(TABLE_NAME, values, _ID + "=" + st.id, null);
        if (updated > 0) {
            st.lastUsed = lastUsed;
            st.status = status;
        }

        // Log.d(TAG, "Update row, accid=" + BaseDb.getInstance().getAccountId() +
        //         " name=" + topic.getName() + " returned " + updated);

        return updated > 0;
    }

    /**
     * A message was received and stored. Update topic record with the message info
     *
     * @return true on success, false otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean msgReceived(SQLiteDatabase db, Topic topic, Date timestamp, int seq) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }

        // Convert topic description to a map of values
        ContentValues values = new ContentValues();

        if (seq > st.maxLocalSeq) {
            values.put(COLUMN_NAME_MAX_LOCAL_SEQ, seq);
            values.put(COLUMN_NAME_RECV, seq);
        }

        if (seq > 0 && (st.minLocalSeq == 0 || seq < st.minLocalSeq)) {
            values.put(COLUMN_NAME_MIN_LOCAL_SEQ, seq);
        }

        if (seq > topic.getSeq()) {
            values.put(COLUMN_NAME_SEQ, seq);
        }

        if (timestamp.after(st.lastUsed)) {
            values.put(COLUMN_NAME_LASTUSED, timestamp.getTime());
        }

        if (values.size() > 0) {
            int updated = db.update(TABLE_NAME, values, _ID + "=" + st.id, null);
            if (updated <= 0) {
                return false;
            }

            st.lastUsed = timestamp.after(st.lastUsed) ? timestamp : st.lastUsed;
            st.minLocalSeq = seq > 0 && (st.minLocalSeq == 0 || seq < st.minLocalSeq) ?
                    seq : st.minLocalSeq;
            st.maxLocalSeq = seq > st.maxLocalSeq ? seq : st.maxLocalSeq;
        }
        return true;
    }

    /**
     * Update cached ID of delete transaction.
     *
     * @return true on success
     */
    public static boolean msgDeleted(SQLiteDatabase db, Topic topic, int delId) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }

        if (delId > topic.getMaxDel()) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_MAX_DEL, topic.getMaxDel());

            int updated = db.update(TABLE_NAME, values, _ID + "=" + st.id, null);
            return updated > 0;
        }
        return true;
    }
    /**
     * Query topics.
     *
     * @param db database to select from;
     * @return cursor with topics
     */
    public static Cursor query(SQLiteDatabase db) {
        // Log.d(TAG, "Querying");

        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getInstance().getAccountId() +
                " ORDER BY " + COLUMN_NAME_LASTUSED + " DESC";

        // Log.d(TAG, sql);
        return db.rawQuery(sql, null);
    }

    /**
     * Read Topic at the current cursor position.
     *
     * @param c Cursor to read from
     * @return Subscription
     */
    @SuppressWarnings("unchecked, WeakerAccess")
    protected static Topic readOne(Tinode tinode, Cursor c) {
        // Instantiate topic of an appropriate class ('me' or 'fnd' or group)
        Topic topic = tinode.newTopic(c.getString(COLUMN_IDX_TOPIC), null);
        StoredTopic.deserialize(topic, c);
        return topic;
    }

    /**
     * Read topic given its name
     *
     * @param db database to use
     * @param name Name of the topic to read
     * @return Subscription
     */
    protected static Topic readOne(SQLiteDatabase db, Tinode tinode, String name) {
        Topic topic = null;
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getInstance().getAccountId() + " AND " +
                COLUMN_NAME_TOPIC + "='" + name + "'";
        Cursor c = db.rawQuery(sql, null);
        if (c != null) {
            if (c.moveToFirst()) {
                topic = readOne(tinode, c);
            }
            c.close();
        }
        return topic;
    }

    /**
     * Delete topic by name
     *
     * @param db writable database
     * @param id of the topic to delete
     * @return true if table was actually deleted, false if table was not found
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean delete(SQLiteDatabase db, long id) {
        return db.delete(TABLE_NAME, _ID + "=" + id, null) > 0;
    }

    /**
     * Given topic name, get it's database _id
     *
     * @param db database
     * @param topic topic name
     * @return _id of the topic
     */
    public static long getId(SQLiteDatabase db, String topic) {
        try {
            return db.compileStatement("SELECT " + _ID + " FROM " + TABLE_NAME +
                    " WHERE " +
                    COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getInstance().getAccountId() + " AND " +
                    COLUMN_NAME_TOPIC + "='" + topic + "'").simpleQueryForLong();
        } catch (SQLException ignored) {
            // topic not round
            return -1;
        }
    }

    public static int getNextUnsentSeq(SQLiteDatabase db, Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null) {
            st.nextUnsentId ++;
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_NEXT_UNSENT_SEQ, st.nextUnsentId);
            db.update(TABLE_NAME, values, _ID + "=" + st.id, null);
            return st.nextUnsentId;
        }

        throw new IllegalArgumentException("Stored topic undefined " + topic.getName());
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean updateRead(SQLiteDatabase db, long topicId, int read) {
        return BaseDb.updateCounter(db, TABLE_NAME, COLUMN_NAME_READ, topicId, read);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean updateRecv(SQLiteDatabase db, long topicId, int recv) {
        return BaseDb.updateCounter(db, TABLE_NAME, COLUMN_NAME_RECV, topicId, recv);
    }

    public static boolean updateSeq(SQLiteDatabase db, long topicId, int seq) {
        return BaseDb.updateCounter(db, TABLE_NAME, COLUMN_NAME_SEQ, topicId, seq);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean updateClear(SQLiteDatabase db, long topicId, int clear) {
        return BaseDb.updateCounter(db, TABLE_NAME, COLUMN_NAME_CLEAR, topicId, clear);
    }
}
