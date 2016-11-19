package co.tinode.tindroid.db;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.Date;

import co.tinode.tinodesdk.model.MsgServerData;

/**
 * Storage structure for messages:
 * public String id -- not stored
 * public String topic
 * public String from;
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
     * UID as string, originator of the message. The column cannot be named 'from'.
     */
    public static final String COLUMN_NAME_FROM = "sender";
    /**
     * Sequential ID of the sender within the topic. Neede for color selection.
     */
    public static final String COLUMN_NAME_FROM_ID = "sender_id";
    /**
     * MessageDb timestamp
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
    private static final int COLUMN_IDX_FROM = 2;
    private static int COLUMN_IDX_FROM_ID = 3;
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
                    COLUMN_NAME_FROM + " TEXT," +
                    COLUMN_NAME_FROM_ID + " INT," +
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
    public static long insert(SQLiteDatabase db, long topicId, MsgServerData msg) {
        // Convert message to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_TOPIC_ID, topicId);
        values.put(COLUMN_NAME_FROM, msg.from);
        values.put(COLUMN_NAME_FROM_ID, fromId);
        values.put(COLUMN_NAME_TS, msg.ts.getTime());
        values.put(COLUMN_NAME_SEQ, msg.seq);
        values.put(COLUMN_NAME_CONTENT, BaseDb.serialize(msg.content));

        return db.insert(TABLE_NAME, null, values);
    }

    /**
     * Insert multiple messages into DB in one transaction.
     *
     * @return number of inserted messages
     */
    public static long insert(SQLiteDatabase db, long topicId, MsgServerData[] msgs) {
        int count = 0;
        try {
            db.beginTransaction();
            String insert = "INSERT INTO " + TABLE_NAME + "(" +
                    COLUMN_NAME_TOPIC_ID + "," +
                    COLUMN_NAME_FROM + "," +
                    COLUMN_NAME_TS + "," +
                    COLUMN_NAME_SEQ + "," +
                    COLUMN_NAME_CONTENT +
                    ") VALUES (?, ?, ?, ?, ?, ?)";

            SQLiteStatement stmt = db.compileStatement(insert);

            for (MsgServerData msg : msgs) {
                stmt.clearBindings();
                stmt.bindLong(1, topicId);
                if (msg.from != null) {
                    stmt.bindString(2, msg.from);
                } else {
                    stmt.bindNull(2);
                }
                stmt.bindLong(3, msg.ts.getTime());
                stmt.bindLong(4, msg.seq);
                stmt.bindBlob(5, BaseDb.serialize(msg.content));
                stmt.executeInsert();

                count++;
            }

            db.setTransactionSuccessful(); // This commits the transaction if there were no exceptions

        } catch (Exception e) {
            Log.w("Exception:", e);
            count = -1;
        } finally {
            db.endTransaction();
        }
        return count;
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
                " ORDER BY " + COLUMN_NAME_SEQ;
        Log.d(TAG, "Sql=[" + sql + "]");
        
        return db.rawQuery(sql, null);
    }

    /**
     * Delete one message
     *
     * @param db    Database to use.
     * @param topicId Tinode topic ID to delete message from.
     * @param seq   seq value to delete.
     * @return number of deleted messages
     */
    public static int delete(SQLiteDatabase db, long topicId, int seq) {
        // Define 'where' part of query.
        String selection =
                COLUMN_NAME_TOPIC_ID + "=? AND " +
                COLUMN_NAME_SEQ + "=?";
        String[] selectionArgs = {
                String.valueOf(topicId),
                String.valueOf(seq)
        };

        return db.delete(TABLE_NAME, selection, selectionArgs);
    }

    /**
     * Delete messages between 'from' and 'to'. To delete all messages make from and to equal to -1.
     *
     * @param db    Database to use.
     * @param topicId Tinode topic ID to delete messages from.
     * @param from  minimum seq value to delete from (exclusive).
     * @param to    maximum seq value to delete, inclusive.
     * @return number of deleted messages
     */
    public static int delete(SQLiteDatabase db, long topicId, int from, int to) {
        to = (to < 0 ? Integer.MAX_VALUE : to);

        String selection =
                COLUMN_NAME_TOPIC_ID + "=? AND " +
                COLUMN_NAME_SEQ + ">? AND " +
                COLUMN_NAME_SEQ + "<=?";
        String[] selectionArgs = {
                String.valueOf(topicId),
                String.valueOf(from),
                String.valueOf(to)
        };

        return db.delete(TABLE_NAME, selection, selectionArgs);
    }

    public static <T> MsgServerData<T> readMessage(Cursor cursor) {
        MsgServerData<T> msg = new MsgServerData<>();

        msg.from = cursor.getString(COLUMN_IDX_FROM);
        msg.seq = cursor.getInt(COLUMN_IDX_SEQ);
        msg.ts = new Date(cursor.getLong(COLUMN_IDX_TS));
        msg.content = BaseDb.deserialize(cursor.getBlob(COLUMN_IDX_CONTENT));

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

    /**
     * Get maximum SeqId for the given table.
     *
     * @param db    Database to use
     * @param topicId ID of the Tinode topic to query.
     * @return maximum seq id.
     */
    public static long getMaxSeq(SQLiteDatabase db, int topicId) {
        SQLiteStatement stmt = db.compileStatement(
                "SELECT MAX(" + COLUMN_NAME_SEQ + ") " +
                        "FROM " + TABLE_NAME +
                        " WHERE " +
                        COLUMN_NAME_TOPIC_ID + "=?");

        stmt.bindLong(1, topicId);

        long count;
        try {
            count = stmt.simpleQueryForLong();
        } catch (SQLiteDoneException ignored) {
            count = 0;
        }
        return count;
    }

    public static class Loader extends AsyncTaskLoader<Cursor> {
        SQLiteDatabase mDb;
        private Cursor mCursor;

        private long topicId;
        private int from;
        private int to;

        public Loader(Context context, String account, String topic, int from, int to) {
            super(context);

            mDb = BaseDb.getInstance(context, account).getReadableDatabase();
            this.topicId = TopicDb.getTopicId(mDb, topic);
            this.from = from;
            this.to = to;
        }

        @Override
        public Cursor loadInBackground() {
            return query(mDb, topicId, from, to);
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
