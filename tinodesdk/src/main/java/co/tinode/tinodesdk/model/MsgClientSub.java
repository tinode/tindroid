package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Subscribe to topic packet.
 *
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientSub<Pu,Pr> implements Serializable {
    public String id;
    public String topic;
    public MsgSetMeta<Pu,Pr> set;
    public MsgGetMeta get;

    public MsgClientSub() {}

    public MsgClientSub(String id, String topic, MsgSetMeta<Pu,Pr> set, MsgGetMeta get) {
        this.id = id;
        this.topic = topic;
        this.set = set;
        this.get = get;
    }
}
