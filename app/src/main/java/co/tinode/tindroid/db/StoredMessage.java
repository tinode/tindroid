package co.tinode.tindroid.db;

import java.util.Date;
import java.util.Map;

/**
 * StoredMessage fetched from the database
 */

public class StoredMessage<T> {
    public String id;
    public long topicId;
    public long userId;
    public int senderIdx;
    public Date ts;
    public int seq;
    public T content;
    public boolean isMine;
}
