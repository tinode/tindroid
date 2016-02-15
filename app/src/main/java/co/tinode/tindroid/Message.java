package co.tinode.tindroid;

import java.util.Date;
/**
 * Created by gsokolov on 2/5/16.
 */
public class Message<T> {
    public String topic;
    public String from;
    public Date ts;
    public int seq;

    public T content;

    public Message() {}
}
