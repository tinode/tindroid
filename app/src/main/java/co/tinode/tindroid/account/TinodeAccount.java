package co.tinode.tindroid.account;

/**
 * Account values for synchronization.
 */

public class TinodeAccount {
    public String Uid;
    public String Login;
    public String Name;
    public String Other;
    //public Bitmap Photo;

    public TinodeAccount(String uid, String login, String name, String other) {
        Uid = uid;
        Login = login;
        Name = name;
        Other = other;
    }
}
