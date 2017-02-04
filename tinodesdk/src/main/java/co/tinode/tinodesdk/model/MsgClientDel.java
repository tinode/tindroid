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
    public Integer before;
    public int[] list;
    public Boolean hard;

    public MsgClientDel() {}

    public MsgClientDel(String id, String topic, What what, int before, boolean hard) {
        this.id = id;
        this.topic = topic;
        this.what = what == TOPIC ? STR_TOPIC : STR_MSG;
        // null value will cause before to be skipped during serialization instead of sending "0".
        this.before = (what == MSG && before > 0) ? before : null;
        this.hard = hard ? true : null;
    }

    public MsgClientDel(String id, String topic, What what, int before) {
        this(id, topic, what, before, false);
    }

    /**
     * Delete messages with seq IDs from the list.
     */
    public MsgClientDel(String id, String topic, int[] list, boolean hard) {
        this.id = id;
        this.topic = topic;
        this.what = STR_MSG;
        this.before = null;
        this.list = list;
        this.hard = hard ? true : null;
    }
}
