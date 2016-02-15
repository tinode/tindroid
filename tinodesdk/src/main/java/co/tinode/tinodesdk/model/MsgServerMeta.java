package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Created by gsokolov on 2/2/16.
 */
public class MsgServerMeta<Pu, Pr> {
    public String id;
    public String topic;
    public Date ts;
    public Description<Pu,Pr> desc;
    public Subscription<Pu,Pr>[] sub;

    public MsgServerMeta() {
    }
}
