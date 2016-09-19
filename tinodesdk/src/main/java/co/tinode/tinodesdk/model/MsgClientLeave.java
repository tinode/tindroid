package co.tinode.tinodesdk.model;

/**
 * Topic unsubscribe packet.
 */
public class MsgClientLeave {
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
