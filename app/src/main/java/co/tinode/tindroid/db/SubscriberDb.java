package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;


import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.AccessMode;
import co.tinode.tinodesdk.model.LastSeen;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Holds mappings of user IDs to topics
 */

public class SubscriberDb implements BaseColumns {
    private static final String TAG = "SubscriberDb";

    /**
     * The name of the table.
     */
    public static final String TABLE_NAME = "subscriptions";
    /**
     * The name of index: topic by account id and topic name.
     */
    public static final String INDEX_NAME = "subscription_topic";
    /**
     * Topic _ID, references topics._id
     */
    public static final String COLUMN_NAME_TOPIC_ID = "topic_id";
    /**
     * UID of the subscriber
     */
    public static final String COLUMN_NAME_USER_ID = "user_id";
    /**
     * Sequential ID of the user within the topic
     */
    public static final String COLUMN_NAME_SENDER_INDEX = "sender_idx";
    /**
     * User's access mode
     */
    public static final String COLUMN_NAME_MODE = "mode";
    /**
     * Last update timestamp
     */
    public static final String COLUMN_NAME_UPDATED = "updated";
    /**
     * Deletion timestamp or null
     */
    public static final String COLUMN_NAME_DELETED = "deleted";
    /**
     * Sequence ID marked as read by this user, integer
     */
    public static final String COLUMN_NAME_READ = "read";
    /**
     * Sequence ID marked as received by this user, integer
     */
    public static final String COLUMN_NAME_RECV = "recv";
    /**
     * Max sequence ID marked as deleted, integer
     */
    public static final String COLUMN_NAME_CLEAR = "clear";
    /**
     * Time stamp when the user was last seen in the topic
     */
    public static final String COLUMN_NAME_LAST_SEEN = "last_seen";
    /**
     * User agent string when last seen.
     */
    public static final String COLUMN_NAME_USER_AGENT = "user_agent";

    private static final int COLUMN_IDX_ID = 0;
    private static final int COLUMN_IDX_TOPIC_ID = 1;
    private static final int COLUMN_IDX_USER_ID = 2;
    private static final int COLUMN_IDX_SENDER_INDEX = 3;
    private static final int COLUMN_IDX_MODE = 4;
    private static final int COLUMN_IDX_UPDATED = 5;
    private static final int COLUMN_IDX_DELETED = 6;
    private static final int COLUMN_IDX_READ = 7;
    private static final int COLUMN_IDX_RECV = 8;
    private static final int COLUMN_IDX_CLEAR = 9;
    private static final int COLUMN_IDX_LAST_SEEN = 10;
    private static final int COLUMN_IDX_USER_AGENT = 11;

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
                    COLUMN_NAME_SENDER_INDEX + " INT," +
                    COLUMN_NAME_MODE + " TEXT," +
                    COLUMN_NAME_UPDATED + " INT," +
                    COLUMN_NAME_DELETED + " INT," +
                    COLUMN_NAME_READ + " INT," +
                    COLUMN_NAME_RECV + " INT," +
                    COLUMN_NAME_CLEAR + " INT," +
                    COLUMN_NAME_LAST_SEEN + " INT," +
                    COLUMN_NAME_USER_AGENT + " TEXT)";

    /**
     * Add index on account_id-topic name, in descending order
     */
    static final String CREATE_INDEX =
            "CREATE UNIQUE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" + COLUMN_NAME_TOPIC_ID + ")";

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
     * Save user's subscription to DB
     * @param db database to insert to
     * @param sub Subscription to save
     * @return ID of the newly added user
     */
    public static long insert(SQLiteDatabase db, long topicId, long userId, Subscription sub) {
        Log.d(TAG, "Inserting sub for " + sub.topic + "/" + sub.user);
        long id = -1;
        try {
            db.beginTransaction();

            StoredSubscription ss = new StoredSubscription();

            ContentValues values = new ContentValues();
            // TODO(gene): Create topic or user
            values.put(TopicDb.COLUMN_NAME_PRIVATE, BaseDb.serialize(sub.priv));
            ss.topicId = db.insert(TABLE_NAME, null, values);

            // Insert subscription
            values.clear();
            values.put(COLUMN_NAME_TOPIC_ID, ss.topicId);
            values.put(COLUMN_NAME_USER_ID, ss.userId);
            // User's own sender index is 0.
            ss.senderIdx = BaseDb.isMe(sub.user) ? 0 : getNextSenderIndex(db, topicId);
            values.put(COLUMN_NAME_SENDER_INDEX, ss.senderIdx);
            values.put(COLUMN_NAME_MODE, sub.mode);
            values.put(COLUMN_NAME_UPDATED, sub.updated.getTime());
            // values.put(COLUMN_NAME_DELETED, NULL);
            values.put(COLUMN_NAME_READ, sub.read);
            values.put(COLUMN_NAME_RECV, sub.recv);
            values.put(COLUMN_NAME_CLEAR, sub.clear);
            id = db.insert(TABLE_NAME, null, values);

            db.setTransactionSuccessful();
            ss.id = id;

            sub.setLocal(ss);

        } catch (Exception ex) {
            db.endTransaction();
        }

        return id;
    }

    /**
     * Update user record
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, Subscription sub) {
        try {
            db.beginTransaction();
            if (sub.topicId <= 0) {
                sub.topicId = TopicDb.getId(db, sub.topic);
            }

            if (sub.userId <= 0) {
                sub.userId = UserDb.getId(db, sub.user);
            }

            if (sub.topicId <= 0 || sub.userId <= 0) {
                return false;
            }

            // Convert topic description to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_MODE, sub.mode);
            values.put(COLUMN_NAME_UPDATED, sub.updated.getTime());
            if (sub.deleted != null) {
                values.put(COLUMN_NAME_DELETED, sub.deleted.getTime());
            }
            values.put(COLUMN_NAME_READ, sub.read);
            values.put(COLUMN_NAME_RECV, sub.recv);
            values.put(COLUMN_NAME_CLEAR, sub.clear);

            int updated = db.update(TABLE_NAME, values,
                    COLUMN_NAME_TOPIC_ID + "=" + sub.topicId + " AND " +
                    COLUMN_NAME_USER_ID + "=" + sub.userId,
                    null);

            Log.d(TAG, "Update row, accid=" + BaseDb.getAccountId() + " name=" + sub.user + " returned " + updated);

            // Maybe update topic
            values.clear();
            values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(sub.priv));


            db.setTransactionSuccessful();

            return updated > 0;
        } catch (Exception ex) {
            db.endTransaction();
        }

        return false;
    }

    /**
     * Save or update a subscription
     *
     * @return Id of the newly inserted user or 0 if the user was updated
     */
    public static long upsert(SQLiteDatabase db, Subscription sub) {
        if (!update(db, sub)) {
            return insert(db, sub);
        }
        return 0;
    }

    /**
     * Get the next available senderId for the given topic. Min Id == 1.
     *
     * @param db database
     * @param topicId _id of the topic to query
     * @return _id of the user
     */
    private static int getNextSenderIndex(SQLiteDatabase db, long topicId) {
        return (int) db.compileStatement("SELECT count(*) FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId).simpleQueryForLong() + 1;
    }

    /**
     * Get the next available senderId for the given topic. Min Id == 1.
     *
     * @param db database
     * @param topicId _id of the topic to query
     * @return _id of the user
     */
    protected static int getSenderIndex(SQLiteDatabase db, long topicId, long userId) {
        return (int) db.compileStatement("SELECT " + COLUMN_NAME_SENDER_INDEX + " FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_TOPIC_ID + "=" + topicId
                + " AND " +
                COLUMN_NAME_USER_ID + "=" + userId).simpleQueryForLong();
    }

    public static Subscription readOne(Cursor c) {
        // StoredSub part
        int col = 0;
        StoredSubscription ss = new StoredSubscription();
        ss.id = c.getLong(col++);
        ss.userId = c.getLong(col++);
        ss.topicId = c.getLong(col++);
        ss.senderIdx = c.getInt(col++);

        // Subscription part
        Subscription s = new Subscription();
        // From subs table
        s.mode = c.getString(col++);
        s.updated = new Date(c.getLong(col++));
        s.deleted = new Date(c.getLong(col++));
        s.read = c.getInt(col++);
        s.recv = c.getInt(col++);
        s.clear = c.getInt(col++);
        s.seen = new LastSeen(
                new Date(c.getLong(col++)),
                c.getString(col++)
        );

        // From user table
        s.user = c.getString(col++);
        s.pub = BaseDb.deserialize(c.getBlob(col++));

        // From topic table
        s.topic = c.getString(col++);
        s.seq = c.getInt(col++);
        s.with = c.getString(col++);
        s.priv = BaseDb.deserialize(c.getBlob(col));

        s.setLocal(ss);
    }

    protected static Cursor query(SQLiteDatabase db, long topicId) {
        return db.rawQuery("SELECT " +
                TABLE_NAME + "." + _ID + "," +
                TABLE_NAME + "." + COLUMN_NAME_USER_ID + "," +
                TABLE_NAME + "." + COLUMN_NAME_TOPIC_ID + "," +
                TABLE_NAME + "." + COLUMN_NAME_SENDER_INDEX + "," +
                TABLE_NAME + "." + COLUMN_NAME_MODE + "," +
                TABLE_NAME + "." + COLUMN_NAME_UPDATED + "," +
                TABLE_NAME + "." + COLUMN_NAME_DELETED + "," +
                TABLE_NAME + "." + COLUMN_NAME_READ + "," +
                TABLE_NAME + "." + COLUMN_NAME_RECV + "," +
                TABLE_NAME + "." + COLUMN_NAME_CLEAR + "," +
                TABLE_NAME + "." + COLUMN_NAME_LAST_SEEN + "," +
                TABLE_NAME + "." + COLUMN_NAME_USER_AGENT + "," +

                UserDb.TABLE_NAME + "." + UserDb.COLUMN_NAME_UID + "," +
                UserDb.TABLE_NAME + "." + UserDb.COLUMN_NAME_PUBLIC + "," +

                TopicDb.TABLE_NAME + "." + TopicDb.COLUMN_NAME_TOPIC + "," +
                TopicDb.TABLE_NAME + "." + TopicDb.COLUMN_NAME_SEQ + "," +
                TopicDb.TABLE_NAME + "." + TopicDb.COLUMN_NAME_WITH + "," +
                TopicDb.TABLE_NAME + "." + TopicDb.COLUMN_NAME_PRIVATE +
                " FROM " + TABLE_NAME +
                " LEFT JOIN " + UserDb.TABLE_NAME +
                " ON " + COLUMN_NAME_USER_ID + "=" + UserDb.TABLE_NAME + "." + UserDb._ID +
                " LEFT JOIN " + TopicDb.TABLE_NAME +
                " ON " + COLUMN_NAME_TOPIC_ID + "=" + TopicDb.TABLE_NAME + "." + TopicDb._ID +
                " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId, null);

    }

    public static Collection<Subscription> readAll(Cursor c) {
        if (!c.moveToFirst()) {
            return null;
        }

        Collection<Subscription> result = new LinkedList<>();
        do {
            Subscription s = readOne(c);
            if (s != null) {
                result.add(s);
            }
        } while (c.moveToNext());

        return result;
    }
}
