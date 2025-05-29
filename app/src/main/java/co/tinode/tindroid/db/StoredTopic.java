package co.tinode.tindroid.db;

import android.database.Cursor;

import java.util.Date;

import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.LocalData;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Topic;

/**
 * Representation of a topic stored in a database;
 */
public class StoredTopic implements LocalData.Payload {
    public long id;
    public Date lastUsed;
    // Seq value of the earliest cached message.
    public int minLocalSeq;
    // Seq value of the latest cached message.
    public int maxLocalSeq;
    public BaseDb.Status status;
    public int nextUnsentId;

    public StoredTopic() {
    }

    @SuppressWarnings("unchecked")
    static void deserialize(Topic topic, Cursor c) {
        StoredTopic st = new StoredTopic();

        st.id = c.getLong(TopicDb.COLUMN_IDX_ID);
        st.status = BaseDb.Status.fromInt(c.getInt(TopicDb.COLUMN_IDX_STATUS));
        st.lastUsed = new Date(c.getLong(TopicDb.COLUMN_IDX_LASTUSED));
        st.minLocalSeq = c.getInt(TopicDb.COLUMN_IDX_MIN_LOCAL_SEQ);
        st.maxLocalSeq = c.getInt(TopicDb.COLUMN_IDX_MAX_LOCAL_SEQ);
        st.nextUnsentId = c.getInt(TopicDb.COLUMN_IDX_NEXT_UNSENT_SEQ);

        topic.setUpdated(new Date(c.getLong(TopicDb.COLUMN_IDX_UPDATED)));
        topic.setDeleted(st.status == BaseDb.Status.DELETED_HARD || st.status == BaseDb.Status.DELETED_SOFT);
        topic.setTouched(st.lastUsed);
        if (topic instanceof ComTopic) {
            ((ComTopic) topic).setHasChannelAccess(c.getInt(TopicDb.COLUMN_IDX_CHANNEL_ACCESS) != 0);
        }

        topic.setRead(c.getInt(TopicDb.COLUMN_IDX_READ));
        topic.setRecv(c.getInt(TopicDb.COLUMN_IDX_RECV));
        topic.setSeq(c.getInt(TopicDb.COLUMN_IDX_SEQ));
        topic.setClear(c.getInt(TopicDb.COLUMN_IDX_CLEAR));
        topic.setMaxDel(c.getInt(TopicDb.COLUMN_IDX_MAX_DEL));

        topic.setTags(BaseDb.deserializeStringArray(c.getString(TopicDb.COLUMN_IDX_TAGS)));
        topic.setAux(BaseDb.deserialize(c.getString(TopicDb.COLUMN_IDX_AUX)));
        try {
            topic.setLastSeen(new Date(c.getLong(TopicDb.COLUMN_IDX_LAST_SEEN)),
                    c.getString(TopicDb.COLUMN_IDX_LAST_SEEN_UA));
        } catch (Exception ignored) {
            // It throws is lastSeen is NULL, which is normal.
        }

        if (topic instanceof MeTopic) {
            ((MeTopic) topic).setCreds(BaseDb.deserialize(c.getString(TopicDb.COLUMN_IDX_CREDS)));
        }
        topic.setPub(BaseDb.deserialize(c.getString(TopicDb.COLUMN_IDX_PUBLIC)));
        topic.setTrusted(BaseDb.deserialize(c.getString(TopicDb.COLUMN_IDX_TRUSTED)));
        topic.setPriv(BaseDb.deserialize(c.getString(TopicDb.COLUMN_IDX_PRIVATE)));

        topic.setAccessMode(BaseDb.deserializeMode(c.getString(TopicDb.COLUMN_IDX_ACCESSMODE)));
        topic.setDefacs(BaseDb.deserializeDefacs(c.getString(TopicDb.COLUMN_IDX_DEFACS)));

        topic.setLocal(st);
    }

    public static long getId(Topic topic) {
        StoredTopic st = (StoredTopic) topic.getLocal();
        return st != null ? st.id : -1;
    }
}
