package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Map;

/**
 * Content packet
 */
public class MsgServerData {

    public String id;
    public String topic;
    public Map<String, String> head;
    public String from;
    public Date ts;
    public int seq;
    public Drafty content;

    public MsgServerData() {
    }

    @JsonIgnore
    public String getHeader(String key) {
        return head == null ? null : head.get(key);
    }
}
