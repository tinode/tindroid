package co.tinode.tindroid.db;


import java.util.Map;

import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.model.MsgServerData;

/**
 * StoredMessage fetched from the database
 */
public class StoredMessage<T> extends MsgServerData<T> implements Storage.Message<T> {
    public static final int STATUS_DELETE = -1;
    public static final int STATUS_NONE = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_RECV = 2;
    public static final int STATUS_READ = 3;

    public long id;
    public long topicId;
    public long userId;
    public int senderIdx;
    public int status;

    public StoredMessage() {
    }

    public StoredMessage(MsgServerData<T> m) {
        topic = m.topic;
        head = m.head;
        from = m.from;
        ts = m.ts;
        seq = m.seq;
        content = m.content;
    }

    public boolean isMine() {
        return senderIdx == 0;
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
