package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.core.Base64Variants;

/**
 * Helper of authentication scheme for account creation.
 */
public class AuthScheme {
    public static final String LOGIN_BASIC = "basic";
    public static final String LOGIN_TOKEN = "token";

    public AuthScheme() {}

    public static String makeBasicToken(String uname, String password) {
        // Encode string as base64
        return Base64Variants.getDefaultVariant().encode((uname + ":" + password).getBytes());
    }
}