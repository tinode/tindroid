package co.tinode.tindroid.account;

/**
 * Account values for synchronization.
 */

public class TinodeAccount {
    public String Uid;
    public String Login;
    public String Name;
    //public Bitmap Photo;

    public TinodeAccount(String uid, String login, String name) {
        Uid = uid;
        Login = login;
        Name = name;
    }
}
