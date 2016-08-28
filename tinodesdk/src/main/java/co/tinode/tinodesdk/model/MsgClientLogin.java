package co.tinode.tinodesdk.model;

/**
 * Login packet.
 */

public class MsgClientLogin {

    public String id;
    public String scheme; // "basic" or "token"
    public String secret; // <uname + ":" + password>

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

    public void LoginBasic(String uname, String password) {
        Login(AuthScheme.LOGIN_BASIC, uname + ":" + password);
    }
}
