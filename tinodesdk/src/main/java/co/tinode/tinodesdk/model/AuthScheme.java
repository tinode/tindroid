package co.tinode.tinodesdk.model;

/**
 * Authentication scheme for account creation.
 */
public class AuthScheme {
    public static final String LOGIN_BASIC = "basic";

    public String scheme;
    public String secret;

    public AuthScheme() {
    }

    public AuthScheme(String scheme, String secret) {
        this.scheme = scheme;
        this.secret = secret;
    }

    public static String makeBasicToken(String uname, String password) {
        return uname + ":" + password;
    }
}