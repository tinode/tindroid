package co.tinode.tindroid.db;

import java.util.Date;
import java.util.Map;

import co.tinode.tinodesdk.Topic;

/**
 * Representation of a topic stored in a database;
 */
public class StoredTopic<Pu,Pr> {
    public long id;
    public String name;
    public Topic.TopicType type;
    public String with;
    public Date updated;
    public Date deleted;
    public int read;
    public int recv;
    public int seq;
    public String mode;

    public Pu pub;
    public Pr priv;

    public Date lastUsed;

    private Map<String,Long> senders;
}
