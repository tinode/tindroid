package co.tinode.tindroid.db;

import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * SQLite backend. Persistent store for contacts and messages.
 */
public class BaseDb  extends SQLiteOpenHelper {
    /** Schema version. */
    public static final int DATABASE_VERSION = 8;
    /** Filename for SQLite file. */
    public static final String DATABASE_NAME = "base.db";

    /** SQL statement to create Messages table */
    private static final String CREATE_MESSAGES_TABLE =
            "CREATE TABLE " + Message.TABLE_NAME + " (" +
                    Message._ID + " INTEGER PRIMARY KEY," +
                    Message.COLUMN_NAME_TOPIC + " TEXT," +
                    Message.COLUMN_NAME_FROM + " TEXT," +
                    Message.COLUMN_NAME_TS + " TEXT," +
                    Message.COLUMN_NAME_SEQ + " INT," +
                    Message.COLUMN_NAME_TS + " TEXT," +
                    Message.COLUMN_NAME_CONTENT + " TEXT)";
    /** Add index on topic-seq, in descending order */
    private static final String CREATE_MESSAGES_INDEX =
            "CREATE UNIQUE INDEX "+ Message.INDEX_NAME +
                    " ON " + Message.TABLE_NAME + " (" +
                    Message.COLUMN_NAME_TOPIC + "," +
                    Message.COLUMN_NAME_SEQ + " DESC)";

    /** SQL statement to drop Messages table. */
    private static final String DROP_MESSAGES_TABLE =
            "DROP TABLE IF EXISTS " + Message.TABLE_NAME;

    /** Drop the index too */
    private static final String DROP_MESSAGES_INDEX =
            "DROP INDEX IF EXISTS " + Message.INDEX_NAME;

    public BaseDb(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_MESSAGES_TABLE);
        db.execSQL(CREATE_MESSAGES_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This is just a cache. Drop then refetch everything from the server.
        db.execSQL(DROP_MESSAGES_INDEX);
        db.execSQL(DROP_MESSAGES_TABLE);
        onCreate(db);
    }

    /**
     * Storage structure for messages:
     *  public String id -- not stored
     *  public String topic
     *  public String from;
     *  public Date ts;
     *  public int seq;
     *  public T content;
     */
    public static class Message implements BaseColumns {
        /**
         * Content provider authority.
         */
        public static final String CONTENT_AUTHORITY = "co.tinode.tindroid";

        /**
         * Base URI. (content://co.tinode.tindroid)
         */
        public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

        /** MIME type for lists of messages */
        public static final String CONTENT_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.tinode.im-msg";
        /** MIME type for individual messages */
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
        /**
         * UID as string, originator of the message
         */
        public static final String COLUMN_NAME_FROM = "from";
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
    }
}
