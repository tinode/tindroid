package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Info packet
 */
public class MsgServerInfo implements Serializable {
    public String topic;
    public String src;
    public String from;
    public String what;
    public Integer seq;
    // "event" and "payload" are video call event and its associated JSON payload.
    // Set only when what="call".
    public String event;
    public Object payload;

    public MsgServerInfo() {
    }
}
