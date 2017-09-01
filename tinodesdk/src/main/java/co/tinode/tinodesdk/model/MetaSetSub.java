package co.tinode.tinodesdk.model;

/**
 * Parameter of MsgSetMeta
 */

public class MetaSetSub {
    public String user;
    public String mode;
    public Object info;

    public MetaSetSub() {}

    public MetaSetSub(String user, String mode, Object info) {
        this.user = user;
        this.mode = mode;
        this.info = info;
    }
}
