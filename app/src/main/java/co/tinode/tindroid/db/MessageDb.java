package co.tinode.tindroid.db;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import java.util.Date;

import co.tinode.tinodesdk.model.MsgServerData;

/**
 * Storage structure for messages:
 * public String id -- not stored
 * public String topic -> as topic_id
 * public String from; -> as user_id
 * public Map head -- not stored yet
 * public Date ts;
 * public int seq;
 * public T content;
 */
public class MessageDb implements BaseColumns {
    private static final String TAG = "MessageDb";

    /**
     * Content provider authority.
     */
    public static final String CONTENT_AUTHORITY = "co.tinode.tindroid";

    /**
     * Base URI. (content://co.tinode.tindroid)
     */
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    /**
     * MIME type for lists of messages
     */
    public static final String CONTENT_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.tinode.im-msg";
    /**
     * MIME type for individual messages
     */
    public static final String CONTENT_ITEM_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.tinode.im-msg";
    /**
     * The name of the main table.
     */
    public static final String TABLE_NAME = "messages";
    /**
     * The name of index: messages by topic and sequence.
     */
    public static final String INDEX_NAME = "message_topic_id_seq";
    /**
     * Topic ID, references topics._ID
     */
    public static final String COLUMN_NAME_TOPIC_ID = "topic_id";
    /**
     * Id of the originator of the message, references users._ID
     */
    public static final String COLUMN_NAME_USER_ID = "user_id";
    /**
     * Sequential ID of the sender within the topic. Needed for assigning colors to message bubbles in UI.
     */
    public static final String COLUMN_NAME_SENDER_INDEX = "sender_idx";
    /**
     * Message timestamp
     */
    public static final String COLUMN_NAME_TS = "ts";
    /**
     * Server-issued sequence ID, integer, indexed
     */
    public static final String COLUMN_NAME_SEQ = "seq";
    /**
     * Serialized message content
     */
    public static final String COLUMN_NAME_CONTENT = "content";
    /**
     * Path component for "message"-type resources..
     */
    private static final String PATH_MESSAGES = "messages";
    /**
     * URI for "messages" resource.
     */
    public static final Uri CONTENT_URI =
            BASE_CONTENT_URI.buildUpon().appendPath(PATH_MESSAGES).build();

    private static final int COLUMN_IDX_TOPIC_ID = 1;
    private static final int COLUMN_IDX_USER_ID = 2;
    private static final int COLUMN_IDX_SENDER_INDEX = 3;
    private static final int COLUMN_IDX_TS = 4;
    private static final int COLUMN_IDX_SEQ = 5;
    private static final int COLUMN_IDX_CONTENT = 6;

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
                    COLUMN_NAME_TS + " TEXT," +
                    COLUMN_NAME_SEQ + " INT," +
                    COLUMN_NAME_CONTENT + " BLOB)";
    /**
     * Add index on account_id-topic-seq, in descending order
     */
    static final String CREATE_INDEX =
            "CREATE UNIQUE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_SEQ + " DESC)";

    /**
     * SQL statement to drop Messages table.
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
    public static long insert(SQLiteDatabase db, StoredMessage msg) {
        long id = -1;
        try {
            db.beginTransaction();

            if (msg.topicId <= 0) {
                msg.topicId = TopicDb.getId(db, msg.topic);
            }
            if (msg.userId <= 0) {
                msg.userId = UserDb.getId(db, msg.from);
            }

            if (msg.userId <=0 || msg.topicId <= 0) {
                return -1;
            }

            if (msg.senderIdx < 0) {
                msg.senderIdx = SubscriberDb.getSenderIndex(db, msg.topicId, msg.userId);
            }

            // Convert message to a map of values
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TOPIC_ID, msg.topicId);
            values.put(COLUMN_NAME_USER_ID, msg.userId);
            values.put(COLUMN_NAME_SENDER_INDEX, msg.senderIdx);
            values.put(COLUMN_NAME_TS, msg.ts.getTime());
            values.put(COLUMN_NAME_SEQ, msg.seq);
            values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(msg.content));

            id = db.insert(TABLE_NAME, null, values);
            db.setTransactionSuccessful();

        } catch (Exception ex) {
            db.endTransaction();
        }

        return id;
    }

    /**
     * Query messages. To select all messages set <b>from</b> and <b>to</b> equal to -1.
     *
     * @param db    database to select from;
     * @param topicId Tinode topic ID (topics._id) to select from
     * @param from  minimum seq value to select, exclusive
     * @param to    maximum seq value to select, inclusive
     * @return cursor with the messages
     */
    public static Cursor query(SQLiteDatabase db, long topicId, int from, int to) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_TOPIC_ID + "=" + topicId +
                (from > 0 ? " AND " + COLUMN_NAME_SEQ + ">" + from : "") +
                (to > 0 ?  " AND " + COLUMN_NAME_SEQ + "<=" + to : "") +
                " ORDER BY " + COLUMN_NAME_TS;
        Log.d(TAG, "Sql=[" + sql + "]");
        
        return db.rawQuery(sql, null);
    }

    /**
     * Delete messages between 'from' and 'to'. To delete all messages make from and to equal to -1.
     *
     * @param db    Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @param before maximum seq value to delete, inclusive.
     * @param soft  mark messages as deleted but do not actually delete them
     * @return number of deleted messages
     */
    public static boolean delete(SQLiteDatabase db, long topicId, int before, boolean soft) {
        if (!soft) {
            return db.delete(TABLE_NAME, COLUMN_NAME_TOPIC_ID + "=" + topicId +
                    " AND " + COLUMN_NAME_SEQ + "<=" + before, null) > 0;
        } else {
            return TopicDb.updateClear(db, topicId, before);
        }
    }

    public static <T> StoredMessage<T> readMessage(Cursor c) {
        StoredMessage<T> msg = new StoredMessage<>();

        msg.topicId     = c.getLong(COLUMN_IDX_TOPIC_ID);
        msg.userId      = c.getLong(COLUMN_IDX_USER_ID);
        msg.senderIdx   = c.getInt(COLUMN_IDX_SENDER_INDEX);
        msg.seq         = c.getInt(COLUMN_IDX_SEQ);
        msg.ts          = new Date(c.getLong(COLUMN_IDX_TS));
        msg.content     = BaseDb.deserialize(c.getBlob(COLUMN_IDX_CONTENT));

        return msg;
    }

    /**
     * Get locally-unique ID of the message (content of _ID field).
     *
     * @param cursor Cursor to query
     * @return _id of the message at the current position.
     */
    public static long getLocalId(Cursor cursor) {
        return cursor.getLong(0);
    }

    public static class Loader extends AsyncTaskLoader<Cursor> {
        SQLiteDatabase mDb;
        private Cursor mCursor;

        private long topicId;
        private int fromSeq;
        private int toSeq;

        public Loader(Context context, String account, String topic, int from, int to) {
            super(context);

            mDb = BaseDb.getInstance(context, account).getReadableDatabase();
            this.topicId = TopicDb.getId(mDb, topic);
            this.fromSeq = from;
            this.toSeq = to;
        }

        @Override
        public Cursor loadInBackground() {
            return query(mDb, topicId, fromSeq, toSeq);
        }

        /* Runs on the UI thread */
        @Override
        public void deliverResult(Cursor cursor) {
            if (isReset()) {
                // An async query came in while the loader is stopped
                if (cursor != null) {
                    cursor.close();
                }
                return;
            }
            Cursor oldCursor = mCursor;
            mCursor = cursor;

            if (isStarted()) {
                super.deliverResult(cursor);
            }

            if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed()) {
                oldCursor.close();
            }
        }

        /**
         * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
         * will be called on the UI thread. If a previous load has been completed and is still valid
         * the result may be passed to the callbacks immediately.
         * <p/>
         * Must be called from the UI thread
         */
        @Override
        protected void onStartLoading() {
            if (mCursor != null) {
                deliverResult(mCursor);
            }
            if (takeContentChanged() || mCursor == null) {
                forceLoad();
            }
        }

        /**
         * Must be called from the UI thread
         */
        @Override
        protected void onStopLoading() {
            // Attempt to cancel the current load task if possible.
            cancelLoad();
        }

        @Override
        public void onCanceled(Cursor cursor) {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        @Override
        protected void onReset() {
            super.onReset();

            // Ensure the loader is stopped
            onStopLoading();

            if (mCursor != null && !mCursor.isClosed()) {
                mCursor.close();
            }
            mCursor = null;
        }
    }
}
