package co.tinode.tinodesdk.model;

/**
 * Metadata query packet
 * 	Id    string `json:"id,omitempty"`
 *  Topic string `json:"topic"`
 *  What string `json:"what"`
 *  Desc *MsgGetOpts `json:"desc,omitempty"`
 *  Sub *MsgGetOpts `json:"sub,omitempty"`
 *  Data *MsgBrowseOpts `json:"data,omitempty"`
 */

public class MsgClientGet {
    public String id;
    public String topic;
    public String what;

    public MetaGetDesc desc;
    public MetaGetSub sub;
    public MetaGetData data;

    public MsgClientGet() {}

    public MsgClientGet(String id, String topic, MsgGetMeta query) {
        this.id = id;
        this.topic = topic;
        this.what = query.what;
        this.desc = query.desc;
        this.sub = query.sub;
        this.data = query.data;
    }

    public MsgClientGet(String id, String topic, MetaGetDesc desc,
                        MetaGetSub sub, MetaGetData data) {
        this.id = id;
        this.topic = topic;
        this.what = "";
        if (desc != null) {
            this.what = "desc";
            this.desc = desc;
        }
        if (sub != null) {
            this.what += " sub";
            this.sub = sub;
        }
        if (data != null) {
            this.what += " data";
            this.data = data;
        }
        this.what = this.what.trim();
    }
}
