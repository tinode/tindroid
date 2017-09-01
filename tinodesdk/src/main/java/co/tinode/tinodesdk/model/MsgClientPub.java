package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Map;

/**
 * Publish to topic packet.
 */
public class MsgClientPub {
    public String id;
    public String topic;
    public Boolean noecho;
    public Map<String, String> head;
    public Object content;

    public MsgClientPub() {
    }

    public MsgClientPub(String id, String topic, Boolean noecho, Object content) {
        this.id = id;
        this.topic = topic;
        this.noecho = noecho ? true : null;
        this.content = content;
        if (content instanceof Drafty) {
            setHeader("mime", Drafty.MIME_TYPE);
        }
    }

    @JsonIgnore
    public String setHeader(String key, String value) {
        if (this.head == null) {
            this.head = new HashMap<>();
        }
        return this.head.put(key, value);
    }
}
