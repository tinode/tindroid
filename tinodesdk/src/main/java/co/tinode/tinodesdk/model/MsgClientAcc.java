package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;

/**
 * Account creation packet
 */
public class MsgClientAcc<Pu,Pr> {
    private static final String USER_NEW = "new";

    public String id;
    public String user;
    public String scheme;
    public String secret;
    // Use the new account for immediate authentication.
    public Boolean login;
    public String[] tags;
    public Credential[] cred;
    // New account parameters
    public MetaSetDesc<Pu,Pr> desc;

    public MsgClientAcc(String id, String uid, String scheme, String secret, boolean doLogin,
                        MetaSetDesc<Pu, Pr> desc) {
        this.id = id;
        this.user = uid == null ? USER_NEW : uid;
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
}
