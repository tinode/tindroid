package co.tinode.tinodesdk.model;

/**
 * Parameter of MsgSetMeta
 */

public class MetaSetSub<Inv> {
    public String user;
    public String mode;
    public Inv info;

    public MetaSetSub() {}

    public MetaSetSub(String user, String mode, Inv info) {
        this.user = user;
        this.mode = mode;
        this.info = info;
    }
}
