package co.tinode.tinodesdk.model;

/**
 * Create client handshake packet.
 */
public class MsgClientHi {

    public String id;
    public String ver;
    public String ua;   // User Agent
    public String dev;  // Device ID
    public String lang;

    public MsgClientHi(String id, String version, String userAgent, String deviceId, String lang) {
        this.id = id;
        this.ver = version;
        this.ua = userAgent;
        this.dev = deviceId;
        this.lang = lang;
    }
}
