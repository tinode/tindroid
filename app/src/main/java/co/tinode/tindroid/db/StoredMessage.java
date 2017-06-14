package co.tinode.tindroid.db;


import android.database.Cursor;

import java.util.Date;
import java.util.Map;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Announcement;
import co.tinode.tinodesdk.model.MsgServerData;

/**
 * StoredMessage fetched from the database
 */
public class StoredMessage<T> extends MsgServerData<T> implements Storage.Message<T> {
    public static final int MSG_TYPE_NORMAL = 0;
    public static final int MSG_TYPE_META = 1;

    public long id;
    public long topicId;
    public long userId;
    public int status;
    public int type;
    public Announcement meta;

    public StoredMessage() {
    }

    public StoredMessage(MsgServerData<T> m) {
        topic = m.topic;
        head = m.head;
        from = m.from;
        ts = m.ts;
        seq = m.seq;
        type = Topic.getTopicTypeByName(topic) == Topic.TopicType.ME ?
                MSG_TYPE_META : MSG_TYPE_NORMAL;
        if (type == MSG_TYPE_META) {
            meta = (Announcement) m.content;
        } else {
            content = m.content;
        }
    }

    public static <T> StoredMessage<T> readMessage(Cursor c) {
        StoredMessage<T> msg = new StoredMessage<>();

        msg.id = c.getLong(MessageDb.COLUMN_IDX_ID);
        msg.topicId = c.getLong(MessageDb.COLUMN_IDX_TOPIC_ID);
        msg.userId = c.getLong(MessageDb.COLUMN_IDX_USER_ID);
        msg.status = c.getInt(MessageDb.COLUMN_IDX_STATUS);
        msg.type = c.getInt(MessageDb.COLUMN_IDX_TYPE);
        msg.from = c.getString(MessageDb.COLUMN_IDX_SENDER);
        msg.seq = c.getInt(MessageDb.COLUMN_IDX_SEQ);
        msg.ts = new Date(c.getLong(MessageDb.COLUMN_IDX_TS));
        if (msg.type == MSG_TYPE_META) {
            msg.meta = BaseDb.deserialize(c.getBlob(MessageDb.COLUMN_IDX_CONTENT));
        } else {
            msg.content = BaseDb.deserialize(c.getBlob(MessageDb.COLUMN_IDX_CONTENT));
        }

        return msg;
    }

    public boolean isMine() {
        return BaseDb.isMe(from);
    }

    @Override
    public T getContent() {
        return content;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Map<String, String> getHeader() {
        return head;
    }
}
