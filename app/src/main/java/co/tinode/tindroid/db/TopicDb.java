package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;

import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;

/**
 * Store for topics
 */
@SuppressWarnings("WeakerAccess")
public class TopicDb implements BaseColumns {
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
     * When the topic was created
     */
    public static final String COLUMN_NAME_CREATED = "created";
    /**
     * When the topic was last updated
     */
    public static final String COLUMN_NAME_UPDATED = "updated";
    /**
     * When the topic was last changed: either updated or received a message.
     */
    public static final String COLUMN_NAME_CHANNEL_ACCESS = "channel_access";
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
     * Highest known ID of a message deletion transaction.
     */
    public static final String COLUMN_NAME_CLEAR = "clear";
    /**
     * ID of the last applied message deletion transaction.
     */
    public static final String COLUMN_NAME_MAX_DEL = "max_del";
    /**
     * Access mode, string.
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
     * Auxiliary topic data, key-value map.
     */
    public static final String COLUMN_NAME_AUX = "aux";
    /**
     * Timestamp when the topic was last online.
     */
    public static final String COLUMN_NAME_LAST_SEEN = "last_seen";
    /**
     * User agent of the last client when the topic was last online.
     */
    public static final String COLUMN_NAME_LAST_SEEN_UA = "last_seen_ua";
    /**
     * MeTopic credentials, serialized as TEXT.
     */
    public static final String COLUMN_NAME_CREDS = "creds";
    /**
     * Public topic description, serialized as TEXT
     */
    public static final String COLUMN_NAME_PUBLIC = "pub";
    /**
     * Trusted values, serialized as TEXT
     */
    public static final String COLUMN_NAME_TRUSTED = "trusted";
    /**
     * Private topic description, serialized as TEXT
     */
    public static final String COLUMN_NAME_PRIVATE = "priv";

    static final int COLUMN_IDX_ID = 0;
    // static final int COLUMN_IDX_ACCOUNT_ID = 1;
    static final int COLUMN_IDX_STATUS = 2;
    static final int COLUMN_IDX_TOPIC = 3;
    // static final int COLUMN_IDX_CREATED = 4;
    static final int COLUMN_IDX_UPDATED = 5;
    static final int COLUMN_IDX_CHANNEL_ACCESS = 6;
    static final int COLUMN_IDX_READ = 7;
    static final int COLUMN_IDX_RECV = 8;
    static final int COLUMN_IDX_SEQ = 9;
    static final int COLUMN_IDX_CLEAR = 10;
    static final int COLUMN_IDX_MAX_DEL = 11;
    static final int COLUMN_IDX_ACCESSMODE = 12;
    static final int COLUMN_IDX_DEFACS = 13;
    static final int COLUMN_IDX_LASTUSED = 14;
    static final int COLUMN_IDX_MIN_LOCAL_SEQ = 15;
    static final int COLUMN_IDX_MAX_LOCAL_SEQ = 16;
    static final int COLUMN_IDX_NEXT_UNSENT_SEQ = 17;
    static final int COLUMN_IDX_TAGS = 18;
    static final int COLUMN_IDX_AUX = 19;
    static final int COLUMN_IDX_LAST_SEEN = 20;
    static final int COLUMN_IDX_LAST_SEEN_UA = 21;
    static final int COLUMN_IDX_CREDS = 22;
    static final int COLUMN_IDX_PUBLIC = 23;
    static final int COLUMN_IDX_TRUSTED = 24;
    static final int COLUMN_IDX_PRIVATE = 25;
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
                    COLUMN_NAME_CREATED + " INT," +
                    COLUMN_NAME_UPDATED + " INT," +
                    COLUMN_NAME_CHANNEL_ACCESS + " INT," +
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
                    COLUMN_NAME_AUX + " TEXT," +
                    COLUMN_NAME_LAST_SEEN + " INT," +
                    COLUMN_NAME_LAST_SEEN_UA + " TEXT," +
                    COLUMN_NAME_CREDS + " TEXT," +
                    COLUMN_NAME_PUBLIC + " TEXT," +
                    COLUMN_NAME_TRUSTED + " TEXT," +
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
    private static final String TAG = "TopicsDb";

    /**
     * Save topic description to DB
     *
     * @return ID of the newly added message
     */
    public static long insert(SQLiteDatabase db, Topic topic) {
        BaseDb.Status status = topic.isNew() ? BaseDb.Status.QUEUED : BaseDb.Status.SYNCED;

        // Convert topic description to a map of values. If value is not set use a magical constant.
        // 1414213562373L is Oct 25, 2014 05:06:02.373 UTC, incidentally equal to the first few digits of sqrt(2)
        Date lastUsed = topic.getTouched() != null ? topic.getTouched() : new Date(1414213562373L);
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_ACCOUNT_ID, BaseDb.getInstance().getAccountId());
        values.put(COLUMN_NAME_STATUS, status.value);
        values.put(COLUMN_NAME_TOPIC, topic.getName());

        values.put(COLUMN_NAME_CREATED, lastUsed.getTime());
        if (topic.getUpdated() != null) {
            // Updated is null at the topic creation time
            values.put(COLUMN_NAME_UPDATED, topic.getUpdated().getTime());
        }
        if (topic instanceof ComTopic) {
            values.put(COLUMN_NAME_CHANNEL_ACCESS, ((ComTopic) topic).hasChannelAccess());
        }
        values.put(COLUMN_NAME_READ, topic.getRead());
        values.put(COLUMN_NAME_RECV, topic.getRecv());
        values.put(COLUMN_NAME_SEQ, topic.getSeq());
        values.put(COLUMN_NAME_CLEAR, topic.getClear());
        values.put(COLUMN_NAME_MAX_DEL, topic.getMaxDel());
        values.put(COLUMN_NAME_ACCESSMODE, BaseDb.serializeMode(topic.getAccessMode()));
        values.put(COLUMN_NAME_DEFACS, BaseDb.serializeDefacs(topic.getDefacs()));
        values.put(COLUMN_NAME_TAGS, BaseDb.serializeStringArray(topic.getTags()));
        values.put(COLUMN_NAME_AUX, BaseDb.serialize(topic.getAux()));
        if (topic.getLastSeen() != null) {
            values.put(COLUMN_NAME_LAST_SEEN, topic.getLastSeen().getTime());
        }
        if (topic.getLastSeenUA() != null) {
            values.put(COLUMN_NAME_LAST_SEEN_UA, topic.getLastSeenUA());
        }
        if (topic instanceof MeTopic) {
            values.put(COLUMN_NAME_CREDS, BaseDb.serialize(((MeTopic) topic).getCreds()));
        }
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(topic.getPub()));
        values.put(COLUMN_NAME_TRUSTED, BaseDb.serialize(topic.getTrusted()));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(topic.getPriv()));

        values.put(COLUMN_NAME_LASTUSED, lastUsed.getTime());
        values.put(COLUMN_NAME_MIN_LOCAL_SEQ, 0);
        values.put(COLUMN_NAME_MAX_LOCAL_SEQ, 0);
        values.put(COLUMN_NAME_NEXT_UNSENT_SEQ, BaseDb.UNSENT_ID_START);

        long id = db.insert(TABLE_NAME, null, values);
        if (id > 0) {
            StoredTopic st = new StoredTopic();
            st.id = id;
            st.lastUsed = lastUsed;
            st.nextUnsentId = BaseDb.UNSENT_ID_START;
            st.status = status;
            topic.setLocal(st);
        }

        return id;
    }

    /**
     * Update topic description
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }

        BaseDb.Status status = st.status;
        // Convert topic description to a map of values
        ContentValues values = new ContentValues();

        if (st.status == BaseDb.Status.QUEUED && !topic.isNew()) {
            status = BaseDb.Status.SYNCED;
            values.put(COLUMN_NAME_STATUS, status.value);
            values.put(COLUMN_NAME_TOPIC, topic.getName());
        }
        if (topic.getUpdated() != null) {
            values.put(COLUMN_NAME_UPDATED, topic.getUpdated().getTime());
        }
        if (topic instanceof ComTopic) {
            values.put(COLUMN_NAME_CHANNEL_ACCESS, ((ComTopic) topic).hasChannelAccess());
        }
        values.put(COLUMN_NAME_READ, topic.getRead());
        values.put(COLUMN_NAME_RECV, topic.getRecv());
        values.put(COLUMN_NAME_SEQ, topic.getSeq());
        values.put(COLUMN_NAME_CLEAR, topic.getClear());
        values.put(COLUMN_NAME_ACCESSMODE, BaseDb.serializeMode(topic.getAccessMode()));
        values.put(COLUMN_NAME_DEFACS, BaseDb.serializeDefacs(topic.getDefacs()));
        values.put(COLUMN_NAME_TAGS, BaseDb.serializeStringArray(topic.getTags()));
        values.put(COLUMN_NAME_AUX, BaseDb.serialize(topic.getAux()));
        if (topic.getLastSeen() != null) {
            values.put(COLUMN_NAME_LAST_SEEN, topic.getLastSeen().getTime());
        }
        if (topic.getLastSeenUA() != null) {
            values.put(COLUMN_NAME_LAST_SEEN_UA, topic.getLastSeenUA());
        }
        if (topic instanceof MeTopic) {
            values.put(COLUMN_NAME_CREDS, BaseDb.serialize(((MeTopic) topic).getCreds()));
        }
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(topic.getPub()));
        values.put(COLUMN_NAME_TRUSTED, BaseDb.serialize(topic.getTrusted()));
        values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(topic.getPriv()));

        Date lastUsed = topic.getTouched();
        if (lastUsed != null) {
            values.put(COLUMN_NAME_LASTUSED, lastUsed.getTime());
        }

        int updated = db.update(TABLE_NAME, values, _ID + "=" + st.id, null);
        if (updated > 0) {
            if (lastUsed != null) {
                st.lastUsed = lastUsed;
            }
            st.status = status;
        }

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
            st.maxLocalSeq = Math.max(seq, st.maxLocalSeq);
        }
        return true;
    }

    /**
     * Update cached ID of a delete transaction.
     *
     * @param db    database reference.
     * @param topic topic to update.
     * @param delId server-issued deletion ID.
     * @param lowId lowest seq ID in the deleted range, inclusive (closed).
     * @param hiId  greatest seq ID in the deletion range, exclusive (open).
     * @return true on success
     */
    public static boolean msgDeleted(SQLiteDatabase db, Topic topic, int delId, int lowId, int hiId) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st == null) {
            return false;
        }

        ContentValues values = new ContentValues();
        if (delId > topic.getMaxDel()) {
            values.put(COLUMN_NAME_MAX_DEL, delId);
        }

        // If lowId is 0, all earlier messages are being deleted, set it to lowest possible value: 1.
        if (lowId <= 0) {
            lowId = 1;
        }

        if (hiId > 1) {
            // Upper bound is exclusive. Convert to inclusive.
            hiId--;
        } else {
            // If hiId is zero all later messages are being deleted, set it to highest possible value.
            hiId = topic.getSeq();
        }

        // Expand the available range only when there is an overlap.

        // When minLocalSeq is 0 then there are no locally stored messages possibly because they have not been fetched yet.
        // Don't update minLocalSeq otherwise the client may miss some messages.
        if (lowId < st.minLocalSeq && hiId >= st.minLocalSeq) {
            values.put(COLUMN_NAME_MIN_LOCAL_SEQ, lowId);
        } else {
            lowId = -1;
        }

        if (hiId > st.maxLocalSeq && lowId <= st.maxLocalSeq) {
            values.put(COLUMN_NAME_MAX_LOCAL_SEQ, hiId);
        } else {
            hiId = -1;
        }

        if (values.size() > 0) {
            int updated = db.update(TABLE_NAME, values, _ID + "=" + st.id, null);
            if (updated <= 0) {
                Log.d(TAG, "Failed to update table records on delete");
                return false;
            }
            if (lowId > 0) {
                st.minLocalSeq = lowId;
            }
            if (hiId > 0) {
                st.maxLocalSeq = hiId;
            }
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
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getInstance().getAccountId() +
                " ORDER BY " + COLUMN_NAME_LASTUSED + " DESC";

        return db.rawQuery(sql, null);
    }

    /**
     * Read Topic at the current cursor position.
     *
     * @param c Cursor to read from
     * @return Subscription
     */
    @SuppressWarnings("WeakerAccess")
    protected static Topic readOne(Tinode tinode, Cursor c) {
        // Instantiate topic of an appropriate class ('me' or 'fnd' or group)
        Topic topic = Tinode.newTopic(tinode, c.getString(COLUMN_IDX_TOPIC), null);
        StoredTopic.deserialize(topic, c);
        return topic;
    }

    /**
     * Read topic given its name
     *
     * @param db   database to use
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
        if (c.moveToFirst()) {
            topic = readOne(tinode, c);
        }
        c.close();
        return topic;
    }

    /**
     * Delete topic by database id.
     *
     * @param db writable database
     * @param id of the topic to delete
     * @return true if table was actually deleted, false if table was not found
     */
    public static boolean delete(SQLiteDatabase db, long id) {
        return db.delete(TABLE_NAME, _ID + "=" + id, null) > 0;
    }

    /**
     * Mark topic as deleted without removing it from the database.
     *
     * @param db writable database
     * @param id of the topic to delete
     * @return true if table was actually deleted, false if table was not found
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean markDeleted(SQLiteDatabase db, long id) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_STATUS, BaseDb.Status.DELETED_HARD.value);
        return db.update(TABLE_NAME, values, _ID + "=" + id, null) > 0;
    }

    /**
     * Delete all topics of the given account ID.
     * Also deletes subscriptions and messages.
     */
    static void deleteAll(SQLiteDatabase db, long accId) {
        // Delete messages.
        String sql = "DELETE FROM " + MessageDb.TABLE_NAME +
                " WHERE " + MessageDb.COLUMN_NAME_TOPIC_ID + " IN (" +
                "SELECT " + _ID + " FROM " + TABLE_NAME + " WHERE " + COLUMN_NAME_ACCOUNT_ID + "=" + accId +
                ")";
        db.execSQL(sql);
        // Delete subscribers.
        sql = "DELETE FROM " + SubscriberDb.TABLE_NAME +
                " WHERE " + SubscriberDb.COLUMN_NAME_TOPIC_ID + " IN (" +
                "SELECT " + _ID + " FROM " + TABLE_NAME + " WHERE " + COLUMN_NAME_ACCOUNT_ID + "=" + accId +
                ")";
        db.execSQL(sql);
        db.delete(TABLE_NAME, COLUMN_NAME_ACCOUNT_ID + "=" + accId, null);
    }

    /**
     * Deletes all records from 'topics' table.
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
     * Given topic name, get it's database _id
     *
     * @param db    database
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
            // topic not found
            return -1;
        }
    }

    public static synchronized int getNextUnsentSeq(SQLiteDatabase db, Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        if (st != null) {
            st.nextUnsentId++;
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
}
