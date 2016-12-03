package co.tinode.tindroid.db;

import co.tinode.tinodesdk.model.AccessMode;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Subscription record
 */
public class StoredSubscription<Pu,Pr> extends Subscription<Pu,Pr> {
    public long id;
    public long topicId;
    public long userId;

    public int senderIdx;

    public AccessMode amode;
}
