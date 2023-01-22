package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Arrays;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Account creation packet
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientAcc<Pu,Pr> implements Serializable {
    public final String id;
    public final String user;
    public String tmpscheme;
    public String tmpsecret;
    public final String scheme;
    public final String secret;
    // Use the new account for immediate authentication.
    public final Boolean login;
    public String[] tags;
    public Credential[] cred;
    // New account parameters
    public final MetaSetDesc<Pu,Pr> desc;

    public MsgClientAcc(String id, String uid, String scheme, String secret, boolean doLogin,
                        MetaSetDesc<Pu, Pr> desc) {
        this.id = id;
        this.user = uid;
        this.scheme = scheme;
        this.secret = secret;
        this.login = doLogin;
        this.desc = desc;
    }

    @JsonIgnore
    public void addTag(String tag) {
        if (tags == null) {
            tags = new String[1];
        } else {
            tags = Arrays.copyOf(tags, tags.length + 1);
        }
        tags[tags.length-1] = tag;
    }

    @JsonIgnore
    public void addCred(Credential c) {
        if (cred == null) {
            cred = new Credential[1];
        } else {
            cred = Arrays.copyOf(cred, cred.length + 1);
        }
        cred[cred.length-1] = c;
    }

    @JsonIgnore
    public void setTempAuth(String scheme, String secret) {
        this.tmpscheme = scheme;
        this.tmpsecret = secret;
    }
}
