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

    public SetDesc<Pu,Pr> desc;
    public MsgSetMeta.SetSub<Inv> sub;

    public MsgClientSet() {}

    public MsgClientSet(String id, String topic, MsgSetMeta<Pu,Pr,Inv> meta) {
        this(id, topic, meta.desc, meta.sub);
    }

    public MsgClientSet(String id, String topic, SetDesc<Pu, Pr> desc,
                        MsgSetMeta.SetSub<Inv> sub) {
        this.id = id;
        this.topic = topic;
        this.desc = desc;
        this.sub = sub;
    }
}
