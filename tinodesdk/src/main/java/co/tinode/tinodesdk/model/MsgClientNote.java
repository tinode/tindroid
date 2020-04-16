package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Typing and read/received notifications packet.
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientNote implements Serializable {
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
