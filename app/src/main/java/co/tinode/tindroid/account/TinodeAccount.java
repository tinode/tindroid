package co.tinode.tindroid.account;

import java.util.Date;

/**
 * Account values for synchronization.
 */

public class TinodeAccount {
    private String Uid;
    private String Login;
    private String Name;
    private String Other;

    private boolean mDeleted;
    private boolean mUpdated;
    private Date mSyncState;
    //public Bitmap Photo;

    public TinodeAccount(String uid, String login, String name, String other) {
        Uid = uid;
        Login = login;
        Name = name;
        Other = other;
    }

    public String getDisplayName() {
        return Name;
    }

    public String getName() {
        return Login;
    }

    public String getUid() {
        return Uid;
    }

    public String getOther() {
        return Other;
    }

    public void setDeleted() {
        mDeleted = true;
    }

    public void setUpdated() {
        mUpdated = true;
    }

    public boolean isDeleted() {
        return mDeleted;
    }

    public boolean isUpdated() {
        return mUpdated;
    }

    public Date getSyncState() {
        return mSyncState;
    }

    public void setSyncState(Date state) {
        mSyncState = state;
    }
}
