package co.tinode.tinodesdk.model;

import static co.tinode.tinodesdk.model.MsgClientDel.What.MSG;
import static co.tinode.tinodesdk.model.MsgClientDel.What.TOPIC;

/**
 * Topic or message deletion packet
 *
 * 	Id    string `json:"id,omitempty"`
 *  Topic string `json:"topic"`
 *  // what to delete, either "msg" to delete messages (default) or "topic" to delete the topic
 *  What string `json:"what"`
 *  // Delete messages older than this seq ID (inclusive)
 *  Before int `json:"before"`
 *  // Request to hard-delete messages for all users, if such option is available.
 *  Hard bool `json:"hard,omitempty"`
 */

public class MsgClientDel {
    public enum What {TOPIC, MSG};

    public final static String STR_TOPIC = "topic";
    public final static String STR_MSG = "msg";

    public String id;
    public String topic;
    public String what;
    public int before;
    public boolean hard;

    public MsgClientDel() {}

    public MsgClientDel(String id, String topic, What what, int before, boolean hard) {
        this.id = id;
        this.topic = topic;
        this.what = what == TOPIC ? STR_TOPIC : STR_MSG;
        this.before = what == MSG ? before : 0;
        this.hard = hard;
    }

    public MsgClientDel(String id, String topic, What what, int before) {
        this(id, topic, what, before, false);
    }


}
