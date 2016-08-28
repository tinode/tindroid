package co.tinode.tinodesdk.model;

/**
 * Helper of authentication scheme for account creation.
 */
public class AuthScheme {
    public static final String LOGIN_BASIC = "basic";

    public AuthScheme() {}

    public static String makeBasicToken(String uname, String password) {
        return uname + ":" + password;
    }
}