package co.tinode.tindroid.account;

import android.accounts.Account;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

// Observer is called when contacts are updated. It forces the contact sync with the server.
// This observer is used only when the app is in the foreground.
public class ContactsObserver extends ContentObserver {
    private static final String TAG = "ContactsObserver";

    private Account mAcc;

    public ContactsObserver(Account acc) {
        super(null);
        mAcc = acc;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        Log.d(TAG, "Contacts have changed, requesting sync.");
        if (mAcc != null) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            ContentResolver.requestSync(mAcc, Utils.SYNC_AUTHORITY, bundle);
        } else {
            Log.i(TAG, "Failed to start sync: missing account");
        }

    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }
}
