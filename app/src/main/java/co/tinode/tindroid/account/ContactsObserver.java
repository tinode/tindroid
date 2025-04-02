package co.tinode.tindroid.account;

import android.accounts.Account;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

// Observer is called when contacts are updated. It forces the contact sync with the server.
// This observer is used only when the app is in the foreground.
public class ContactsObserver extends ContentObserver {
    private static final String TAG = "ContactsObserver";

    private final Account mAcc;

    public ContactsObserver(Account acc, Handler handler) {
        super(handler);
        mAcc = acc;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if (selfChange) {
            return;
        }

        if (mAcc != null) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            ContentResolver.requestSync(mAcc, Utils.SYNC_AUTHORITY, bundle);
        } else {
            Log.w(TAG, "Failed to start sync: missing account");
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }
}
