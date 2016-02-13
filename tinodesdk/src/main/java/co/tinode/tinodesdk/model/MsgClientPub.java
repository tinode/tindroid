package co.tinode.tinodesdk.model;

/**
 * Created by gene on 31/01/16.
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
