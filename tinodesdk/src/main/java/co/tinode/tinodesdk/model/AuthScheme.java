package co.tinode.tinodesdk.model;

/**
 * Helper of authentication scheme for account creation.
 */
public class AuthScheme {
    public static final String LOGIN_BASIC = "basic";

    public AuthScheme() {}

    public static byte[] makeBasicToken(String uname, String password) {
        // []byte will be base64-encoded by Jackson
        return (uname + ":" + password).getBytes();
    }
}