package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by gsokolov on 2/12/16.
 */
public class MsgSetMeta<Pu,Pr,Inv> {

    public SetDesc<Pu,Pr> desc;
    public SetSub<Inv> sub;

    public class SetDesc<Pu,Pr> {
        public Defacs defacs;
        @JsonProperty("public")
        public Pu pub;
        @JsonProperty("private")
        public Pr priv;

        public SetDesc() {}
    }

    public class SetSub<Inv> {
        public String user;
        public String mode;
        public Inv info;

        public SetSub() {}
    }

    public MsgSetMeta() {}
}
