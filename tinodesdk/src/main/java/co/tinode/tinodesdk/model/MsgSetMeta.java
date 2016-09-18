package co.tinode.tinodesdk.model;

/**
 * Playload for setting meta params.
 */
public class MsgSetMeta<Pu,Pr,Inv> {

    public SetDesc<Pu,Pr> desc;
    public SetSub<Inv> sub;

    public MsgSetMeta() {}

    public MsgSetMeta(SetDesc<Pu,Pr> desc, SetSub<Inv> sub) {
        this.desc = desc;
        this.sub = sub;
    }

    public static class SetSub<Inv> {
        public String user;
        public String mode;
        public Inv info;

        public SetSub() {}

        public SetSub(String user, String mode, Inv info) {
            this.user = user;
            this.mode = mode;
            this.info = info;
        }
    }
}
