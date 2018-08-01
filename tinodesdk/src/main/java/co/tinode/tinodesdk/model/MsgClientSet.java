package co.tinode.tinodesdk.model;

/**
 * Metadata update packet
 *
 * 	topic metadata, new topic &amp; new subscriptions only
 *  Desc *MsgSetDesc `json:"desc,omitempty"`
 *
 *  Subscription parameters
 *  Sub *MsgSetSub `json:"sub,omitempty"`
 */

public class MsgClientSet<Pu,Pr> {
    public String id;
    public String topic;

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub sub;

    public MsgClientSet() {}

    public MsgClientSet(String id, String topic, MsgSetMeta<Pu,Pr> meta) {
        this(id, topic, meta.desc, meta.sub);
    }

    public MsgClientSet(String id, String topic, MetaSetDesc<Pu, Pr> desc,
                        MetaSetSub sub) {
        this.id = id;
        this.topic = topic;
        this.desc = desc;
        this.sub = sub;
    }
}
