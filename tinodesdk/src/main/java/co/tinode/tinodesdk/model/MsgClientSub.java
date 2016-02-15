package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by gene on 31/01/16.
 *
 */

public class MsgClientSub<Pu,Pr,Inv> {
    public String id;
    public String topic;
    public MsgSetMeta<Pu,Pr,Inv> set;
    public MsgGetMeta get;

    public MsgClientSub(String id, String topic, MsgSetMeta<Pu,Pr,Inv> set, MsgGetMeta get) {
        this.id = id;
        this.topic = topic;
        this.set = set;
        this.get = get;
    }
}
