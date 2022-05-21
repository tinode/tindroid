package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Typing and read/received notifications packet.
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientNote implements Serializable {
    public final String topic; // topic to notify, required
    public final String what;  // one of "kp" (key press), "read" (read notification),
                // "rcpt" (received notification), "call" (video call event),
                // any other string will cause
                // message to be silently dropped, required
    public final Integer seq; // ID of the message being acknowledged, required for rcpt & read

    public final String event; // Event (set only when what="call")
    public final Object payload; // Arbitrary json payload (set only when what="call")

    public MsgClientNote(String topic, String what, int seq) {
        this(topic, what, seq, null, null);
    }

    public MsgClientNote(String topic, String what, int seq, String event, Object payload) {
        this.topic = topic;
        this.what = what;
        this.seq = seq > 0 ? seq : null;
        this.event = event;
        this.payload = payload;
    }
}
