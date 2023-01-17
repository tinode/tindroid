package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Arrays;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Account credential: email, phone, captcha
 */
@JsonInclude(NON_DEFAULT)
public class Credential implements Comparable<Credential>, Serializable {
    public static final String METH_EMAIL = "email";
    public static final String METH_PHONE = "tel";

    // Confirmation method: email, phone, captcha.
    public String meth;
    // Credential to be validated, e.g. email or a phone number.
    public String val;
    // Confirmation response, such as '123456'.
    public String resp;
    // Confirmation parameters.
    public Object params;
    // Indicator if credential is validated.
    public Boolean done;

    public static Credential[] append(@Nullable Credential[] creds, @NotNull Credential c) {
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

    public boolean isDone() {
        return done != null && done;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return meth + ":" + val;
    }

    @Override
    public int compareTo(Credential other) {
        int r = meth.compareTo(other.meth);
        if (r ==0) {
            r = val.compareTo(other.val);
        }
        if (r == 0) {
            r = done.compareTo(other.done);
        }
        return r;
    }
}
