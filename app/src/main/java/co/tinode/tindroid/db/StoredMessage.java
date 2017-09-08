package co.tinode.tindroid.db;


import android.database.Cursor;

import java.util.Date;
import java.util.Map;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.model.MsgServerData;

/**
 * StoredMessage fetched from the database
 */
public class StoredMessage extends MsgServerData implements Storage.Message {
    public long id;
    public long topicId;
    public long userId;
    public int status;

    public StoredMessage() {
    }

    public StoredMessage(MsgServerData m) {
        topic = m.topic;
        head = m.head;
        from = m.from;
        ts = m.ts;
        seq = m.seq;
        content = m.content;
    }

    public static StoredMessage readMessage(Cursor c) {
        StoredMessage msg = new StoredMessage();

        msg.id = c.getLong(MessageDb.COLUMN_IDX_ID);
        msg.topicId = c.getLong(MessageDb.COLUMN_IDX_TOPIC_ID);
        msg.userId = c.getLong(MessageDb.COLUMN_IDX_USER_ID);
        msg.status = c.getInt(MessageDb.COLUMN_IDX_STATUS);
        msg.from = c.getString(MessageDb.COLUMN_IDX_SENDER);
        msg.seq = c.getInt(MessageDb.COLUMN_IDX_SEQ);
        msg.ts = new Date(c.getLong(MessageDb.COLUMN_IDX_TS));
        msg.content = BaseDb.deserialize(c.getBlob(MessageDb.COLUMN_IDX_CONTENT));

        return msg;
    }

    public boolean isMine() {
        return BaseDb.isMe(from);
    }

    @Override
    public Object getContent() {
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
