package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Info packet
 */
public class MsgServerInfo implements Serializable {
    public enum What {CALA, CALL, KP, KPA, KPV, RECV, READ, UNKNOWN}
    public enum Event {ACCEPT, ANSWER, HANG_UP, ICE_CANDIDATE, OFFER, RINGING, UNKNOWN}

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

    public static What parseWhat(String what) {
        if (what == null) {
            return What.UNKNOWN;
        } else if (what.equals("kp")) {
            return What.KP;
        } else if (what.equals("kpa")) {
            return What.KPA;
        } else if (what.equals("kpv")) {
            return What.KPV;
        } else if (what.equals("recv")) {
            return What.RECV;
        } else if (what.equals("read")) {
            return What.READ;
        } else if (what.equals("call")) {
            return What.CALL;
        } else if (what.equals("cala")) {
            return What.CALA;
        }
        return What.UNKNOWN;
    }

    public static Event parseEvent(String event) {
        if (event == null) {
            return Event.UNKNOWN;
        } else if (event.equals("accept")) {
            return Event.ACCEPT;
        } else if (event.equals("answer")) {
            return Event.ANSWER;
        } else if (event.equals("hang-up")) {
            return Event.HANG_UP;
        } else if (event.equals("ice-candidate")) {
            return Event.ICE_CANDIDATE;
        } else if (event.equals("offer")) {
            return Event.OFFER;
        } else if (event.equals("ringing")) {
            return Event.RINGING;
        }
        return Event.UNKNOWN;
    }
}
