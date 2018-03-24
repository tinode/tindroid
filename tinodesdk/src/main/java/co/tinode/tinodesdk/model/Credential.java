package co.tinode.tinodesdk.model;

import java.util.Arrays;

/**
 * Account credential: email, phone captcha
 */
public class Credential {
    // Confirmation method: email, phone, captcha.
    public String meth;
    // Credential to be confirmed, e.g. email or a phone number.
    public String val;
    // Confirmation response, such as '123456'.
    public String resp;
    // Confirmation parameters.
    public Object params;

    public static Credential[] append(Credential[] creds, Credential c) {
        if (creds == null) {
            creds = new Credential[1];
        } else {
            creds = Arrays.copyOf(creds, creds.length + 1);
        }
        creds[creds.length - 1] = c;

        return creds;
    }

    public Credential() {
    }

    public Credential(String meth, String val) {
        this.meth = meth;
        this.val = val;
    }

    public Credential(String meth, String val, String resp, Object params) {
        this(meth, val);
        this.resp = resp;
        this.params = params;
    }
}
