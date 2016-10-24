package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.text.TextUtils;

import java.util.Date;

/**
 * List of Tinode accounts.
 * Schema:
 *  _ID -- account ID
 *  name - UID of the account
 *  last_used -- timestamp when this account was last used
 *  active -- 1 or 0 to indicate that the last login with this account was successful
 */
public class AccountDb implements BaseColumns {
    public static final String TABLE_NAME = "accounts";
    public static final String COLUMN_NAME_ACCOUNT = "name";
    public static final String COLUMN_LAST_USED = "last_used";

    public static final String INDEX_NAME = "accounts_name";

    /**
     * Statement to create account table - mapping of account UID to long id
     */
    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_ACCOUNT + " TEXT," +
                    COLUMN_LAST_USED + " INTEGER)";
    /**
     * Add index on account name
     */
    static final String CREATE_INDEX =
            "CREATE UNIQUE INDEX " + INDEX_NAME +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_ACCOUNT + ")";

    /**
     * Statements to drop accounts table and index
     */
    static final String DROP_TABLE =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    static final String DROP_INDEX =
            "DROP INDEX IF EXISTS " + INDEX_NAME;


    public static long addAccount(SQLiteDatabase db, String name) {
        long id = -1;
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_ACCOUNT, name);
            values.put(COLUMN_LAST_USED, new Date().getTime());
            id = db.insert(TABLE_NAME, null, values);
            db.setTransactionSuccessful();
        } catch (SQLException ignored) {}
        db.endTransaction();
        return id;
    }

    /**
     * Get ID of an account associated with the given name. If the account is missing, create it.
     * @param db writable database
     * @param name name of the account to query
     * @return id of the account or -1
     */
    public static long getAccountId(SQLiteDatabase db, String name) {
        // If the account is already in the db, return it.
        long id = -1;
        if (!TextUtils.isEmpty(name)) {
            Cursor c = db.query(
                    TABLE_NAME,
                    new String[]{AccountDb._ID},
                    COLUMN_NAME_ACCOUNT + "=?",
                    new String[]{name},
                    null, null, null);
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
            c.close();

            if (id == -1) {
                id = addAccount(db, name);
            }
        }
        return id;
    }
}
