package co.tinode.tindroid.db;

import co.tinode.tinodesdk.LocalData;

/**
 * Subscription record
 */
public class StoredSubscription implements LocalData.Payload {
    public long mId;
    public long topicId;
    public long userId;
    public int senderIdx;


}
