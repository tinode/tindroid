package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by gsokolov on 2/12/16.
 */
public class MsgSetMeta<Pu,Pr,Inv> {

    public Desc<Pu,Pr> desc;
    public Sub<Inv> sub;

    public class Desc<Pu,Pr> {
        public Defacs defacs;
        @JsonProperty("public")
        public Pu pub;
        @JsonProperty("private")
        public Pr priv;
    }

    public class Sub<Inv> {
        public String user;
        public String mode;
        public Inv info;
    }
}
