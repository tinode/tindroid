package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.core.Base64Variants;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.StringTokenizer;

/**
 * Helper of authentication scheme for account creation.
 */
public class AuthScheme implements Serializable {
    public static final String LOGIN_BASIC = "basic";
    public static final String LOGIN_TOKEN = "token";
    public static final String LOGIN_RESET = "reset";

    public String scheme;
    public String secret;

    public AuthScheme() {}

    public AuthScheme(String scheme, String secret) {
        this.scheme = scheme;
        this.secret = secret;
    }

    @Override
    public String toString() {
        return scheme + ":" + secret;
    }

    public static AuthScheme parse(String s) {
        if (s != null) {
            StringTokenizer st = new StringTokenizer(s, ":");
            if (st.countTokens() == 2) {
                String scheme = st.nextToken();
                if (scheme.contentEquals(LOGIN_BASIC) || scheme.contentEquals(LOGIN_TOKEN)) {
                    return new AuthScheme(scheme, st.nextToken());
                }
            } else {
                throw new IllegalArgumentException();
            }
        }
        return null;
    }

    public static String encodeBasicToken(String uname, String password) {
        // Encode string as base64
        try {
            if (uname.contains(":")) {
                throw new IllegalArgumentException("illegal character ':' in user name '" + uname + "'");
            }
            password = password == null ? "" : password;
            return Base64Variants.getDefaultVariant().encode((uname + ":" + password).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ignored) {}
        return null;
    }

    public static String encodeResetSecret(String scheme, String method, String value) {
        // Join parts using ":" then base64-encode.
        if (scheme == null || method == null || value == null) {
            throw new IllegalArgumentException("illegal 'null' parameter");
        }
        if (scheme.contains(":") || method.contains(":") || value.contains(":")) {
            throw new IllegalArgumentException("illegal character ':' in parameter");
        }
        try {
            return Base64Variants.getDefaultVariant().encode((scheme + ":" + method + ":" + value)
                    .getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ignored) {}
        return null;
    }

    public static String[] decodeBasicToken(String token) {
        String basicToken;
        try {
            // Decode base64 string
            basicToken = new String(Base64Variants.getDefaultVariant().decode(token), "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            return null;
        }

        // Split "login:password" into parts.
        int splitAt = basicToken.indexOf(':');
        if (splitAt <= 0) {
            return null;
        }

        return new String[] {
                basicToken.substring(0, splitAt),
                splitAt == basicToken.length() - 1 ? "" : basicToken.substring(splitAt+1, basicToken.length()-1)
        };
    }


    public static AuthScheme basicInstance(String login, String password) {
        return new AuthScheme(LOGIN_BASIC, encodeBasicToken(login, password));
    }

    public static AuthScheme tokenInstance(String secret) {
        return new AuthScheme(LOGIN_TOKEN, secret);
    }
}