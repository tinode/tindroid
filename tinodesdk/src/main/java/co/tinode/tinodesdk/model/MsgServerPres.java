package co.tinode.tinodesdk.model;

/**
 * Presence notification.
 */
public class MsgServerPres {
    public enum What {ON, OFF, UPD, UNSUB, GONE, MSG, UA, RECV, READ, DEL, UNKNOWN}

    public String topic;
    public String src;
    public String what;
    public Integer seq;
    public String ua;

    public MsgServerPres() {
    }

    public static What parseWhat(String what) {
        if (what == null) {
            return What.UNKNOWN;
        } else if (what.equals("on")) {
            return What.ON;
        } else if (what.equals("off")) {
            return What.OFF;
        } else if (what.equals("upd")) {
            return What.UPD;
        } else if (what.equals("unsub")) {
            return What.UNSUB;
        } else if (what.equals("gone")) {
            return What.GONE;
        } else if (what.equals("msg")) {
            return What.MSG;
        } else if (what.equals("ua")) {
            return What.UA;
        } else if (what.equals("recv")) {
            return What.RECV;
        } else if (what.equals("read")) {
            return What.READ;
        } else if (what.equals("del")) {
            return What.DEL;
        } else {
            return What.UNKNOWN;
        }
    }
}
