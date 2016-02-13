package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Created by gsokolov on 2/2/16.
 */
public class MsgServerData<T> {
    public String id;
    public String topic;
    public String from;
    public Date ts;
    public int seq;
    public T content;

    public MsgServerData() {
    }
}
