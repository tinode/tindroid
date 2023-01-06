package co.tinode.tindroid.db;

import android.database.Cursor;

import java.util.Date;
import java.util.Map;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgRange;
import co.tinode.tinodesdk.model.MsgServerData;

/**
 * StoredMessage fetched from the database
 */
public class StoredMessage extends MsgServerData implements Storage.Message {
    public long id;
    public long topicId;
    public long userId;
    public BaseDb.Status status;
    public int delId;
    public int high;

    StoredMessage() {
    }

    StoredMessage(MsgServerData m) {
        topic = m.topic;
        head = m.head;
        from = m.from;
        ts = m.ts;
        seq = m.seq;
        content = m.content;
    }

    public static StoredMessage readMessage(Cursor c, int previewLength) {
        StoredMessage msg = new StoredMessage();

        msg.id = c.getLong(MessageDb.COLUMN_IDX_ID);
        msg.topicId = c.getLong(MessageDb.COLUMN_IDX_TOPIC_ID);
        msg.userId = c.getLong(MessageDb.COLUMN_IDX_USER_ID);
        msg.status = BaseDb.Status.fromInt(c.getInt(MessageDb.COLUMN_IDX_STATUS));
        msg.from = c.getString(MessageDb.COLUMN_IDX_SENDER);
        msg.ts = new Date(c.getLong(MessageDb.COLUMN_IDX_REPLACES_TS));
        msg.seq =  c.isNull(MessageDb.COLUMN_IDX_EFFECTIVE_SEQ) ?
                c.getInt(MessageDb.COLUMN_IDX_SEQ) : c.getInt(MessageDb.COLUMN_IDX_EFFECTIVE_SEQ);
        msg.high = c.isNull(MessageDb.COLUMN_IDX_HIGH) ? 0 : c.getInt(MessageDb.COLUMN_IDX_HIGH);
        msg.delId = c.isNull(MessageDb.COLUMN_IDX_DEL_ID) ? 0 : c.getInt(MessageDb.COLUMN_IDX_DEL_ID);
        msg.head = BaseDb.deserialize(c.getString(MessageDb.COLUMN_IDX_HEAD));
        if (previewLength != 0) {
            msg.content = BaseDb.deserialize(c.getString(MessageDb.COLUMN_IDX_CONTENT));
            if (previewLength > 0 && msg.content != null) {
                msg.content = msg.content.preview(previewLength);
            }
        }
        if (c.getColumnCount() > MessageDb.COLUMN_IDX_TOPIC_NAME) {
            msg.topic = c.getString(MessageDb.COLUMN_IDX_TOPIC_NAME);
        }

        return msg;
    }

    static MsgRange readDelRange(Cursor c) {
        // 0: delId, 1: seq, 2: high
        return new MsgRange(c.getInt(1), c.getInt(2));
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public boolean isMine() {
        return BaseDb.isMe(from);
    }

    @Override
    public Drafty getContent() {
        return content;
    }

    @Override
    public void setContent(Drafty content) {
        this.content = content;
    }

    @Override
    public Map<String, Object> getHead() {
        return head;
    }

    @Override
    public Integer getIntHeader(String key) {
        Object val = getHeader(key);
        if (val instanceof Integer) {
            return (Integer) val;
        }
        return null;
    }

    public int getStatus() {
        return status != null ? status.value : BaseDb.Status.UNDEFINED.value;
    }

    @Override
    public long getDbId() {
        return id;
    }

    @Override
    public int getSeqId() {
        return seq;
    }

    @Override
    public boolean isPending() {
        return status == BaseDb.Status.DRAFT || status == BaseDb.Status.QUEUED || status == BaseDb.Status.SENDING;
    }

    @Override
    public boolean isReady() {
        return status == BaseDb.Status.QUEUED;
    }

    @Override
    public boolean isDeleted() {
        return status == BaseDb.Status.DELETED_SOFT || status == BaseDb.Status.DELETED_HARD;
    }

    @Override
    public boolean isDeleted(boolean hard) {
        return hard ? status == BaseDb.Status.DELETED_HARD : status == BaseDb.Status.DELETED_SOFT;
    }

    @Override
    public boolean isSynced() {
        return status == BaseDb.Status.SYNCED;
    }

    public boolean isReplacement() {
        return getHeader("replace") != null;
    }

    public int getReplacementSeqId() {
        String replace = getStringHeader("replace");
        if (replace == null || replace.length() < 2 || replace.charAt(0) != ':') {
            return 0;
        }
        try {
            return Integer.parseInt(replace.substring(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
