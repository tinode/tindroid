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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
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
    public static final String TABLE_NAME = "message";
    /**
     * The name of index: messages by topic and sequence.
     */
    public static final String INDEX_NAME = "message_topic_seq";
    /**
     * Topic name, indexed
     */
    public static final String COLUMN_NAME_TOPIC = "topic";
    /**
     * UID as string, originator of the message. Cannot be named 'from'.
     */
    public static final String COLUMN_NAME_FROM = "sender";
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
    private static final int COLUMN_IDX_TOPIC = 1;
    private static final int COLUMN_IDX_FROM = 2;
    private static final int COLUMN_IDX_TS = 3;
    private static final int COLUMN_IDX_SEQ = 4;
    private static final int COLUMN_IDX_CONTENT = 5;

    /**
     * Save message to DB
     *
     * @return ID of the newly added message
     */
    public static long insert(SQLiteDatabase db, MsgServerData msg) {
        // Convert message to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_TOPIC, msg.topic);
        values.put(COLUMN_NAME_FROM, msg.from);
        values.put(COLUMN_NAME_TS, msg.ts.getTime());
        values.put(COLUMN_NAME_SEQ, msg.seq);
        values.put(COLUMN_NAME_CONTENT, serialize(msg.content));

        return db.insert(TABLE_NAME, null, values);
    }

    /**
     * Insert multiple messages into DB in one transaction.
     *
     * @return number of inserted messages
     */
    public static long insert(SQLiteDatabase db, MsgServerData[] msgs) {
        int count = 0;
        try {
            db.beginTransaction();
            String insert = "INSERT INTO " + TABLE_NAME + "(" +
                    COLUMN_NAME_TOPIC + "," +
                    COLUMN_NAME_FROM + "," +
                    COLUMN_NAME_TS + "," +
                    COLUMN_NAME_SEQ + "," +
                    COLUMN_NAME_CONTENT +
                    ") VALUES (?, ?, ?, ?, ?)";

            SQLiteStatement stmt = db.compileStatement(insert);

            for (MsgServerData msg : msgs) {
                stmt.clearBindings();
                stmt.bindString(1, msg.topic);
                if (msg.from != null) {
                    stmt.bindString(2, msg.from);
                } else {
                    stmt.bindNull(2);
                }
                stmt.bindLong(3, msg.ts.getTime());
                stmt.bindLong(4, msg.seq);
                stmt.bindBlob(5, serialize(msg.content));
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
     * Query messages. To select all messages make from and to equal to -1.
     *
     * @param db    database to select from;
     * @param topic Tinode topic to select from
     * @param from  minimum seq value to select, exclusive
     * @param to    maximum seq value to select, inclusive
     * @return cursor with the messages
     */
    public static Cursor query(SQLiteDatabase db, String topic, int from, int to) {

        to = (to < 0 ? Integer.MAX_VALUE : to);

        // Filter results by topic and sequence
        String selection =
                COLUMN_NAME_TOPIC + "=? AND " +
                        COLUMN_NAME_SEQ + ">? AND " +
                        COLUMN_NAME_SEQ + "<=?";
        String[] selectionArgs = {
                topic,
                String.valueOf(from),
                String.valueOf(to)
        };

        // How you want the results sorted in the resulting Cursor
        String sortOrder = COLUMN_NAME_SEQ;

        return db.query(
                TABLE_NAME,     // The table to query
                null,           // Return all columns
                selection,      // The columns for the WHERE clause
                selectionArgs,  // The values for the WHERE clause
                null,           // no GROUP BY
                null,           // no HAVING
                sortOrder       // sort by seq, ASC
        );
    }

    /**
     * Delete one message
     *
     * @param db    Database to use.
     * @param topic Tinode topic to delete message from.
     * @param seq   seq value to delete.
     * @return number of deleted messages
     */
    public static int deleteMsg(SQLiteDatabase db, String topic, int seq) {
        // Define 'where' part of query.
        String selection = COLUMN_NAME_TOPIC + "=? AND " +
                COLUMN_NAME_SEQ + "=?";
        String[] selectionArgs = {
                topic, String.valueOf(seq)
        };

        return db.delete(TABLE_NAME, selection, selectionArgs);
    }

    /**
     * Delete messages between 'from' and 'to'. To delete all messages make from and to equal to -1.
     *
     * @param db    Database to use.
     * @param topic Tinode topic to delete messages from.
     * @param from  minimum seq value to delete from (exclusive).
     * @param to    maximum seq value to delete, inclusive.
     * @return number of deleted messages
     */
    public static int deleteMsg(SQLiteDatabase db, String topic, int from, int to) {
        to = (to < 0 ? Integer.MAX_VALUE : to);

        String selection = COLUMN_NAME_TOPIC + "=? AND " +
                COLUMN_NAME_SEQ + ">? AND " +
                COLUMN_NAME_SEQ + "<=?";
        String[] selectionArgs = {
                topic, String.valueOf(from), String.valueOf(to)
        };

        return db.delete(TABLE_NAME, selection, selectionArgs);
    }

    public static <T> MsgServerData<T> readMessage(Cursor cursor) {
        MsgServerData<T> msg = new MsgServerData<>();

        msg.topic = cursor.getString(COLUMN_IDX_TOPIC);
        msg.from = cursor.getString(COLUMN_IDX_FROM);
        msg.seq = cursor.getInt(COLUMN_IDX_SEQ);
        msg.ts = new Date(cursor.getLong(COLUMN_IDX_TS));
        msg.content = deserialize(cursor.getBlob(COLUMN_IDX_CONTENT));

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
     * @param topic Tinode topic to query.
     * @return maximum seq id.
     */
    public static long getMaxSeq(SQLiteDatabase db, String topic) {
        SQLiteStatement stmt = db.compileStatement(
                "SELECT MAX(" + COLUMN_NAME_SEQ + ")" +
                        " FROM " + TABLE_NAME +
                        " WHERE " + COLUMN_NAME_TOPIC + "='" + topic + "'");
        long count;
        try {
            count = stmt.simpleQueryForLong();
        } catch (SQLiteDoneException ignored) {
            count = 0;
        }
        return count;
    }

    private static byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutput objout = null;
        try {
            objout = new ObjectOutputStream(baos);
            objout.writeObject(obj);
            objout.flush();
            return baos.toByteArray();
        } catch (IOException ignored) {
        } finally {
            try {
                baos.close();
                if (objout != null) {
                    objout.close();
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static <T> T deserialize(byte[] bytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInput objin = null;
        try {
            objin = new ObjectInputStream(bais);
            return (T) objin.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException ignored) {
        } finally {
            try {
                bais.close();
                if (objin != null) {
                    objin.close();
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    public static class Loader extends AsyncTaskLoader<Cursor> {
        private Cursor mCursor;

        private String topic;
        private int from;
        private int to;

        public Loader(Context context, String topic, int from, int to) {
            super(context);
            this.topic = topic;
            this.from = from;
            this.to = to;
        }

        @Override
        public Cursor loadInBackground() {
            SQLiteDatabase db = BaseDb.getInstance(getContext()).getReadableDatabase();
            return query(db, topic, from, to);
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
