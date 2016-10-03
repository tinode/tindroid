package co.tinode.tinodesdk.model;

/**
 * Create client handshake packet.
 */
public class MsgClientHi {

    public String id;
    public String ver;
    public String ua;   // User Agent
    public String dev;  // Device ID

    public MsgClientHi(String id, String version, String userAgent, String deviceId) {
        this.id = id;
        this.ver = version;
        this.ua = userAgent;
        this.dev = deviceId;
    }
}
