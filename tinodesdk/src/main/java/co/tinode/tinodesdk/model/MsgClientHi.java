package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Create client handshake packet.
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientHi implements Serializable {

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
