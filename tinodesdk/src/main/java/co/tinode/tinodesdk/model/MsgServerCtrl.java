package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * Control packet
 */
public class MsgServerCtrl implements Serializable {
    public String id;
    public String topic;
    public int code;
    public String text;
    public Date ts;
    public Map<String, Object> params;

    public MsgServerCtrl() {
    }

    @JsonIgnore
    private Object getParam(String key, Object def) {
        if (params == null) {
            return def;
        }
        Object result = params.get(key);
        return result != null ? result : def;
    }

    @JsonIgnore
    public Integer getIntParam(String key, Integer def) {
        return (Integer) getParam(key, def);
    }

    @JsonIgnore
    public String getStringParam(String key, String def) {
        return (String) getParam(key, def);
    }

    @JsonIgnore
    public Boolean getBoolParam(String key, Boolean def) {
        return (Boolean) getParam(key, def);
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public Iterator<String> getStringIteratorParam(String key) {
        Iterable<String> it = params != null ? (Iterable<String>) params.get(key) : null;
        return it != null && it.iterator().hasNext() ? it.iterator() : null;
    }
}
