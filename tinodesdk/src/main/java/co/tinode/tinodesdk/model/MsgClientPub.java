package co.tinode.tinodesdk.model;

/**
 * Publish to topic packet.
 */
public class MsgClientPub<T> {
    public String id;
    public String topic;
    public Boolean noecho;
    public T content;

    public MsgClientPub() {
    }

    public MsgClientPub(String id, String topic, Boolean noecho, T content) {
        this.id = id;
        this.topic = topic;
        this.noecho = noecho;
        this.content = content;
    }
}
