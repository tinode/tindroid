package co.tinode.tinodesdk.model;

/**
 * Parameter of MsgSetMeta
 */

public class MetaSetSub {
    public String user;
    public String mode;

    public MetaSetSub() {}

    public MetaSetSub(String user, String mode) {
        this.user = user;
        this.mode = mode;
    }
}
