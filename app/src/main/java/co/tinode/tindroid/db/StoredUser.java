package co.tinode.tindroid.db;

import android.database.Cursor;

import java.util.Date;

import co.tinode.tinodesdk.LocalData;
import co.tinode.tinodesdk.User;

/**
 * Topic subscriber stored in the database
 */
public class StoredUser implements LocalData.Payload {
    public long id;

    static <Pu> void deserialize(User<Pu> user, Cursor c) {
        StoredUser su = new StoredUser();

        su.id = c.getLong(UserDb.COLUMN_IDX_ID);

        user.uid = c.getString(UserDb.COLUMN_IDX_UID);
        if (!c.isNull(UserDb.COLUMN_IDX_UPDATED)) {
            user.updated = new Date(c.getLong(UserDb.COLUMN_IDX_UPDATED));
        } else {
            user.updated = null;
        }
        user.pub = BaseDb.deserialize(c.getString(UserDb.COLUMN_IDX_PUBLIC));

        user.setLocal(su);
    }
}
