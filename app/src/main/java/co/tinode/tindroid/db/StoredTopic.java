package co.tinode.tindroid.db;

import android.database.Cursor;

import java.util.Date;

import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.AccessMode;
import co.tinode.tinodesdk.model.Description;

/**
 * Representation of a topic stored in a database;
 */
public class StoredTopic<Pu,Pr,T> extends Topic<Pu,Pr,T> {
    public long mId;

    public Date mLastUsed;

    public StoredTopic(Tinode tinode, String name, Listener<Pu,Pr,T> listener) {
        super(tinode, name, listener);
    }

    public StoredTopic(Topic<Pu,Pr,T> topic) {
        // Copy constructor
        super(topic);
    }

    protected void deserialize(Cursor c) {
        mId = c.getLong(TopicDb.COLUMN_IDX_ID);

        if (mDescription == null) {
            mDescription = new Description<>();
        }
        mDescription.updated = new Date(c.getLong(TopicDb.COLUMN_IDX_UPDATED));
        mDescription.deleted = new Date(c.getLong(TopicDb.COLUMN_IDX_DELETED));

        mDescription.read = c.getInt(TopicDb.COLUMN_IDX_READ);
        mDescription.recv = c.getInt(TopicDb.COLUMN_IDX_RECV);
        mDescription.seq = c.getInt(TopicDb.COLUMN_IDX_SEQ);
        mDescription.clear = c.getInt(TopicDb.COLUMN_IDX_CLEAR);

        mDescription.with = c.getString(TopicDb.COLUMN_IDX_WITH);
        mDescription.pub = BaseDb.deserialize(c.getBlob(TopicDb.COLUMN_IDX_PUBLIC));
        mDescription.priv = BaseDb.deserialize(c.getBlob(TopicDb.COLUMN_IDX_PRIVATE));

        mMode = new AccessMode(c.getString(TopicDb.COLUMN_IDX_ACCESSMODE));

        mLastUsed = new Date(c.getLong(TopicDb.COLUMN_IDX_LASTUSED));

    }

    public long getId() {
        return mId;
    }

    public void setId(long id) {
        mId = id;
    }

    public Date getUpdated() {
        return mDescription.updated;
    }

    public String getWith() {
        return getTopicType() == TopicType.P2P ? mDescription.with : null;
    }

    public int getSeq() {
        return mDescription.seq;
    }

    public int getClear() {
        return mDescription.clear;
    }

    public int getRead() {
        return mDescription.read;
    }

    public void setRead(int read) {
        mDescription.read = read;
    }

    public int getRecv() {
        return mDescription.recv;
    }

    public AccessMode getMode() {
        return mMode;
    }

    public Pu getPub() {
        return mDescription.pub;
    }

    public Pr getPriv() {
        return mDescription.priv;
    }
}
