package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Metadata packet
 */
public class MsgServerMeta<DP,DR,SP,SR> {
    public String id;
    public String topic;
    public Date ts;
    public Description<DP,DR> desc;
    public Subscription<SP,SR>[] sub;
    public DelValues del;
    public String[] tags;

    public MsgServerMeta() {
    }
}
