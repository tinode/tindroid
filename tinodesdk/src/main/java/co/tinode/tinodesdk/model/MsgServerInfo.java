package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Info packet
 */
public class MsgServerInfo implements Serializable {
    public String topic;
    public String from;
    public String what;
    public Integer seq;

    public MsgServerInfo() {
    }
}
