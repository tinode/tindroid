package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import co.tinode.tinodesdk.Tinode;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Topic or message deletion packet
 * <p>
 * 	Id    string `json:"id,omitempty"`
 *  Topic string `json:"topic"`
 *  // what to delete, either "msg" to delete messages, "topic" to delete the topic, "sub" to delete subscription
 *  What string `json:"what"`
 *  // Delete messages older than this seq ID (inclusive)
 *  Before int `json:"before"`
 *  // Request to hard-delete messages for all users, if such option is available.
 *  Hard bool `json:"hard,omitempty"`
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientDel implements Serializable {
    private final static String STR_TOPIC = "topic";
    private final static String STR_MSG = "msg";
    private final static String STR_SUB = "sub";
    private final static String STR_CRED = "cred";
    private final static String STR_USER = "user";

    public String id;
    public String topic;
    public String what;
    public MsgRange[] delseq;
    public String user;
    public Credential cred;
    public Boolean hard;

    public MsgClientDel() {}

    private MsgClientDel(String id, String topic, String what, MsgRange[] ranges, String user, Credential cred, boolean hard) {
        this.id = id;
        this.topic = topic;
        this.what = what;
        // null value will cause the field to be skipped during serialization instead of sending 0/null/[].
        this.delseq = what.equals(STR_MSG) ? ranges : null;
        this.user = what.equals(STR_SUB) || what.equals(STR_USER) ? user : null;
        this.cred = what.equals(STR_CRED) ? cred : null;
        this.hard = hard ? true : null;
    }

    /**
     * Delete all messages in multiple ranges
     */
    public MsgClientDel(String id, String topic, MsgRange[] ranges, boolean hard) {
        this(id, topic, STR_MSG, ranges, null, null, hard);
    }


    /**
     * Delete all messages in one range.
     */
    public MsgClientDel(String id, String topic, int fromId, int toId, boolean hard) {
        this(id, topic, new MsgRange[]{new MsgRange(fromId, toId)}, hard);
    }

    /**
     * Delete one message.
     */
    public MsgClientDel(String id, String topic, int seqId, boolean hard) {
        this(id, topic, STR_MSG, new MsgRange[]{new MsgRange(seqId)}, null, null, hard);
    }

    /**
     * Delete topic
     */
    public MsgClientDel(String id, String topic) {
        this(id, topic, STR_TOPIC, null, null, null, false);
    }

    /**
     * Delete current user.
     */
    public MsgClientDel(String id) {
        this(id, null, STR_USER, null, null, null, false);
    }


    /**
     * Delete subscription of the given user. The server will reject request if the <i>user</i> is null.
     */
    public MsgClientDel(String id, String topic, String user) {
        this(id, topic, STR_SUB, null, user, null, false);
    }

    /**
     * Delete selected credential.
     */
    public MsgClientDel(String id, Credential cred) {
        this(id, Tinode.TOPIC_ME, STR_CRED, null, null, cred, false);
    }
}
