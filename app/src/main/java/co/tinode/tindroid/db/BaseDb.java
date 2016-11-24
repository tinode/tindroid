package co.tinode.tindroid.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.text.TextUtils;

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

    private static long sAccountId = -1;

    private static String sUid = null;

    private BaseDb(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Get instance of BaseDb for a given UID
     *
     * @param context application context
     * @param uid UID of the current user.
     * @return BaseDb instance
     */
    public static BaseDb getInstance(Context context, String uid) {
        if (sInstance == null) {
            sInstance = new BaseDb(context);
            sUid = uid;
            sAccountId = AccountDb.getAccountId(sInstance.getWritableDatabase(), uid);
        }
        return sInstance;
    }

    public static long getAccountId() {
        return sAccountId;
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

    static boolean isMe(String uid) {
        return uid != null && uid.equals(sUid);
    }
}
