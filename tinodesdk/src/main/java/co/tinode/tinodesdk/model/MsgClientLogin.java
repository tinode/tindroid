package co.tinode.tinodesdk.model;

/**
 * Created by gene on 31/01/16.
 */

public class MsgClientLogin {
    public static final String LOGIN_BASIC = "basic";

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
        Login(LOGIN_BASIC, uname + ":" + password);
    }

    public static String makeBasicToken(String uname, String password) {
        return uname + ":" + password;
    }
}
