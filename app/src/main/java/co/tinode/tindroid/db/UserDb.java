package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;

import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Local cash of known users
 */

public class UserDb implements BaseColumns {
    private static final String TAG = "UserDb";

    /**
     * The name of the main table.
     */
    public static final String TABLE_NAME = "users";
    /**
     * The name of index: topic by account id and topic name.
     */
    public static final String INDEX_NAME = "user_account_name";
    /**
     * Account ID, references accounts._ID
     */
    public static final String COLUMN_NAME_ACCOUNT_ID = "account_id";
    /**
     * Topic name, indexed
     */
    public static final String COLUMN_NAME_UID = "uid";
    /**
     * When the user was updated
     */
    public static final String COLUMN_NAME_UPDATED = "updated";
    /**
     * When the user was deleted
     */
    public static final String COLUMN_NAME_DELETED = "deleted";
    /**
     * Public user description, blob
     */
    public static final String COLUMN_NAME_PUBLIC = "pub";


    private static final int COLUMN_IDX_ID = 0;
    private static final int COLUMN_IDX_ACCOUNT_ID = 1;
    private static final int COLUMN_IDX_UID = 2;
    private static final int COLUMN_IDX_UPDATED = 3;
    private static final int COLUMN_IDX_DELETED = 4;
    private static final int COLUMN_IDX_PUBLIC = 5;

    /**
     * SQL statement to create Messages table
     */
    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_ACCOUNT_ID
                    + " REFERENCES " + AccountDb.TABLE_NAME + "(" + AccountDb._ID + ")," +
                    COLUMN_NAME_UID + " TEXT," +
                    COLUMN_NAME_UPDATED + " INT," +
                    COLUMN_NAME_DELETED + " INT," +
                    COLUMN_NAME_PUBLIC + " BLOB)";
    /**
     * Add index on account_id-topic name, in descending order
     */
    static final String CREATE_INDEX =
            "CREATE UNIQUE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_ACCOUNT_ID + "," + COLUMN_NAME_UID + ")";

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
     * Save user to DB
     *
     * @return ID of the newly added user
     */
    public static long insert(SQLiteDatabase db, Subscription sub) {
        Log.d(TAG, "Inserting sub for " + sub.user);

        // Convert subscription description to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_ACCOUNT_ID, BaseDb.getAccountId());
        values.put(COLUMN_NAME_UID, sub.user);
        values.put(COLUMN_NAME_UPDATED, sub.updated.getTime());
        // values.put(COLUMN_NAME_DELETED, NULL);
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(sub.pub));
        return db.insert(TABLE_NAME, null, values);
    }

    /**
     * Update user record
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, Subscription sub) {

        // Convert topic description to a map of values
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_UPDATED, sub.updated.getTime());
        // values.put(COLUMN_NAME_DELETED, NULL);
        values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(sub.pub));
        int updated = db.update(TABLE_NAME, values,
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() +
                        " AND " + COLUMN_NAME_UID + "='" + sub.user + "'",
                null);

        Log.d(TAG, "Update row, accid=" + BaseDb.getAccountId() + " name=" + sub.user + " returned " + updated);

        return updated > 0;
    }

    /**
     * Save or update a topic
     *
     * @return Id of the newly inserted user or 0 if the user was updated
     */
    public static long upsert(SQLiteDatabase db, Subscription sub) {
        if (!update(db, sub)) {
            return insert(db, sub);
        }
        return 0;
    }

    /**
     * Given UID, get it's database _id
     *
     * @param db database
     * @param uid UID
     * @return _id of the user
     */
    public static long getId(SQLiteDatabase db, String uid) {
        return db.compileStatement("SELECT " + _ID + " FROM " + TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getAccountId() + " AND " +
                COLUMN_NAME_UID + "='" + uid + "'").simpleQueryForLong();
    }
}
