package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Map;

/**
 * Content packet
 */
public class MsgServerData<T> {

    public String id;
    public String topic;
    public Map<String, String> head;
    public String from;
    public Date ts;
    public int seq;
    public T content;

    // Local/calculated
    //public boolean isMine;
    //protected DisplayAs mDisplay;

    public MsgServerData() {
    }

    @JsonIgnore
    public String getHeader(String key) {
        return head == null ? null : head.get(key);
    }
}
