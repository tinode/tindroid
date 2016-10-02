package co.tinode.tindroid.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite backend. Persistent store for messages and chats.
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
                    Message.COLUMN_NAME_CONTENT + " BLOB)";
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
}
