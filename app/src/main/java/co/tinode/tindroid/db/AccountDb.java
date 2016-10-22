package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;

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
    public static final String COLUMN_NAME_ACTIVE = "active";

    public static final String INDEX_NAME = "accounts_name";

    public static long addAccount(SQLiteDatabase db, String name) {
        long id = -1;
        db.beginTransaction();
        try {
            // Clear active for all accounts
            db.execSQL("UPDATE " + TABLE_NAME + " SET " + COLUMN_NAME_ACTIVE + "=0");
            ContentValues values = new ContentValues();
            values.put(AccountDb.COLUMN_NAME_ACCOUNT, name);
            values.put(AccountDb.COLUMN_LAST_USED, new Date().getTime());
            values.put(AccountDb.COLUMN_NAME_ACTIVE, 1);
            id = db.insert(AccountDb.TABLE_NAME, null, values);
            db.setTransactionSuccessful();
        } catch (SQLException ignored) {}
        db.endTransaction();
        return id;
    }

    public static long getActiveAccountId(SQLiteDatabase db) {
        SQLiteStatement stmt = db.compileStatement(
                "SELECT " + _ID +
                        "FROM " + TABLE_NAME +
                        " WHERE " +
                        COLUMN_NAME_ACTIVE + "=1");

        long id;
        try {
            id = stmt.simpleQueryForLong();
        } catch (SQLiteDoneException ignored) {
            id = -1;
        }
        return id;
    }

    public static long getAccountId(SQLiteDatabase db, String name) {
        // If the account is already in the db, return it.
        Cursor c = db.query(
                AccountDb.TABLE_NAME,
                new String[] {AccountDb._ID},
                AccountDb.COLUMN_NAME_ACCOUNT + "=?",
                new String[] {name},
                null, null, null);
        if (c.moveToFirst()) {
            return c.getLong(0);
        }
        return -1;
    }
}
