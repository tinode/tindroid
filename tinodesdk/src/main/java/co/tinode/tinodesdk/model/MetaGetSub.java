package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Parameter of MsgGetMeta
 */
@JsonInclude(NON_DEFAULT)
public class MetaGetSub implements Serializable {
    public String user;
    public String topic;
    public Date ims;
    public Integer limit;

    public MetaGetSub() {}

    public MetaGetSub(Date ims, Integer limit) {
        this.ims = ims;
        this.limit = limit;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    @NotNull
    @Override
    public String toString() {
        return "user=[" + user + "]," +
                " topic=[" + topic + "]," +
                " ims=[" + ims + "]," +
                " limit=[" + limit + "]";
    }
}
