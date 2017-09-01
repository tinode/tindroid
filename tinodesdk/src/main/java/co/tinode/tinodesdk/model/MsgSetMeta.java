package co.tinode.tinodesdk.model;

/**
 * Playload for setting meta params, a combination of
 * MetaSetDesc and MetaSetSub.
 */
public class MsgSetMeta<Pu,Pr> {

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub sub;

    public MsgSetMeta() {}

    public MsgSetMeta(MetaSetDesc<Pu,Pr> desc, MetaSetSub sub) {
        this.desc = desc;
        this.sub = sub;
    }
}
