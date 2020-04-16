package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Metadata update packet
 *
 * 	topic metadata, new topic &amp; new subscriptions only
 *  Desc *MsgSetDesc `json:"desc,omitempty"`
 *
 *  Subscription parameters
 *  Sub *MsgSetSub `json:"sub,omitempty"`
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientSet<Pu,Pr> implements Serializable {
    public String id;
    public String topic;

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub sub;
    public String[] tags;
    public Credential cred;

    public MsgClientSet() {}

    public MsgClientSet(String id, String topic, MsgSetMeta<Pu,Pr> meta) {
        this(id, topic, meta.desc, meta.sub, meta.tags, meta.cred);
    }

    protected MsgClientSet(String id, String topic, MetaSetDesc<Pu, Pr> desc,
                        MetaSetSub sub, String[] tags, Credential cred) {
        this.id = id;
        this.topic = topic;
        this.desc = desc;
        this.sub = sub;
        this.tags = tags;
        this.cred = cred;
    }
}
