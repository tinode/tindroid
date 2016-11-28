package co.tinode.tinodesdk.model;

import java.util.Date;
import java.util.Map;

/**
 * Control packet
 */
public class MsgServerCtrl {
    public String id;
    public String topic;
    public int code;
    public String text;
    public Date ts;
    public Map<String, Object> params;

    public MsgServerCtrl() {
    }
}
