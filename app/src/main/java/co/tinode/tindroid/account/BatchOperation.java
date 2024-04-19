package co.tinode.tindroid.account;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This class handles execution of batch mOperations on Contacts provider.
 */
final public class BatchOperation {
    private final static String TAG = "BatchOperation";

    private final ContentResolver mResolver;
    // List for storing the batch mOperations
    private final ArrayList<ContentProviderOperation> mOperations;

    BatchOperation(ContentResolver resolver) {
        mResolver = resolver;
        mOperations = new ArrayList<>();
    }

    public int size() {
        return mOperations.size();
    }

    public void add(ContentProviderOperation cpo) {
        mOperations.add(cpo);
    }

    @SuppressWarnings("UnusedReturnValue")
    List<Uri> execute() {
        List<Uri> resultUris = new ArrayList<>();
        if (mOperations.isEmpty()) {
            return resultUris;
        }
        // Apply the mOperations to the content provider
        try {
            ContentProviderResult[] results = mResolver.applyBatch(ContactsContract.AUTHORITY, mOperations);
            for (ContentProviderResult result : results) {
                resultUris.add(result.uri);
            }
        } catch (final OperationApplicationException | RemoteException e) {
            Log.e(TAG, "storing contact data failed", e);
        }
        mOperations.clear();
        return resultUris;
    }
}

