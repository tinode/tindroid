package co.tinode.tinodesdk.model;

import java.util.List;

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
    public MsgDelRange[] delseq;
    public String user;
    public Boolean hard;

    public MsgClientDel() {}

    private MsgClientDel(String id, String topic, String what, MsgDelRange[] ranges, String user, boolean hard) {
        this.id = id;
        this.topic = topic;
        this.what = what;
        // null value will cause the field to be skipped during serialization instead of sending 0/null/[].
        this.delseq = what.equals(STR_MSG) ? ranges : null;
        this.user = what.equals(STR_SUB) ? user : null;
        this.hard = hard ? true : null;
    }

    /**
     * Delete messages with ids in the list
     */
    public MsgClientDel(String id, String topic, List<Integer> list, boolean hard) {
        this(id, topic, STR_MSG, MsgDelRange.listToRanges(list), null, hard);
    }

    /**
     * Delete all messages in the range
     */
    public MsgClientDel(String id, String topic, int fromId, int toId, boolean hard) {
        this(id, topic, STR_MSG, new MsgDelRange[]{new MsgDelRange(fromId, toId)}, null, hard);
    }

    /**
     * Delete one message.
     */
    public MsgClientDel(String id, String topic, int seqId, boolean hard) {
        this(id, topic, STR_MSG, new MsgDelRange[]{new MsgDelRange(seqId)}, null, hard);
    }

    /**
     * Delete topic
     */
    public MsgClientDel(String id, String topic) {
        this(id, topic, STR_TOPIC, null, null, false);
    }

    /**
     * Delete subscription of the given user. The server will reject request if the <i>user</i> is null.
     */
    public MsgClientDel(String id, String topic, String user) {
        this(id, topic, STR_SUB, null, user, false);
    }
}
