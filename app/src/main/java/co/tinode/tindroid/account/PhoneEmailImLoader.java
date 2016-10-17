package co.tinode.tindroid.account;

import android.content.Context;
import android.database.ContentObserver;
import android.provider.ContactsContract;
import android.support.v4.content.AsyncTaskLoader;
import android.util.SparseArray;

/**
 * Loader for phone numbers, emails and tinode im handles.
 * Used to differentiate between contacts already on Tinode vs those who are not.
 */
public class PhoneEmailImLoader extends AsyncTaskLoader<SparseArray<Utils.ContactHolder>> {
    private static final String TAG = "PhoneEmailImLoader";

    private SparseArray<Utils.ContactHolder> mOldData;
    private ContentObserver mObserver;

    public PhoneEmailImLoader(Context context) {
        super(context);
    }

    @Override
    protected void onStartLoading() {
        if (mOldData != null) {
            super.deliverResult(mOldData);
        }

        // Begin monitoring the underlying data source.
        if (mObserver == null) {
            mObserver = new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    onContentChanged();
                }
            };
            getContext().getContentResolver().registerContentObserver(ContactsContract.Data.CONTENT_URI,
                    true, mObserver);
        }

        if (takeContentChanged() || mOldData == null) {
            // When the observer detects a change, it should call onContentChanged()
            // on the Loader, which will cause the next call to takeContentChanged()
            // to return true. If this is ever the case (or if the current data is
            // null), we force a new load.
            forceLoad();
            super.onStartLoading();
        }
    }

    @Override
    public SparseArray<Utils.ContactHolder> loadInBackground() {
        return Utils.fetchEmailsAndPhones(getContext().getContentResolver(),
                ContactsContract.Data.CONTENT_URI);
    }

    @Override
    public void deliverResult(SparseArray<Utils.ContactHolder> newData) {
        mOldData = newData;

        if (isStarted()) {
            super.deliverResult(newData);
        }
    }

    @Override
    public void onStopLoading() {
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
        }
        super.onStopLoading();
    }
}