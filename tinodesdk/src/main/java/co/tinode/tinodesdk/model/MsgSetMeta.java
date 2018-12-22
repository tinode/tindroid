package co.tinode.tinodesdk.model;

import java.util.List;

/**
 * Playload for setting meta params, a combination of
 * MetaSetDesc and MetaSetSub.
 */
public class MsgSetMeta<Pu,Pr> {

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub sub;
    public String[] tags;

    public MsgSetMeta() {}

    public MsgSetMeta(MetaSetDesc<Pu,Pr> desc, MetaSetSub sub, List<String> tags) {
        this.desc = desc;
        this.sub = sub;
        if (tags != null) {
            this.tags = tags.toArray(this.tags);
        }
    }

    public MsgSetMeta(MetaSetDesc<Pu,Pr> desc) {
        this(desc, null, null);
    }

    public MsgSetMeta(MetaSetSub sub) {
        this(null, sub, null);
    }

    public MsgSetMeta(List<String> tags) {
        this(null, null, tags);
    }
}
