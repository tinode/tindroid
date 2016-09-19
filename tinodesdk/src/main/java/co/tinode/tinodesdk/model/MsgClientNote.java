package co.tinode.tinodesdk.model;

/**
 * Typing and read/received notifications packet.
 */
public class MsgClientNote {
    public String topic; // topic to notify, required
    public String what;  // one of "kp" (key press), "read" (read notification),
                // "rcpt" (received notification), any other string will cause
                // message to be silently dropped, required
    public Integer seq; // ID of the message being acknowledged, required for rcpt & read

    public MsgClientNote(String topic, String what, int seq) {
        this.topic = topic;
        this.what = what;
        this.seq = seq > 0 ? seq : null;
    }
}
