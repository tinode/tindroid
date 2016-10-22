package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * SQLite backend. Persistent store for messages and chats.
 */
public class BaseDb extends SQLiteOpenHelper {
    /**
     * Schema version.
     */
    public static final int DATABASE_VERSION = 9;
    /**
     * Filename for SQLite file.
     */
    public static final String DATABASE_NAME = "base.db";

    /**
     * Statement to create account table - mapping of account UID to long id
     */
    private static final String CREATE_ACCOUNTS_TABLE =
            "CREATE TABLE " + AccountDb.TABLE_NAME + " (" +
                    AccountDb._ID + " INTEGER PRIMARY KEY," +
                    AccountDb.COLUMN_NAME_ACCOUNT + " TEXT," +
                    AccountDb.COLUMN_LAST_USED + " INTEGER," +
                    AccountDb.COLUMN_NAME_ACTIVE + " INTEGER)";

    /**
     * Statements to drop accounts table and index
     */
    private static final String DROP_ACCOUNTS_TABLE =
            "DROP TABLE IF EXISTS " + AccountDb.TABLE_NAME;

    private static final String DROP_ACCOUNTS_INDEX =
            "DROP INDEX IF EXISTS " + AccountDb.INDEX_NAME;

    /**
     * Add index on account name
     */
    private static final String CREATE_ACCOUNTS_INDEX =
            "CREATE UNIQUE INDEX " + AccountDb.INDEX_NAME +
                    " ON " + AccountDb.TABLE_NAME + " (" +
                    AccountDb.COLUMN_NAME_ACCOUNT + ")";

    /**
     * SQL statement to create Messages table
     */
    private static final String CREATE_MESSAGES_TABLE =
            "CREATE TABLE " + MessageDb.TABLE_NAME + " (" +
                    MessageDb._ID + " INTEGER PRIMARY KEY," +
                    MessageDb.COLUMN_NAME_ACCOUNT_ID
                        + " REFERENCES " + AccountDb.TABLE_NAME + "(" + AccountDb._ID + ")," +
                    MessageDb.COLUMN_NAME_TOPIC + " TEXT," +
                    MessageDb.COLUMN_NAME_FROM + " TEXT," +
                    MessageDb.COLUMN_NAME_TS + " TEXT," +
                    MessageDb.COLUMN_NAME_SEQ + " INT," +
                    MessageDb.COLUMN_NAME_CONTENT + " BLOB)";
    /**
     * Add index on account_id-topic-seq, in descending order
     */
    private static final String CREATE_MESSAGES_INDEX =
            "CREATE UNIQUE INDEX " + MessageDb.INDEX_NAME +
                    " ON " + MessageDb.TABLE_NAME + " (" +
                    MessageDb.COLUMN_NAME_ACCOUNT_ID + "," +
                    MessageDb.COLUMN_NAME_TOPIC + "," +
                    MessageDb.COLUMN_NAME_SEQ + " DESC)";
    /**
     * SQL statement to drop Messages table.
     */
    private static final String DROP_MESSAGES_TABLE =
            "DROP TABLE IF EXISTS " + MessageDb.TABLE_NAME;
    /**
     * Drop the index too
     */
    private static final String DROP_MESSAGES_INDEX =
            "DROP INDEX IF EXISTS " + MessageDb.INDEX_NAME;

    private static BaseDb sInstance = null;

    private static long sAccountId = -1;

    private BaseDb(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static BaseDb getInstance(Context context, String accountName) {
        if (sInstance == null) {
            sInstance = new BaseDb(context);
            sAccountId = AccountDb.getAccountId(sInstance.getWritableDatabase(), accountName);
        }
        return sInstance;
    }

    public static long getAccountId() {
        return sAccountId;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_ACCOUNTS_TABLE);
        db.execSQL(CREATE_ACCOUNTS_INDEX);
        db.execSQL(CREATE_MESSAGES_TABLE);
        db.execSQL(CREATE_MESSAGES_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This is just a cache. Drop then refetch everything from the server.
        db.execSQL(DROP_MESSAGES_INDEX);
        db.execSQL(DROP_MESSAGES_TABLE);
        db.execSQL(DROP_ACCOUNTS_INDEX);
        db.execSQL(DROP_ACCOUNTS_TABLE);
        onCreate(db);
    }
}
