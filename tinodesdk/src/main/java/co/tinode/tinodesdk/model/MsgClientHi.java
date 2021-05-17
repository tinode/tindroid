package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Create client handshake packet.
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientHi implements Serializable {

    public final String id;
    public final String ver;
    public final String ua;   // User Agent
    public final String dev;  // Device ID
    public final String lang;
    public final Boolean bkg;

    public MsgClientHi(String id, String version, String userAgent, String deviceId, String lang, Boolean background) {
        this.id = id;
        this.ver = version;
        this.ua = userAgent;
        this.dev = deviceId;
        this.lang = lang;
        this.bkg = background;
    }
}
