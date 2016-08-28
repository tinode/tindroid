package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Playload for setting meta params.
 */
public class MsgSetMeta<Pu,Pr,Inv> {

    public SetDesc<Pu,Pr> desc;
    public SetSub<Inv> sub;

    public class SetSub<Inv> {
        public String user;
        public String mode;
        public Inv info;

        public SetSub() {}
    }

    public MsgSetMeta() {}
}
