package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;

/**
 * Login packet.
 */

public class MsgClientLogin {

    public String id;
    public String scheme; // "basic" or "token"
    public String secret; // i.e. <uname + ":" + password> or token
    public Credential []cred;

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
