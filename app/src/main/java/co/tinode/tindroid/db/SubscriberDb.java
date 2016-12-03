package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;


import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

import co.tinode.tinodesdk.model.AccessMode;
import co.tinode.tinodesdk.model.LastSeen;

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
                    COLUMN_NAME_CLEAR + " INT)";
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
    public static long insert(SQLiteDatabase db, StoredSubscription sub) {
        Log.d(TAG, "Inserting sub for " + sub.topic + "/" + sub.user);
        long id = -1;
        try {
            db.beginTransaction();
            if (sub.topicId <= 0) {
                sub.topicId = TopicDb.getId(db, sub.topic);
            }

            if (sub.userId <= 0) {
                sub.userId = UserDb.getId(db, sub.user);
            }

            if (sub.topicId <= 0 || sub.userId <= 0) {
                return -1;
            }

            // Convert subscription description to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, sub.topicId);
            values.put(COLUMN_NAME_USER_ID, sub.userId);
            // User's own sender index is 0.
            values.put(COLUMN_NAME_SENDER_INDEX, BaseDb.isMe(sub.user) ? 0 : getNextSenderIndex(db, sub.topicId));
            values.put(COLUMN_NAME_MODE, sub.mode);
            values.put(COLUMN_NAME_UPDATED, sub.updated.getTime());
            // values.put(COLUMN_NAME_DELETED, NULL);
            values.put(COLUMN_NAME_READ, sub.read);
            values.put(COLUMN_NAME_RECV, sub.recv);
            values.put(COLUMN_NAME_CLEAR, sub.clear);
            id = db.insert(TABLE_NAME, null, values);

            // Possibly Create topic
            values.clear();
            values.put(TopicDb.COLUMN_NAME_PRIVATE, BaseDb.serialize(sub.priv));
            id = db.insert(TABLE_NAME, null, values);

            db.setTransactionSuccessful();
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
    public static boolean update(SQLiteDatabase db, StoredSubscription sub) {
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
    public static long upsert(SQLiteDatabase db, StoredSubscription sub) {
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
    private static long getNextSenderIndex(SQLiteDatabase db, long topicId) {
        return db.compileStatement("SELECT count(*) FROM " + TABLE_NAME +
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

    public static <Pu,Pr> StoredSubscription readOne(Cursor c) {
        StoredSubscription s = new StoredSubscription();
        // StoredSub part
        int col = 0;
        s.id = c.getLong(col++);
        s.userId = c.getLong(col++);
        s.topicId = c.getLong(col++);
        s.senderIdx = c.getInt(col++);

        // Both
        s.mode = c.getString(col++);
        s.amode = new AccessMode(s.mode);

        // Subscription part
        // From subs table
        s.updated = new Date(c.getLong(col++));
        s.deleted = new Date(c.getLong(col++));
        s.read = c.getInt(col++);
        s.recv = c.getInt(col++);
        s.clear = c.getInt(col++);
        s.seen = new LastSeen(new Date(c.getLong(..)), c.getString(..));

        // From user table
        s.user = c.getString(col++);
        s.pub = BaseDb.deserialize(c.getBlob(col++));

        // From topic table
        s.topic = c.getString(col++);
        s.seq = c.getInt(col++);
        s.with = c.getString(col++);
        s.priv = BaseDb.deserialize(c.getBlob(col++));
    }

    public static Collection<StoredSubscription> query(SQLiteDatabase db, long topicId) {
        Cursor c = db.rawQuery("SELECT " +
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
                "  WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId, null);
        if (c == null) {
            return null;
        }

        if (!c.moveToFirst()) {
            c.close();
            return null;
        }

        Collection<StoredSubscription> result = new LinkedList<>();
        do {
            StoredSubscription s = readOne(c);
            if (s != null) {
                result.add(s);
            }
        } while (c.moveToNext());
        c.close();

        return result;
    }
}
