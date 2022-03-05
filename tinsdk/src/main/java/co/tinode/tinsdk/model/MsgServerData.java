package co.tinode.tinsdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * Content packet
 */
public class MsgServerData implements Serializable {

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
}
