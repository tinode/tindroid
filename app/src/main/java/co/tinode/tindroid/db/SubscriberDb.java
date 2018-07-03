package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

import co.tinode.tinodesdk.model.LastSeen;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Holds mappings of user IDs to topics
 */

public class SubscriberDb implements BaseColumns {
    /**
     * The name of the table.
     */
    public static final String TABLE_NAME = "subscriptions";
    /**
     * The name of index: topic_id.
     */
    public static final String INDEX_NAME = "subscription_topic_id";
    /**
     * Topic _ID, references topics._id
     */
    public static final String COLUMN_NAME_TOPIC_ID = "topic_id";
    /**
     * UID of the subscriber
     */
    public static final String COLUMN_NAME_USER_ID = "user_id";
    /**
     * Status of subscription: unsent, delivered, deleted
     */
    public static final String COLUMN_NAME_STATUS = "status";
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
                    COLUMN_NAME_STATUS + " INT," +
                    COLUMN_NAME_MODE + " TEXT," +
                    COLUMN_NAME_UPDATED + " INT," +
                    COLUMN_NAME_DELETED + " INT," +
                    COLUMN_NAME_READ + " INT," +
                    COLUMN_NAME_RECV + " INT," +
                    COLUMN_NAME_CLEAR + " INT," +
                    COLUMN_NAME_LAST_SEEN + " INT," +
                    COLUMN_NAME_USER_AGENT + " TEXT)";
    /**
     * Add index on topic_id
     */
    static final String CREATE_INDEX =
            "CREATE INDEX " + INDEX_NAME +
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
    private static final String TAG = "SubscriberDb";
    private static final int COLUMN_IDX_ID = 0;
    private static final int COLUMN_IDX_TOPIC_ID = 1;
    private static final int COLUMN_IDX_USER_ID = 2;
    private static final int COLUMN_IDX_STATUS = 3;
    private static final int COLUMN_IDX_MODE = 4;
    private static final int COLUMN_IDX_UPDATED = 5;
    private static final int COLUMN_IDX_DELETED = 6;
    private static final int COLUMN_IDX_READ = 7;
    private static final int COLUMN_IDX_RECV = 8;
    private static final int COLUMN_IDX_CLEAR = 9;
    private static final int COLUMN_IDX_LAST_SEEN = 10;
    private static final int COLUMN_IDX_USER_AGENT = 11;
    private static final int JOIN_USER_COLUMN_IDX_UID = 12;
    private static final int JOIN_USER_COLUMN_IDX_PUBLIC = 13;
    private static final int JOIN_TOPIC_COLUMN_IDX_TOPIC = 14;
    private static final int JOIN_TOPIC_COLUMN_IDX_SEQ = 15;

    /**
     * Save user's subscription to topic.
     *
     * @param db  database to insert to
     * @param sub Subscription to save
     * @return database ID of the newly added subscription
     */
    public static long insert(SQLiteDatabase db, long topicId, int status, Subscription sub) {
        // Log.d(TAG, "Inserting sub for " + topicId + "/" + sub.user);
        long id = -1;
        try {
            db.beginTransaction();

            StoredSubscription ss = new StoredSubscription();

            ss.userId = UserDb.getId(db, sub.user);
            if (ss.userId <= 0) {
                ss.userId = UserDb.insert(db, sub);
            }
            if (ss.userId <= 0) {
                Log.e(TAG, "Failed to insert user: " + ss.userId);
                db.endTransaction();
                return -1;
            }

            ContentValues values = new ContentValues();
            // Insert subscription
            ss.topicId = topicId;
            values.put(COLUMN_NAME_TOPIC_ID, ss.topicId);
            values.put(COLUMN_NAME_USER_ID, ss.userId);
            ss.status = status;
            values.put(COLUMN_NAME_STATUS, ss.status);
            values.put(COLUMN_NAME_MODE, BaseDb.serializeMode(sub.acs));
            values.put(COLUMN_NAME_UPDATED, (sub.updated != null ? sub.updated : new Date()).getTime());
            // values.put(COLUMN_NAME_DELETED, NULL);
            values.put(COLUMN_NAME_READ, sub.read);
            values.put(COLUMN_NAME_RECV, sub.recv);
            values.put(COLUMN_NAME_CLEAR, sub.clear);

            if (sub.seen != null) {
                if (sub.seen.when != null) {
                    values.put(COLUMN_NAME_LAST_SEEN, sub.seen.when.getTime());
                }
                if (sub.seen.ua != null) {
                    values.put(COLUMN_NAME_USER_AGENT, sub.seen.ua);
                }
            }
            ss.id = db.insert(TABLE_NAME, null, values);

            db.setTransactionSuccessful();
            sub.setLocal(ss);

        } catch (SQLException ignored) {
            Log.e(TAG, "Exception while inserting", ignored);
        }

        db.endTransaction();

        return id;
    }

    /**
     * Update user record
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, Subscription sub) {
        int updated = -1;

        try {
            StoredSubscription ss = (StoredSubscription) sub.getLocal();
            if (ss == null || ss.id < 0) {
                return false;
            }

            db.beginTransaction();

            int status = ss.status;

            // Update user
            UserDb.update(db, sub);

            // Convert topic description to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_MODE, BaseDb.serializeMode(sub.acs));
            if (sub.updated != null) {
                values.put(COLUMN_NAME_UPDATED, sub.updated.getTime());
            }
            if (ss.status != BaseDb.STATUS_SYNCED) {
                values.put(COLUMN_NAME_STATUS, BaseDb.STATUS_SYNCED);
                status = BaseDb.STATUS_SYNCED;
            }
            values.put(COLUMN_NAME_READ, sub.read);
            values.put(COLUMN_NAME_RECV, sub.recv);
            values.put(COLUMN_NAME_CLEAR, sub.clear);
            if (sub.seen != null) {
                if (sub.seen.when != null) {
                    values.put(COLUMN_NAME_LAST_SEEN, sub.seen.when.getTime());
                }
                if (sub.seen.ua != null) {
                    values.put(COLUMN_NAME_USER_AGENT, sub.seen.ua);
                }
            }

            updated = db.update(TABLE_NAME, values, _ID + "=" + ss.id, null);

            //Log.d(TAG, "Update row, accid=" + BaseDb.getInstance().getAccountId() +
            //        " name=" + sub.user + " returned " + updated);

            db.setTransactionSuccessful();

            ss.status = status;

        } catch (SQLException ignored) {
            Log.e(TAG, "Exception while updating subscription", ignored);
        }

        db.endTransaction();

        return updated > 0;
    }

    /**
     * Delete one subscription record
     */
    public static boolean delete(SQLiteDatabase db, long id) {
        return db.delete(TABLE_NAME, _ID + "=" + id, null) > 0;
    }

    /**
     * Delete all subscription records for the given topic
     */
    public static boolean deleteForTopic(SQLiteDatabase db, long topicId) {
        return db.delete(TABLE_NAME, COLUMN_NAME_TOPIC_ID + "=" + topicId, null) > 0;
    }

    /**
     * Get the next available senderId for the given topic. Min Id == 1.
     *
     * @param db      database
     * @param topicId _id of the topic to query
     * @return _id of the user
     */
    private static int getNextSenderIndex(SQLiteDatabase db, long topicId) {
        return (int) db.compileStatement("SELECT count(*) FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId).simpleQueryForLong() + 1;
    }

    protected static Cursor query(SQLiteDatabase db, long topicId) {
        return db.rawQuery("SELECT " +
                TABLE_NAME + "." + _ID + "," +
                TABLE_NAME + "." + COLUMN_NAME_TOPIC_ID + "," +
                TABLE_NAME + "." + COLUMN_NAME_USER_ID + "," +
                TABLE_NAME + "." + COLUMN_NAME_STATUS + "," +
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
                TopicDb.TABLE_NAME + "." + TopicDb.COLUMN_NAME_SEQ +
                " FROM " + TABLE_NAME +
                " LEFT JOIN " + UserDb.TABLE_NAME +
                " ON " + COLUMN_NAME_USER_ID + "=" + UserDb.TABLE_NAME + "." + UserDb._ID +
                " LEFT JOIN " + TopicDb.TABLE_NAME +
                " ON " + COLUMN_NAME_TOPIC_ID + "=" + TopicDb.TABLE_NAME + "." + TopicDb._ID +
                " WHERE " + COLUMN_NAME_TOPIC_ID + "=" + topicId, null);

    }

    public static Subscription readOne(Cursor c) {
        // StoredSub part
        StoredSubscription ss = new StoredSubscription();
        ss.id = c.getLong(COLUMN_IDX_ID);
        ss.topicId = c.getLong(COLUMN_IDX_TOPIC_ID);
        ss.userId = c.getLong(COLUMN_IDX_USER_ID);
        ss.status = c.getInt(COLUMN_IDX_STATUS);

        // Subscription part
        Subscription s = new Subscription();
        // From subs table
        s.acs = BaseDb.deserializeMode(c.getString(COLUMN_IDX_MODE));
        s.updated = new Date(c.getLong(COLUMN_IDX_UPDATED));
        s.read = c.getInt(COLUMN_IDX_READ);
        s.recv = c.getInt(COLUMN_IDX_RECV);
        s.clear = c.getInt(COLUMN_IDX_CLEAR);
        s.seen = new LastSeen(
                new Date(c.getLong(COLUMN_IDX_LAST_SEEN)),
                c.getString(COLUMN_IDX_USER_AGENT)
        );

        // From user table
        s.user = c.getString(JOIN_USER_COLUMN_IDX_UID);
        s.pub = BaseDb.deserialize(c.getString(JOIN_USER_COLUMN_IDX_PUBLIC));

        // From topic table
        s.topic = c.getString(JOIN_TOPIC_COLUMN_IDX_TOPIC);
        s.seq = c.getInt(JOIN_TOPIC_COLUMN_IDX_SEQ);

        s.setLocal(ss);

        return s;
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

    public static boolean updateRead(SQLiteDatabase db, long topicId, int read) {
        return BaseDb.updateCounter(db, TABLE_NAME, COLUMN_NAME_READ, topicId, read);
    }

    public static boolean updateRecv(SQLiteDatabase db, long topicId, int recv) {
        return BaseDb.updateCounter(db, TABLE_NAME, COLUMN_NAME_RECV, topicId, recv);
    }
}
