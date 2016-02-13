package co.tinode.tinodesdk.model;

/**
 * Created by gene on 31/01/16.
 */

public class MsgClientLogin {
    public static final String LOGIN_BASIC = "basic";

    public String id;
    public String scheme; // "basic" or "token"
    public String secret; // <uname + ":" + password>
    public String ua; // user agent

    public MsgClientLogin() {
    }

    public MsgClientLogin(String id, String scheme, String secret, String userAgent) {
        this.id = id;
        this.scheme = scheme;
        this.secret = secret;
        this.ua = userAgent;
    }

    public void Login(String scheme, String secret, String userAgent) {
        this.scheme = scheme;
        this.secret = secret;
        this.ua = userAgent;
    }

    public void LoginBasic(String uname, String password, String userAgent) {
        Login(LOGIN_BASIC, uname + ":" + password, userAgent);
    }

    public static String makeBasicToken(String uname, String password) {
        return uname + ":" + password;
    }
}
