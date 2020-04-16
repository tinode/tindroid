package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Topic unsubscribe packet.
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientLeave implements Serializable {
    public String id;
    public String topic;
    public Boolean unsub;

    public MsgClientLeave() {
    }

    public MsgClientLeave(String id, String topic, boolean unsub) {
        this.id = id;
        this.topic = topic;
        this.unsub = unsub ? true : null;
    }
}
