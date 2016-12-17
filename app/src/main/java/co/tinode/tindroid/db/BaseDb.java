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

/**
 * SQLite backend. Persistent store for messages and chats.
 */
public class BaseDb extends SQLiteOpenHelper {
    /**
     * Schema version.
     */
    public static final int DATABASE_VERSION = 1;
    /**
     * Filename for SQLite file.
     */
    public static final String DATABASE_NAME = "base.db";

    private static BaseDb sInstance = null;

    private long mAccountId = -1;
    private String mMyUid = null;

    private SqlStore mStore = null;

    /** Private constructor */
    private BaseDb(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Get instance of BaseDb for a given UID
     *
     * @param context application context
     * @return BaseDb instance
     */
    public static BaseDb getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BaseDb(context);
            if (sInstance.mStore == null) {
                sInstance.mStore = new SqlStore(sInstance.getWritableDatabase());
            }
        }
        return sInstance;
    }

    public void setAccount(String uid) {
        if(mMyUid != null) {
            // It won't work if the account is switched on a live DB.
            throw new IllegalStateException("Account is already assigned");
        }
        mMyUid = uid;
        mAccountId = AccountDb.getAccountId(sInstance.getWritableDatabase(), uid);
    }

    /**
     * Get an instance of {@link SqlStore} to use by  Tinode core for persistence.
     *
     * @return instance of {@link SqlStore}
     */
    public SqlStore getStore() {
        if (mStore == null) {
            mStore = new SqlStore(getWritableDatabase());
        }
        return mStore;
    }

    public long getAccountId() {
        return mAccountId;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(AccountDb.CREATE_TABLE);
        db.execSQL(AccountDb.CREATE_INDEX);
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
        db.execSQL(AccountDb.DROP_INDEX);
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

    static boolean updateCounter(SQLiteDatabase db, String table, String column, long id,  int counter) {
        ContentValues values = new ContentValues();
        values.put(column, counter);
        return db.update(table, values, BaseColumns._ID + "=" + id + " AND " + column + "<" + counter, null) > 0;
    }

    static boolean isMe(String uid) {
        return uid != null && uid.equals(sInstance.mMyUid);
    }
}
