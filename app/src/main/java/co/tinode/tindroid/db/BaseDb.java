package co.tinode.tindroid.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.SparseArray;

import com.fasterxml.jackson.core.JsonProcessingException;

import androidx.annotation.NonNull;
import co.tinode.tindroid.TindroidApp;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.Defacs;

/**
 * SQLite backend. Persistent store for messages and chats.
 */
public class BaseDb extends SQLiteOpenHelper {
    private static final String TAG = "BaseDb";

    /**
     * Schema version. Increment on schema changes.
     */
    private static final int DATABASE_VERSION = 20;

    /**
     * Filename for SQLite file.
     */
    private static final String DATABASE_NAME = "base.db";
    private static BaseDb sInstance = null;
    private StoredAccount mAcc = null;
    private SqlStore mStore = null;

    static final int UNSENT_ID_START = 2_000_000_000;

    /**
     * Private constructor
     */
    private BaseDb(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Get instance of BaseDb
     *
     * @return BaseDb instance
     */
    @SuppressLint("UnsafeOptInUsageError")
    @NonNull
    public static BaseDb getInstance() {
        if (sInstance == null) {
            sInstance = new BaseDb(TindroidApp.getAppContext());
            sInstance.mAcc = AccountDb.getActiveAccount(sInstance.getReadableDatabase());
            sInstance.mStore = new SqlStore(sInstance);
        }
        return sInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(AccountDb.CREATE_TABLE);
        db.execSQL(AccountDb.CREATE_INDEX_1);
        db.execSQL(AccountDb.CREATE_INDEX_2);
        db.execSQL(TopicDb.CREATE_TABLE);
        db.execSQL(TopicDb.CREATE_INDEX);
        db.execSQL(UserDb.CREATE_TABLE);
        db.execSQL(UserDb.CREATE_INDEX);
        db.execSQL(SubscriberDb.CREATE_TABLE);
        db.execSQL(SubscriberDb.CREATE_INDEX);
        db.execSQL(MessageDb.CREATE_TABLE);
        db.execSQL(MessageDb.CREATE_INDEX);
        db.execSQL(MessageDb.CREATE_INDEX_2);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep existing account to be sure the user is not logged out on DB upgrade.
        StoredAccount acc = null;
        String deviceToken = null;
        try {
            acc = AccountDb.getActiveAccount(db);
            deviceToken = AccountDb.getDeviceToken(db);
        } catch (SQLiteException ex) {
            Log.w(TAG, "Unable to retain account across DB upgrade", ex);
        }

        // This is just a cache. Drop then re-fetch everything from the server.
        db.execSQL(MessageDb.DROP_INDEX);
        db.execSQL(MessageDb.DROP_INDEX_2);
        db.execSQL(MessageDb.DROP_TABLE);
        // Remove this on the next release.
        db.execSQL("DROP TABLE IF EXISTS edit_history");
        db.execSQL(SubscriberDb.DROP_INDEX);
        db.execSQL(SubscriberDb.DROP_TABLE);
        db.execSQL(UserDb.DROP_INDEX);
        db.execSQL(UserDb.DROP_TABLE);
        db.execSQL(TopicDb.DROP_INDEX);
        db.execSQL(TopicDb.DROP_TABLE);
        db.execSQL(AccountDb.DROP_INDEX_2);
        db.execSQL(AccountDb.DROP_INDEX_1);
        db.execSQL(AccountDb.DROP_TABLE);
        onCreate(db);

        if (acc != null) {
            // Restore active account.
            AccountDb.addOrActivateAccount(db, acc.uid, acc.hostURI);
            AccountDb.updateCredentials(db, acc.credMethods);
            AccountDb.updateDeviceToken(db, deviceToken);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    public static boolean isUnsentSeq(int seq) {
        return seq >= BaseDb.UNSENT_ID_START;
    }

    /**
     * Serializes object as "canonical_class_name;json_representation of content".
     *
     * @param obj object to serialize
     * @return string representation of the object.
     */
    static String serialize(Object obj) {
        if (obj != null) {
            try {
                return obj.getClass().getCanonicalName() + ";" + Tinode.jsonSerialize(obj);
            } catch (JsonProcessingException ex) {
                Log.w(TAG, "Failed to serialize", ex);
            }
        }
        return null;
    }

    /**
     * Parses serialized object or an array of objects from
     * "canonical_class_name;json content" or
     * "canonical_class_name[];json of array of objects"
     *
     * @param input string to parse
     * @param <T>   type of the parsed object
     * @return parsed object or null
     */
    static <T> T deserialize(String input) {
        if (input != null) {
            try {
                String[] parts = input.split(";", 2);
                if (parts[0].endsWith("[]")) {
                    // Deserializing an array.
                    parts[0] = parts[0].substring(0, parts[0].length() - 2);
                    //noinspection unchecked
                    return (T) Tinode.jsonDeserializeArray(parts[1], parts[0]);
                }
                // Deserializing a single object.
                return Tinode.jsonDeserialize(parts[1], parts[0]);
            } catch (ClassCastException ex) {
                Log.w(TAG, "Failed to de-serialize", ex);
            }
        }
        return null;
    }

    static String serializeMode(Acs acs) {
        String result = "";
        if (acs != null) {
            String val = acs.getMode();
            result = val != null ? val + "," : ",";

            val = acs.getWant();
            result += val != null ? val + "," : ",";

            val = acs.getGiven();
            result += val != null ? val : "";
        }
        return result;
    }

    static Acs deserializeMode(String m) {
        Acs result = new Acs();
        if (m != null) {
            String[] parts = m.split(",");
            if (parts.length == 3) {
                result.setMode(parts[0]);
                result.setWant(parts[1]);
                result.setGiven(parts[2]);
            }
        }
        return result;
    }

    static String serializeDefacs(Defacs da) {
        String result = "";
        if (da != null) {
            String val = da.getAuth();
            result = val != null ? val + "," : ",";

            val = da.getAnon();
            result += val != null ? val : "";
        }
        return result;
    }

    static Defacs deserializeDefacs(String m) {
        Defacs result = null;
        if (m != null) {
            String[] parts = m.split(",");
            if (parts.length == 2) {
                result = new Defacs(parts[0], parts[1]);
            }
        }
        return result;
    }

    static String serializeStringArray(String[] arr) {
        String result = null;
        if (arr != null && arr.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (String val : arr) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(val);
            }
            result = sb.toString();
        }
        return result;
    }

    static String[] deserializeStringArray(String str) {
        String[] result = null;
        if (str != null && !str.isEmpty()) {
            result = str.split(",");
        }
        return result;
    }

    static boolean updateCounter(SQLiteDatabase db, String table, String column, long id, int counter) {
        ContentValues values = new ContentValues();
        values.put(column, counter);
        return db.update(table, values, BaseColumns._ID + "=" + id + " AND " + column + "<" + counter, null) > 0;
    }

    static boolean isMe(String uid) {
        return uid != null && uid.equals(sInstance.getUid());
    }

    /**
     * get UID of the currently logged in user.
     *
     * @return UID or {@code null} if user is not logged in.
     */
    public String getUid() {
        return mAcc != null ? mAcc.uid : null;
    }

    void setUid(String uid, String hostURI) {
        if (uid == null) {
            mAcc = null;
            AccountDb.deactivateAll(sInstance.getWritableDatabase());
        } else {
            mAcc = AccountDb.addOrActivateAccount(sInstance.getWritableDatabase(), uid, hostURI);
        }
    }

    void clearDb() {
        SQLiteDatabase db = sInstance.getWritableDatabase();
        MessageDb.truncateTable(db);
        SubscriberDb.truncateTable(db);
        TopicDb.truncateTable(db);
        UserDb.truncateTable(db);
        AccountDb.truncateTable(db);
    }

    void updateCredentials(String[] credMethods) {
        if (mAcc != null) {
            if (AccountDb.updateCredentials(sInstance.getWritableDatabase(), credMethods)) {
                mAcc.credMethods = credMethods;
            }
        }
    }

    void deleteUid(String uid) {
        StoredAccount acc;
        SQLiteDatabase db = sInstance.getWritableDatabase();
        if (mAcc != null && mAcc.uid.equals(uid)) {
            acc = mAcc;
            mAcc = null;
        } else {
            acc = AccountDb.getByUid(db, uid);
        }

        if (acc != null) {
            AccountDb.delete(db, acc);
        }
    }

    String getHostURI() {
        return mAcc != null ? mAcc.hostURI : null;
    }

    public boolean isReady() {
        return mAcc != null && !isCredValidationRequired();
    }

    public boolean isCredValidationRequired() {
        return mAcc != null && mAcc.credMethods != null && mAcc.credMethods.length > 0;
    }

    public String getFirstValidationMethod() {
        return isCredValidationRequired() ? mAcc.credMethods[0] : null;
    }

    /**
     * Get an instance of {@link SqlStore} to use by Tinode core for persistence.
     *
     * @return instance of {@link SqlStore}
     */
    @NonNull public SqlStore getStore() {
        return mStore;
    }

    long getAccountId() {
        return mAcc != null ? mAcc.id : -1;
    }

    public enum Status {
        // Status undefined/not set.
        UNDEFINED(0),
        // Object is not ready to be sent to the server.
        DRAFT(10),
        // Object is waiting in the queue to be sent to the server.
        QUEUED(20),
        // Object is in the process of being sent to the server.
        SENDING(30),
        // Send failed.
        FAILED(40),
        // Object is received by the server.
        SYNCED(50),
        // Object is hard-deleted.
        DELETED_HARD(60),
        // Object is soft-deleted.
        DELETED_SOFT(70),
        // The object is a deletion range marker synchronized with the server.
        DELETED_SYNCED(80);

        private static final SparseArray<Status> intToTypeMap = new SparseArray<>();

        static {
            for (Status type : Status.values()) {
                intToTypeMap.put(type.value, type);
            }
        }

        public final int value;

        Status(int v) {
            value = v;
        }

        public static Status fromInt(int i) {
            Status type = intToTypeMap.get(i);
            if (type == null) {
                return Status.UNDEFINED;
            }
            return type;
        }
    }
}
