package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

/**
 * List of Tinode accounts.
 * Schema:
 *  _ID -- account ID
 *  name - UID of the account
 *  last_active -- 1 if the account was used for last login, 0 otherwise
 */
public class AccountDb implements BaseColumns {
    static final String TABLE_NAME = "accounts";
    private static final String COLUMN_NAME_UID = "uid";
    private static final String COLUMN_NAME_ACTIVE = "last_active";
    private static final String COLUMN_NAME_CRED_METHODS = "cred_methods";
    private static final String COLUMN_NAME_DEVICE_ID = "device_id";

    private static final String INDEX_UID = "accounts_uid";
    private static final String INDEX_ACTIVE = "accounts_active";

    /**
     * Statement to create account table - mapping of account UID to long id
     */
    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_UID + " TEXT," +
                    COLUMN_NAME_ACTIVE + " INTEGER," +
                    COLUMN_NAME_CRED_METHODS + " TEXT," +
                    COLUMN_NAME_DEVICE_ID + " TEXT)";
    /**
     * Add index on account name
     */
    static final String CREATE_INDEX_1 =
            "CREATE UNIQUE INDEX " + INDEX_UID +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_UID + ")";
    /**
     * Add index on last active
     */
    static final String CREATE_INDEX_2 =
            "CREATE INDEX " + INDEX_ACTIVE +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_ACTIVE + ")";

    /**
     * Statements to drop accounts table and index
     */
    static final String DROP_TABLE =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    static final String DROP_INDEX_1 =
            "DROP INDEX IF EXISTS " + INDEX_UID;

    static final String DROP_INDEX_2 =
            "DROP INDEX IF EXISTS " + INDEX_ACTIVE;

    static StoredAccount addOrActivateAccount(SQLiteDatabase db, String uid, String[] credMethods) {
        StoredAccount acc;
        db.beginTransaction();
        try {
            // Clear Last Active
            deactivateAll(db);
            acc = getByUid(db, uid);
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_ACTIVE, 1);
            values.put(COLUMN_NAME_CRED_METHODS, BaseDb.serializeStringArray(credMethods));
            if (acc != null) {
                // Account exists, updating active status and list of un-validated methods.
                db.update(TABLE_NAME, values, _ID + "=" + acc.id, null);
            } else {
                // Creating new record.
                acc = new StoredAccount();
                acc.uid = uid;
                values.put(COLUMN_NAME_UID, uid);
                // Insert new account as active
                acc.id = db.insert(TABLE_NAME, null, values);
            }
            if (acc.id < 0) {
                acc = null;
            } else {
                acc.credMethods = credMethods;
            }
            db.setTransactionSuccessful();
        } catch (SQLException ignored) {
            acc = null;
        } finally {
            db.endTransaction();
        }

        return acc;
    }

    static StoredAccount getActiveAccount(SQLiteDatabase db) {
        StoredAccount acc = null;
        Cursor c = db.query(
                TABLE_NAME,
                new String[]{_ID, COLUMN_NAME_UID, COLUMN_NAME_CRED_METHODS},
                COLUMN_NAME_ACTIVE + "=1",
                null, null, null, null);
        if (c.moveToFirst()) {
            acc = new StoredAccount();
            acc.id = c.getLong(0);
            acc.uid = c.getString(1);
            acc.credMethods = BaseDb.deserializeStringArray(c.getString(2));
        }
        c.close();
        return acc;
    }

    // Delete given account.
    static void delete(SQLiteDatabase db, StoredAccount acc) {
        TopicDb.deleteAll(db, acc.id);
        UserDb.deleteAll(db, acc.id);
        db.delete(TABLE_NAME, _ID + "=" + acc.id, null);
    }

    static StoredAccount getByUid(SQLiteDatabase db, String uid) {
        if (uid == null) {
            return null;
        }

        StoredAccount acc = null;
        Cursor c = db.query(
                TABLE_NAME,
                new String[]{ _ID, COLUMN_NAME_CRED_METHODS },
                COLUMN_NAME_UID + "=?",
                new String[] { uid },
                null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                acc = new StoredAccount();
                acc.id = c.getLong(0);
                acc.uid = uid;
                acc.credMethods = BaseDb.deserializeStringArray(c.getString(1));
            }
            c.close();
        }
        return acc;
    }

    static void deactivateAll(SQLiteDatabase db) {
        db.execSQL("UPDATE " + TABLE_NAME + " SET " + COLUMN_NAME_ACTIVE + "=0");
    }

    @SuppressWarnings("UnusedReturnValue")
    static boolean updateDeviceToken(SQLiteDatabase db, String token) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_DEVICE_ID, token);
        return db.update(TABLE_NAME, values, COLUMN_NAME_ACTIVE + "=1", null) > 0;
    }

    static String getDeviceToken(SQLiteDatabase db) {
        String token = null;
        Cursor c = db.query(
                TABLE_NAME,
                new String[]{COLUMN_NAME_DEVICE_ID},
                COLUMN_NAME_ACTIVE + "=1",
                null, null, null, null);
        if (c.moveToFirst()) {
            token = c.getString(0);
        }
        c.close();
        return token;
    }
}
