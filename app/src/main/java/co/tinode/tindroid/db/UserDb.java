package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.Date;

import co.tinode.tinodesdk.User;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Local cache of known users
 */

public class UserDb implements BaseColumns {
    /**
     * The name of the main table.
     */
    static final String TABLE_NAME = "users";
    /**
     * The name of index: topic by account id and topic name.
     */
    static final String INDEX_NAME = "user_account_name";
    /**
     * Account ID, references accounts._ID
     */
    static final String COLUMN_NAME_ACCOUNT_ID = "account_id";
    /**
     * Topic name, indexed
     */
    static final String COLUMN_NAME_UID = "uid";
    /**
     * When the user was updated
     */
    static final String COLUMN_NAME_UPDATED = "updated";
    /**
     * When the user was deleted
     */
    static final String COLUMN_NAME_DELETED = "deleted";
    /**
     * Public user description, (what's shown in 'me' topic), serialized as TEXT
     */
    static final String COLUMN_NAME_PUBLIC = "pub";
    // Pseudo-UID for messages with null From.
    static final String UID_NULL = "none";
    static final int COLUMN_IDX_ID = 0;
    static final int COLUMN_IDX_ACCOUNT_ID = 1;
    static final int COLUMN_IDX_UID = 2;
    static final int COLUMN_IDX_UPDATED = 3;
    static final int COLUMN_IDX_DELETED = 4;
    static final int COLUMN_IDX_PUBLIC = 5;
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
                    COLUMN_NAME_PUBLIC + " TEXT)";
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
    private static final String TAG = "UserDb";

    /**
     * Save user to DB
     *
     * @return ID of the newly added user
     */
    static long insert(SQLiteDatabase db, Subscription sub) {
        return insert(db, sub.user, sub.updated, sub.pub);
    }

    /**
     * Save user to DB as user generated from invite
     *
     * @return ID of the newly added user
     */
    static long insert(SQLiteDatabase db, String uid, Date updated, Object pub) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_ACCOUNT_ID, BaseDb.getInstance().getAccountId());
        values.put(COLUMN_NAME_UID, uid != null ? uid : UID_NULL);
        if (updated != null) {
            values.put(COLUMN_NAME_UPDATED, updated.getTime());
        }
        if (pub != null) {
            values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(pub));
        }
        return db.insert(TABLE_NAME, null, values);
    }

    /**
     * Save user to DB
     *
     * @return ID of the newly added user
     */
    static long insert(SQLiteDatabase db, User user) {
        long id = insert(db, user.uid, user.updated, user.pub);
        if (id > 0) {
            StoredUser su = new StoredUser();
            su.id = id;
            user.setLocal(su);
        }
        return id;
    }

    /**
     * Update user record
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, Subscription sub) {
        StoredSubscription ss = (StoredSubscription) sub.getLocal();
        return !(ss == null || ss.userId <= 0) && update(db, ss.userId, sub.updated, sub.pub);
    }

    /**
     * Update user record
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, User user) {
        StoredUser su = (StoredUser) user.getLocal();
        return !(su == null || su.id <= 0) && update(db, su.id, user.updated, user.pub);
    }

    /**
     * Update user record
     *
     * @return true if the record was updated, false otherwise
     */
    public static boolean update(SQLiteDatabase db, long userId, Date updated, Object pub) {
        // Convert topic description to a map of values
        ContentValues values = new ContentValues();
        if (updated != null) {
            values.put(COLUMN_NAME_UPDATED, updated.getTime());
        }
        if (pub != null) {
            values.put(COLUMN_NAME_PUBLIC, BaseDb.serialize(pub));
        }

        return values.size() <= 0 || db.update(TABLE_NAME, values, _ID + "=" + userId, null) > 0;
    }

    /**
     * Delete all users for the given account ID.
     */
    static void deleteAll(SQLiteDatabase db, long accId) {
        db.delete(TABLE_NAME, COLUMN_NAME_ACCOUNT_ID + "=" + accId, null);
    }

    /**
     * Deletes all records from 'users' table.
     *
     * @param db Database to use.
     */
    static void truncateTable(SQLiteDatabase db) {
        try {
            // 'DELETE FROM table' in SQLite is equivalent to truncation.
            db.delete(TABLE_NAME, null, null);
        } catch (SQLException ex) {
            Log.w(TAG, "Delete failed", ex);
        }
    }

    /**
     * Given UID, get it's database _id
     *
     * @param db  database
     * @param uid UID
     * @return _id of the user
     */
    static long getId(SQLiteDatabase db, String uid) {
        long id = -1;
        String sql =
                "SELECT " + _ID +
                        " FROM " + TABLE_NAME +
                        " WHERE " +
                        COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getInstance().getAccountId() +
                        " AND " +
                        COLUMN_NAME_UID + "='" + (uid != null ? uid : UID_NULL) + "'";
        // Log.d(TAG, sql);
        Cursor c = db.rawQuery(sql, null);
        if (c.getCount() > 0) {
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
            c.close();
        }
        return id;
    }

    @SuppressWarnings("WeakerAccess")
    public static <Pu> User<Pu> readOne(SQLiteDatabase db, String uid) {
        // Instantiate topic of an appropriate class ('me' or group)
        User<Pu> user = null;
        String sql =
                "SELECT * FROM " + TABLE_NAME +
                        " WHERE " +
                        COLUMN_NAME_ACCOUNT_ID + "=" + BaseDb.getInstance().getAccountId() +
                        " AND " +
                        COLUMN_NAME_UID + "='" + (uid != null ? uid : UID_NULL) + "'";

        Cursor c = db.rawQuery(sql, null);
        if (c.getCount() > 0) {
            user = new User<>(uid);
            if (c.moveToFirst()) {
                user = new User<>(uid);
                StoredUser.deserialize(user, c);
            }
            c.close();
        }
        return user;
    }
}
