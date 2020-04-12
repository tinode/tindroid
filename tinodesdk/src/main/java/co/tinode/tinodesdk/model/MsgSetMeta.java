package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Playload for setting meta params, a combination of MetaSetDesc, MetaSetSub, tags, credential.
 */
public class MsgSetMeta<Pu,Pr> implements Serializable {

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub sub;
    public String[] tags;
    public Credential cred;

    public MsgSetMeta() {}

    public MsgSetMeta(MetaSetDesc<Pu,Pr> desc, MetaSetSub sub, String[] tags, Credential cred) {
        this.desc = desc;
        this.sub = sub;
        this.tags = tags;
        this.cred = cred;
    }

    public MsgSetMeta(MetaSetDesc<Pu,Pr> desc) {
        this(desc, null, null, null);
    }

    public MsgSetMeta(MetaSetSub sub) {
        this(null, sub, null, null);
    }

    public MsgSetMeta(String[] tags) {
        this(null, null, tags, null);
    }

    public MsgSetMeta(Credential cred) {
        this(null, null, null, cred);
    }

}
