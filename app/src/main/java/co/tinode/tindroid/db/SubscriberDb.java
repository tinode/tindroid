package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
     * Sequence ID marked as read by this user, integer
     */
    public static final String COLUMN_NAME_READ = "read";
    /**
     * Sequence ID marked as received by this user, integer
     */
    public static final String COLUMN_NAME_RECV = "recv";
    /**
     * Per-subscription private data
     */
    public static final String COLUMN_NAME_PRIVATE = "private";


    private static final int COLUMN_IDX_ID = 0;
    private static final int COLUMN_IDX_TOPIC_ID = 1;
    private static final int COLUMN_IDX_USER_ID = 2;
    private static final int COLUMN_IDX_SENDER_INDEX = 3;
    private static final int COLUMN_IDX_MODE = 4;
    private static final int COLUMN_IDX_READ = 5;
    private static final int COLUMN_IDX_RECV = 6;
    private static final int COLUMN_IDX_PRIVATE = 7;

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
                    COLUMN_NAME_READ + " INT," +
                    COLUMN_NAME_RECV + " INT," +
                    COLUMN_NAME_PRIVATE + " BLOB)";
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
     * @param topicId database topics._id of the topic. If it's <=0, read it from the database.
     * @param userId  database users._id of the user. If it's <=0, read it from the database.
     * @param sub Subscription to save
     * @return ID of the newly added user
     */
    public static long insert(SQLiteDatabase db, long topicId, long userId, Subscription sub) {
        Log.d(TAG, "Inserting sub for " + sub.topic + "/" + sub.user);
        long id = -1;
        try {
            db.beginTransaction();
            if (topicId <= 0) {
                topicId = TopicDb.getId(db, sub.topic);
            }

            if (userId <= 0) {
                userId = UserDb.getId(db, sub.user);
            }

            if (topicId <= 0 || userId <= 0) {
                return -1;
            }

            // Convert subscription description to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, topicId);
            values.put(COLUMN_NAME_USER_ID, userId);
            // User's own sender index is 0.
            values.put(COLUMN_NAME_SENDER_INDEX, BaseDb.isMe(sub.user) ? 0 : getNextSenderIndex(db, topicId));
            // values.put(COLUMN_NAME_DELETED, NULL);
            values.put(COLUMN_NAME_MODE, sub.mode);
            values.put(COLUMN_NAME_READ, sub.read);
            values.put(COLUMN_NAME_RECV, sub.recv);
            values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(sub.priv));

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
    public static boolean update(SQLiteDatabase db, long topicId, long userId, Subscription sub) {
        try {
            db.beginTransaction();
            if (topicId <= 0) {
                topicId = TopicDb.getId(db, sub.topic);
            }

            if (userId <= 0) {
                userId = UserDb.getId(db, sub.user);
            }

            if (topicId <= 0 || userId <= 0) {
                return false;
            }

            // Convert topic description to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_MODE, sub.mode);
            values.put(COLUMN_NAME_READ, sub.read);
            values.put(COLUMN_NAME_RECV, sub.recv);
            values.put(COLUMN_NAME_PRIVATE, BaseDb.serialize(sub.priv));

            int updated = db.update(TABLE_NAME, values,
                    COLUMN_NAME_TOPIC_ID + "=" + topicId + " AND " +
                    COLUMN_NAME_USER_ID + "=" + userId,
                    null);

            Log.d(TAG, "Update row, accid=" + BaseDb.getAccountId() + " name=" + sub.user + " returned " + updated);
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
    public static long upsert(SQLiteDatabase db, long topicId, long userId, Subscription sub) {
        if (!update(db, topicId, userId, sub)) {
            return insert(db, topicId, userId, sub);
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

    protected static Map<String,StoredUser> getSenders(SQLiteDatabase db, long topicId) {
        Map<String,StoredUser> result = new HashMap<>();
        Cursor c = db.rawQuery("SELECT " +
                TABLE_NAME + "." + COLUMN_NAME_USER_ID + "," +
                TABLE_NAME + "." + COLUMN_NAME_SENDER_INDEX + "," +
                TABLE_NAME + "." + COLUMN_NAME_MODE + "," +
                TABLE_NAME + "." + COLUMN_NAME_READ + "," +
                TABLE_NAME + "." + COLUMN_NAME_RECV + "," +
                TABLE_NAME + "." + COLUMN_NAME_PRIVATE + "," +
                UserDb.TABLE_NAME + "." + UserDb.COLUMN_NAME_UID + "," +
                UserDb.TABLE_NAME + "." + UserDb.COLUMN_NAME_UPDATED + "," +
                UserDb.TABLE_NAME + "." + UserDb.COLUMN_NAME_DELETED + "," +
                UserDb.TABLE_NAME + "." + UserDb.COLUMN_NAME_PUBLIC + "," +
                " FROM " + TABLE_NAME +
                " JOIN " + UserDb.TABLE_NAME +
                " ON " + COLUMN_NAME_USER_ID + "=" + UserDb.TABLE_NAME + "." + UserDb._ID +
                "  WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId, null);
        if (c == null) {
            return null;
        }
        while (c.moveToNext()) {
            StoredUser u = new StoredUser();
            u.id = c.getLong(0);
            u.senderIdx = c.getInt(1);
            u.mode = c.getString(2);
            u.read = c.getInt(3);
            u.recv = c.getInt(4);
            u.priv = BaseDb.deserialize(c.getBlob(5));
            u.uid = c.getString(6);
            u.updated = new Date(c.getLong(7));
            u.deleted = new Date(c.getLong(8));
            u.pub = BaseDb.deserialize(c.getBlob(9));
            result.put(u.uid, u);
        }
        c.close();
        return result;
    }
}
