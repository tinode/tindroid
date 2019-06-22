package co.tinode.tinodesdk.model;

import java.util.List;

/**
 * Playload for setting meta params, a combination of MetaSetDesc, MetaSetSub, tags, credential.
 */
public class MsgSetMeta<Pu,Pr> {

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub sub;
    public String[] tags;
    public Credential cred;

    public MsgSetMeta() {}

    public MsgSetMeta(MetaSetDesc<Pu,Pr> desc, MetaSetSub sub, List<String> tags, Credential cred) {
        this.desc = desc;
        this.sub = sub;
        if (tags != null) {
            this.tags = tags.toArray(new String[]{});
        }
        this.cred = cred;
    }

    public MsgSetMeta(MetaSetDesc<Pu,Pr> desc) {
        this(desc, null, null, null);
    }

    public MsgSetMeta(MetaSetSub sub) {
        this(null, sub, null, null);
    }

    public MsgSetMeta(List<String> tags) {
        this(null, null, tags, null);
    }

    public MsgSetMeta(Credential cred) {
        this(null, null, null, cred);
    }

}
