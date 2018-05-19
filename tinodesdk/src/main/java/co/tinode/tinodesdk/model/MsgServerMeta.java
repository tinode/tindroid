package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Metadata packet
 */
public class MsgServerMeta<P,R,S> {
    public String id;
    public String topic;
    public Date ts;
    public Description<P,R> desc;
    public Subscription<S>[] sub;
    public DelValues del;
    public String[] tags;

    public MsgServerMeta() {
    }
}
