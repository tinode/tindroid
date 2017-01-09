package co.tinode.tinodesdk.model;

/**
 * Playload for setting meta params.
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
