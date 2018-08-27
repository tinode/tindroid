package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * Control packet
 */
public class MsgServerCtrl {
    public String id;
    public String topic;
    public int code;
    public String text;
    public Date ts;
    public Map<String, Object> params;

    public MsgServerCtrl() {
    }

    @JsonIgnore
    public Integer getIntParam(String key) {
        return params != null ? (Integer) params.get(key) : null;
    }

    @JsonIgnore
    public String getStringParam(String key) {
        return params != null ? (String) params.get(key) : null;
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public Iterator<String> getStringIteratorParam(String key) {
        Iterable<String> it = params != null ? (Iterable<String>) params.get(key) : null;
        return it != null && it.iterator().hasNext() ? it.iterator() : null;
    }
}
