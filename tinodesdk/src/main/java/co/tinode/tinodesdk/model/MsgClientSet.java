package co.tinode.tinodesdk.model;

/**
 * Metadata update packet
 *
 * 	topic metadata, new topic & new subscriptions only
 *  Desc *MsgSetDesc `json:"desc,omitempty"`
 *
 *  Subscription parameters
 *  Sub *MsgSetSub `json:"sub,omitempty"`
 */

public class MsgClientSet<Pu,Pr,Inv> {
    public String id;
    public String topic;

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub<Inv> sub;

    public MsgClientSet() {}

    public MsgClientSet(String id, String topic, MsgSetMeta<Pu,Pr,Inv> meta) {
        this(id, topic, meta.desc, meta.sub);
    }

    public MsgClientSet(String id, String topic, MetaSetDesc<Pu, Pr> desc,
                        MetaSetSub<Inv> sub) {
        this.id = id;
        this.topic = topic;
        this.desc = desc;
        this.sub = sub;
    }
}
