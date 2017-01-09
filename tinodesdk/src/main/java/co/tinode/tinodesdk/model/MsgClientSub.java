package co.tinode.tinodesdk.model;

/**
 * Subscribe to topic packet.
 *
 */
public class MsgClientSub<Pu,Pr,T> {
    public String id;
    public String topic;
    public MsgSetMeta<Pu,Pr,T> set;
    public MsgGetMeta get;

    public MsgClientSub() {}

    public MsgClientSub(String id, String topic, MsgSetMeta<Pu,Pr,T> set, MsgGetMeta get) {
        this.id = id;
        this.topic = topic;
        this.set = set;
        this.get = get;
    }
}
