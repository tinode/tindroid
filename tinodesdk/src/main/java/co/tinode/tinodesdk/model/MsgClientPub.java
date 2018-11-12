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
    public Map<String, Object> head;
    public Object content;

    public MsgClientPub() {
    }

    public MsgClientPub(String id, String topic, Boolean noecho, Object content) {
        this.id = id;
        this.topic = topic;
        this.noecho = noecho ? true : null;
        this.content = content;
        if (content instanceof Drafty) {
            Drafty d = (Drafty) content;
            setHeader("mime", Drafty.MIME_TYPE);
            String[] refs = d.getEntReferences();
            if (refs != null) {
                setHeader("attachments", d.getEntReferences());
            }
        }
    }

    @JsonIgnore
    public Object setHeader(String key, Object value) {
        if (value != null) {
            if (this.head == null) {
                this.head = new HashMap<>();
            }
            return this.head.put(key, value);
        }
        return null;
    }
}
