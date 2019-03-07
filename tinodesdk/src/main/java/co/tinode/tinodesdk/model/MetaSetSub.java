package co.tinode.tinodesdk.model;

/**
 * Parameter of MsgSetMeta
 */

public class MetaSetSub {
    public String user;
    public String mode;

    public MetaSetSub() {}

    public MetaSetSub(String mode) {
        this.user = null;
        this.mode = mode;
    }

    public MetaSetSub(String user, String mode) {
        this.user = user;
        this.mode = mode;
    }
}
