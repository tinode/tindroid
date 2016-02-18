package co.tinode.tinodesdk.model;

/**
 * Created by gsokolov on 2/2/16.
 */
public class MsgClientNote {
    public String topic; // topic to notify, required
    public String what;  // one of "kp" (key press), "read" (read notification),
                // "rcpt" (received notification), any other string will cause
                // message to be silently dropped, required
    public int seq; // ID of the message being acknowledged, required for rcpt & read

    public MsgClientNote(String topic, String what, int seq) {
        this.topic = topic;
        this.what = what;
        this.seq = seq;
    }
}
