package co.tinode.tinodesdk.model;

/**
 * Account creation packet
 */
public class MsgClientAcc<Pu,Pr> {
    private static final String USER_NEW = "new";

    public String id;
    public String user;
    public AuthScheme[] auth;
    // Use the names auth scheme for immediate authentication.
    public String login;
    // Account parameters
    public SetDesc<Pu,Pr> desc;

    public MsgClientAcc(String id, AuthScheme[] auth, String useScheme, SetDesc<Pu, Pr> desc) {
        this.id = id;
        this.user = USER_NEW;
        this.auth = auth;
        this.login = useScheme;
        this.desc = desc;
    }
}
