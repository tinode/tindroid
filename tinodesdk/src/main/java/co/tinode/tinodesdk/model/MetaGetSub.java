package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Parameter of MsgGetMeta
 */

@JsonInclude(NON_DEFAULT)
public class MetaGetSub {
    public String user;
    public String topic;
    public Date ims;
    public Integer limit;

    public MetaGetSub() {}

    public MetaGetSub(Date ims, Integer limit) {
        this.ims = ims;
        this.limit = limit;
    }

    @JsonIgnore
    public void setUser(String user) {
        this.user = user;
    }

    @JsonIgnore
    public void setTopic(String topic) {
        this.topic = topic;
    }
}
