package co.tinode.tindroid.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.provider.BaseColumns;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import co.tinode.tindroid.TindroidApp;
import co.tinode.tinodesdk.model.Acs;

/**
 * SQLite backend. Persistent store for messages and chats.
 */
public class BaseDb extends SQLiteOpenHelper {
    // Object not yet sent to the server
    public static final int STATUS_QUEUED = 0;
    // Object received by the server
    public static final int STATUS_SYNCED = 1;
    // Object deleted
    public static final int STATUS_DELETED = 2;

    /**
     * Schema version.
     */
    public static final int DATABASE_VERSION = 1;
    /**
     * Filename for SQLite file.
     */
    public static final String DATABASE_NAME = "base.db";

    private static BaseDb sInstance = null;

    private StoredAccount mAcc = null;

    private SqlStore mStore = null;

    /** Private constructor */
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

    public String getUid() {
        return mAcc != null ? mAcc.uid : null;
    }

    public boolean isReady() {
        return mAcc != null;
    }

    public void setUid(String uid) {
        if (mAcc == null) {
            mAcc = AccountDb.addOrActivateAccount(sInstance.getReadableDatabase(), uid);
        } else if (!mAcc.uid.equals(uid)) {
            // It won't work if the account is switched on a live DB.
            throw new IllegalStateException("Illegal account assignment");
        }
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
    public void onConfigure(SQLiteDatabase db){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            db.setForeignKeyConstraintsEnabled(true);
        } else {
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }

    static byte[] serialize(Object obj) {
        if (obj != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutput objout = null;
            try {
                objout = new ObjectOutputStream(baos);
                objout.writeObject(obj);
                objout.flush();
                return baos.toByteArray();
            } catch (IOException ignored) {
            } finally {
                try {
                    baos.close();
                    if (objout != null) {
                        objout.close();
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    static <T> T deserialize(byte[] bytes) {
        if (bytes != null) {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInput objin = null;
            try {
                objin = new ObjectInputStream(bais);
                return (T) objin.readObject();
            } catch (IOException | ClassNotFoundException | ClassCastException ignored) {
            } finally {
                try {
                    bais.close();
                    if (objin != null) {
                        objin.close();
                    }
                } catch (IOException ignored) {
                }
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

    static boolean updateCounter(SQLiteDatabase db, String table, String column, long id,  int counter) {
        ContentValues values = new ContentValues();
        values.put(column, counter);
        return db.update(table, values, BaseColumns._ID + "=" + id + " AND " + column + "<" + counter, null) > 0;
    }

    static boolean isMe(String uid) {
        return uid != null && uid.equals(sInstance.getUid());
    }
}
