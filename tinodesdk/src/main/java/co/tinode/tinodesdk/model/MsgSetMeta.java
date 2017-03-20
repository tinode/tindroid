package co.tinode.tinodesdk.model;

/**
 * Playload for setting meta params, a combination of
 * MetaSetDesc and MetaSetSub.
 */
public class MsgSetMeta<Pu,Pr,Inv> {

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub<Inv> sub;

    public MsgSetMeta() {}

    public MsgSetMeta(MetaSetDesc<Pu,Pr> desc, MetaSetSub<Inv> sub) {
        this.desc = desc;
        this.sub = sub;
    }
}
