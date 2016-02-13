package co.tinode.tinodesdk.model;

import java.util.Map;

/**
 * Created by gsokolov on 2/2/16.
 */
public class MsgServerCtrl {
    public String id;
    public String topic;
    public int code;
    public String text;
    public Map<String, Object> params;

    public MsgServerCtrl() {
    }
}
