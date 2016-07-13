package co.tinode.tinodesdk.model;

/**
 * Created by gsokolov on 7/13/16.
 */

public class MsgClientHi {

    public String id;
    public String ver;
    public String ua; // user agent

    public MsgClientHi(String id, String version, String userAgent) {
        this.id = id;
        this.ver = version;
        this.ua = userAgent;
    }
}
