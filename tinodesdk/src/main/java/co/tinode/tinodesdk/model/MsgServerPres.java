package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Presence notification.
 */
public class MsgServerPres implements Serializable {
    public enum What {ON, OFF, UPD, GONE, TERM, ACS, MSG, UA, RECV, READ, DEL, TAGS, AUX, UNKNOWN}

    public String topic;
    public String src;
    public String what;
    public Integer seq;
    public Integer clear;
    public MsgRange[] delseq;
    public String ua;
    public String act;
    public String tgt;
    public AccessChange dacs;

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
        } else if (what.equals("acs")) {
            return What.ACS;
        } else if (what.equals("gone")) {
            return What.GONE;
        } else if (what.equals("term")) {
            return What.TERM;
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
        } else if (what.equals("tags")) {
            return What.TAGS;
        }  else if (what.equals("aux")) {
            return What.AUX;
        }
        return What.UNKNOWN;
    }
}
