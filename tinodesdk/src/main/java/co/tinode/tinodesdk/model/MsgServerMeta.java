package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Metadata packet
 */
public class MsgServerMeta<Pu, Pr> {
    public String id;
    public String topic;
    public Date ts;
    public Description<Pu,Pr> desc;
    public Subscription<Pu,Pr>[] sub;
    public DelValues del;
    public String[] tags;

    public MsgServerMeta() {
    }
}
