package co.tinode.tindroid.account;

import java.util.Date;

/**
 * Account values for synchronization.
 */
public class TinodeAccount {
    private String Uid;
    private String Name;

    private boolean mDeleted;
    private boolean mUpdated;
    private Date mSyncState;

    public TinodeAccount(String uid, String name) {
        Uid = uid;
        Name = name;
    }

    public String getDisplayName() {
        return Name;
    }

    public String getName() {
        return Uid;
    }

    public String getUid() {
        return Uid;
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
