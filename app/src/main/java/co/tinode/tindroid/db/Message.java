package co.tinode.tindroid.db;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.content.AsyncTaskLoader;
import android.util.SparseArray;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Date;

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.model.MsgServerData;

/**
 * Storage structure for messages:
 *  public String id -- not stored
 *  public String topic
 *  public String from;
 *  public Date ts;
 *  public int seq;
 *  public T content;
 */
public class Message implements BaseColumns {
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
     * Path component for "message"-type resources..
     */
    private static final String PATH_MESSAGES = "messages";

    /**
     * URI for "messages" resource.
     */
    public static final Uri CONTENT_URI =
            BASE_CONTENT_URI.buildUpon().appendPath(PATH_MESSAGES).build();

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
    private static final int COLUMN_IDX_TOPIC = 1;
    /**
     * UID as string, originator of the message
     */
    public static final String COLUMN_NAME_FROM = "from";
    private static final int COLUMN_IDX_FROM = 2;
    /**
     * Message timestamp
     */
    public static final String COLUMN_NAME_TS = "ts";
    private static final int COLUMN_IDX_TS = 3;
    /**
     * Server-issued sequence ID, integer, indexed
     */
    public static final String COLUMN_NAME_SEQ = "seq";
    private static final int COLUMN_IDX_SEQ = 4;
    /**
     * Serialized message content
     */
    public static final String COLUMN_NAME_CONTENT = "content";
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
                sortOrder       // sort by seq, ASC or DESC
        );
    }

    /**
     * Read a single message from the provided cursor.
     *
     * @param c Cursor to read from
     */
    public static MsgServerData readMessage(Cursor c) {
        MsgServerData msg = new MsgServerData();
        msg.topic = c.getString(COLUMN_IDX_TOPIC);
        msg.from = c.getString(COLUMN_IDX_FROM);
        msg.ts = new Date(c.getLong(COLUMN_IDX_TS));
        msg.seq = c.getInt(COLUMN_IDX_SEQ);
        msg.content = deserialize(c.getBlob(COLUMN_IDX_CONTENT));
        return msg;
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

    private static Object deserialize(byte[] bytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInput objin = null;
        try {
            objin = new ObjectInputStream(bais);
            return objin.readObject();
        } catch (IOException | ClassNotFoundException ignored) {
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
        private SQLiteDatabase db;
        private String topic;
        private int from;
        private int to;

        public Loader(Context context, String topic, int from, int to) {
            super(context);
            this.db = new BaseDb(context).getReadableDatabase();
            this.topic = topic;
            this.from = from;
            this.to = to;
        }

        @Override
        public Cursor loadInBackground() {
            return query(db, topic, from, to);
        }
    }
}
