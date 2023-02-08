package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * Content packet
 */
public class MsgServerData implements Serializable {
    public enum WebRTC {ACCEPTED, BUSY, DECLINED, DISCONNECTED, FINISHED, MISSED, STARTED, UNKNOWN}

    public String id;
    public String topic;
    public Map<String, Object> head;
    public String from;
    public Date ts;
    public int seq;
    public Drafty content;

    public MsgServerData() {
    }

    @JsonIgnore
    public Object getHeader(String key) {
        return head == null ? null : head.get(key);
    }

    @JsonIgnore
    public int getIntHeader(String key, int def) {
        Object val = getHeader(key);
        if (val instanceof Integer) {
            return (int) val;
        }
        return def;
    }

    @JsonIgnore
    public String getStringHeader(String key) {
        Object val = getHeader(key);
        if (val instanceof String) {
            return (String) val;
        }
        return null;
    }

    @JsonIgnore
    public boolean getBooleanHeader(String key) {
        Object val = getHeader(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return false;
    }

    public static WebRTC parseWebRTC(String what) {
        if (what == null) {
            return WebRTC.UNKNOWN;
        } else if (what.equals("accepted")) {
            return WebRTC.ACCEPTED;
        } else if (what.equals("busy")) {
            return WebRTC.BUSY;
        } else if (what.equals("declined")) {
            return WebRTC.DECLINED;
        } else if (what.equals("disconnected")) {
            return WebRTC.DISCONNECTED;
        } else if (what.equals("finished")) {
            return WebRTC.FINISHED;
        } else if (what.equals("missed")) {
            return WebRTC.MISSED;
        } else if (what.equals("started")) {
            return WebRTC.STARTED;
        }
        return WebRTC.UNKNOWN;
    }
}
