package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;

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
     * Content provider authority.
     */
    public static final String CONTENT_AUTHORITY = "co.tinode.tindroid.provider";
    /**
     * Base content URI. (content://co.tinode.tindroid)
     */
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + BaseDb.CONTENT_AUTHORITY);

    // Status undefined/not set.
    public static final int STATUS_UNDEFINED = 0;
    // Object is not ready to be sent to the server.
    public static final int STATUS_DRAFT = 1;
    // Object is ready but not yet sent to the server
    public static final int STATUS_QUEUED = 2;
    // Object is received by the server
    public static final int STATUS_SYNCED = 3;
    // Meta-status: object should be visible in the UI
    public static final int STATUS_VISIBLE = 3;
    // Object is hard-deleted
    public static final int STATUS_DELETED_HARD = 4;
    // Object is soft-deleted
    public static final int STATUS_DELETED_SOFT = 5;
    // Object is rejected by the server.
    public static final int STATUS_REJECTED = 6;

    /**
     * Schema version. Increment on schema changes.
     */
    private static final int DATABASE_VERSION = 4;
    /**
     * Filename for SQLite file.
     */
    private static final String DATABASE_NAME = "base.db";

    private static BaseDb sInstance = null;

    private StoredAccount mAcc = null;

    private SqlStore mStore = null;

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
    public static BaseDb getInstance() {
        if (sInstance == null) {
            sInstance = new BaseDb(TindroidApp.getAppContext());
            sInstance.mAcc = AccountDb.getActiveAccount(sInstance.getReadableDatabase());
            sInstance.mStore = new SqlStore(sInstance);
        }
        return sInstance;
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
                Log.e(TAG, "Failed to serialize", ex);
            }
        }
        return null;
    }

    /**
     * Parses serialized class from "canonical_class_name;json_representation of content".
     * @param input string to parse
     * @param <T> type of the prased object
     * @return parsed object or null
     */
    static <T> T deserialize(String input) {
        if (input != null) {
            try {
                String[] parts = input.split(";", 2);
                return Tinode.jsonDeserialize(parts[1], parts[0]);
            } catch (ClassCastException ex) {
                Log.e(TAG, "Failed to de-serialize", ex);
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

    static String serializeTags(String[] tags) {
        String result = null;
        if (tags != null) {
            StringBuilder sb = new StringBuilder();
            for (String tag : tags) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(tag);
            }
            result = sb.toString();
        }
        return result;
    }

    static String[] deserializeTags(String m) {
        String[] result = null;
        if (m != null && m.length() > 0) {
            result = m.split(",");
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

    public String getUid() {
        return mAcc != null ? mAcc.uid : null;
    }

    public void setUid(String uid) {
        if (uid == null) {
            mAcc = null;
        } else {
            if (mAcc == null) {
                mAcc = AccountDb.addOrActivateAccount(sInstance.getReadableDatabase(), uid);
            } else if (!mAcc.uid.equals(uid)) {
                AccountDb.deactivateAll(sInstance.getWritableDatabase());
                mAcc = AccountDb.addOrActivateAccount(sInstance.getReadableDatabase(), uid);
            }
        }
    }

    public void logout() {
        AccountDb.deactivateAll(sInstance.getWritableDatabase());
        setUid(null);
    }

    public boolean isReady() {
        return mAcc != null;
    }

    /**
     * Get an instance of {@link SqlStore} to use by  Tinode core for persistence.
     *
     * @return instance of {@link SqlStore}
     */
    public SqlStore getStore() {
        return mStore;
    }

    long getAccountId() {
        return mAcc.id;
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This is just a cache. Drop then re-fetch everything from the server.
        db.execSQL(MessageDb.DROP_INDEX);
        db.execSQL(MessageDb.DROP_TABLE);
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
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }
}
