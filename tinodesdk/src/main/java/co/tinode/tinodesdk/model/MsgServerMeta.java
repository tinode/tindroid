package co.tinode.tinodesdk.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * Metadata packet
 */
public class MsgServerMeta<DP, DR, SP, SR> implements Serializable {
    public String id;
    public String topic;
    public Date ts;
    public Description<DP,DR> desc;
    public Subscription<SP,SR>[] sub;
    public DelValues del;
    public String[] tags;
    public Credential[] cred;
    public Map<String, Object> aux;

    public MsgServerMeta() {
    }
}
