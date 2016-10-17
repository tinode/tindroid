package co.tinode.tindroid.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite backend. Persistent store for messages and chats.
 */
public class BaseDb  extends SQLiteOpenHelper {
    private static BaseDb mInstance = null;

    /** Schema version. */
    public static final int DATABASE_VERSION = 8;
    /** Filename for SQLite file. */
    public static final String DATABASE_NAME = "base.db";

    /** SQL statement to create Messages table */
    private static final String CREATE_MESSAGES_TABLE =
            "CREATE TABLE " + MessageDb.TABLE_NAME + " (" +
                    MessageDb._ID + " INTEGER PRIMARY KEY," +
                    MessageDb.COLUMN_NAME_TOPIC + " TEXT," +
                    MessageDb.COLUMN_NAME_FROM + " TEXT," +
                    MessageDb.COLUMN_NAME_TS + " TEXT," +
                    MessageDb.COLUMN_NAME_SEQ + " INT," +
                    MessageDb.COLUMN_NAME_CONTENT + " BLOB)";
    /** Add index on topic-seq, in descending order */
    private static final String CREATE_MESSAGES_INDEX =
            "CREATE UNIQUE INDEX "+ MessageDb.INDEX_NAME +
                    " ON " + MessageDb.TABLE_NAME + " (" +
                    MessageDb.COLUMN_NAME_TOPIC + "," +
                    MessageDb.COLUMN_NAME_SEQ + " DESC)";

    /** SQL statement to drop Messages table. */
    private static final String DROP_MESSAGES_TABLE =
            "DROP TABLE IF EXISTS " + MessageDb.TABLE_NAME;

    /** Drop the index too */
    private static final String DROP_MESSAGES_INDEX =
            "DROP INDEX IF EXISTS " + MessageDb.INDEX_NAME;

    private BaseDb(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static BaseDb getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new BaseDb(context);
        }

        return mInstance;
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
