package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * Created by gsokolov on 2/2/16.
 */
public class MsgServerMeta<Pu, Pr> {
    public String id;
    public String topic;
    public Date ts;
    public Desc desc;
    public Sub[] sub;

    public MsgServerMeta() {
    }

    public class Desc {
        public Date created;
        public Date updated;
        public Defacs defacs;
        public Acs acs;
        public int seq;
        public int read;
        public int recv;
        public int clear;
        @JsonProperty("public")
        public Pu pub;
        @JsonProperty("private")
        public Pr priv;
    }

    public class Sub {
        public String user;
        public Date updated;
        public String mode;
        public int read;
        public int recv;
        public int clear;
        @JsonProperty("private")
        public Pr priv;
        public boolean online;

        public String topic;
        public int seq;
        public String with;
        @JsonProperty("public")
        public Pu pub;
        public LastSeen seen;
    }
}
