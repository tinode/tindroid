package co.tinode.tindroid.db;

import android.database.Cursor;

import java.util.Date;

import co.tinode.tinodesdk.LocalData;
import co.tinode.tinodesdk.Topic;

/**
 * Representation of a topic stored in a database;
 */
public class StoredTopic<Pu,Pr,T> implements LocalData.Payload {
    public long mId;
    public Date mLastUsed;

    public StoredTopic() {
    }

    @SuppressWarnings("unchecked")
    protected static <Pu,Pr,T> void deserialize(Topic<Pu,Pr,T> topic, Cursor c) {
        StoredTopic<Pu,Pr,T> st = new StoredTopic<>();

        st.mId = c.getLong(TopicDb.COLUMN_IDX_ID);
        st.mLastUsed = new Date(c.getLong(TopicDb.COLUMN_IDX_LASTUSED));

        topic.setUpdated(new Date(c.getLong(TopicDb.COLUMN_IDX_UPDATED)));
        topic.setDeleted(new Date(c.getLong(TopicDb.COLUMN_IDX_DELETED)));

        topic.setRead(c.getInt(TopicDb.COLUMN_IDX_READ));
        topic.setRecv(c.getInt(TopicDb.COLUMN_IDX_RECV));
        topic.setSeq(c.getInt(TopicDb.COLUMN_IDX_SEQ));
        topic.setClear(c.getInt(TopicDb.COLUMN_IDX_CLEAR));

        topic.setWith(c.getString(TopicDb.COLUMN_IDX_WITH));
        topic.setSerializedTypes(c.getString(TopicDb.COLUMN_IDX_SERIALIZED_TYPES));
        topic.setPub((Pu) BaseDb.deserialize(c.getBlob(TopicDb.COLUMN_IDX_PUBLIC)));
        topic.setPriv((Pr) BaseDb.deserialize(c.getBlob(TopicDb.COLUMN_IDX_PRIVATE)));

        topic.setMode(c.getString(TopicDb.COLUMN_IDX_ACCESSMODE));

        topic.setLocal(st);
    }

    public static long getId(Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return st.mId;
    }
}
