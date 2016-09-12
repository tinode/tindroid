package co.tinode.tinodesdk.model;

/**
 * Login packet.
 */

public class MsgClientLogin {

    public String id;
    public String scheme; // "basic" or "token"
    public String secret; // i.e. <uname + ":" + password> or token

    public MsgClientLogin() {
    }

    public MsgClientLogin(String id, String scheme, String secret) {
        this.id = id;
        this.scheme = scheme;
        this.secret = secret;
    }

    public void Login(String scheme, String secret) {
        this.scheme = scheme;
        this.secret = secret;
    }
}
