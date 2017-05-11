package co.tinode.tinodesdk.model;

/**
 * Topic or message deletion packet
 *
 * 	Id    string `json:"id,omitempty"`
 *  Topic string `json:"topic"`
 *  // what to delete, either "msg" to delete messages, "topic" to delete the topic, "sub" to delete subscription
 *  What string `json:"what"`
 *  // Delete messages older than this seq ID (inclusive)
 *  Before int `json:"before"`
 *  // Request to hard-delete messages for all users, if such option is available.
 *  Hard bool `json:"hard,omitempty"`
 */
public class MsgClientDel {
    private final static String STR_TOPIC = "topic";
    private final static String STR_MSG = "msg";
    private final static String STR_SUB = "sub";

    public String id;
    public String topic;
    public String what;
    public Integer before;
    public int[] list;
    public String user;
    public Boolean hard;

    public MsgClientDel() {}

    private MsgClientDel(String id, String topic, String what, int before, int[] list, String user, boolean hard) {
        this.id = id;
        this.topic = topic;
        this.what = what;
        // null value will cause the field to be skipped during serialization instead of sending 0/null/[].
        this.before = (what.equals(STR_MSG) && before > 0) ? before : null;
        this.list = (what.equals(STR_MSG) && this.before == null) ? list : null;
        this.user = what.equals(STR_SUB) ? user : null;
        this.hard = hard ? true : null;
    }

    /**
     * Delete messages with seq IDs from the list.
     */
    public MsgClientDel(String id, String topic) {
        this(id, topic, STR_TOPIC, 0, null, null, false);
    }

    public MsgClientDel(String id, String topic, int before, boolean hard) {
        this(id, topic, STR_MSG, before, null, null, hard);
    }

    /**
     * Delete messages with seq IDs from the list.
     */
    public MsgClientDel(String id, String topic, int[] list, boolean hard) {
        this(id, topic, STR_MSG, 0, list, null, hard);
    }

    /**
     * Delete subscription of the given user. The server will reject request if the <i>user</i> is null.
     */
    public MsgClientDel(String id, String topic, String user) {
        this(id, topic, STR_SUB, 0, null, user, false);
    }
}
