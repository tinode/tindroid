package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Publish to topic packet.
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientPub implements Serializable {
    public String id;
    public String topic;
    public Boolean noecho;
    public Map<String, Object> head;
    public Object content;

    public MsgClientPub() {
    }

    public MsgClientPub(String id, String topic, Boolean noecho, Object content, Map<String, Object> head) {
        this.id = id;
        this.topic = topic;
        this.noecho = noecho ? true : null;
        this.content = content;
        this.head = head;
    }
}
