package co.tinode.tindroid.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Service to handle account sync requests.
 * <p>
 * The service is started as:
 * ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, bundle);
 * ContentResolver.setSyncAutomatically(acc, Utils.SYNC_AUTHORITY, true);
 * <p>
 * For performance, only one sync adapter will be initialized within this application's context.
 * <p>
 * Note: The SyncService itself is not notified when a new sync occurs. It's role is to
 * manage the lifecycle of our {@link ContactsSyncAdapter} and provide a handle to said SyncAdapter to the
 * OS on request.
 */
public class ContactsSyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();

    // Verbatim copy of
    // https://developer.android.com/training/sync-adapters/creating-sync-adapter
    @SuppressLint("StaticFieldLeak")
    private static ContactsSyncAdapter sSyncAdapter = null;

    /**
     * Thread-safe constructor, creates static {@link ContactsSyncAdapter} instance.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new ContactsSyncAdapter(getApplicationContext());
            }
        }
    }

    /**
     * Return Binder handle for IPC communication with {@link ContactsSyncAdapter}.
     * <p>
     * <p>New sync requests will be sent directly to the SyncAdapter using this channel.
     *
     * @param intent Calling intent
     * @return Binder handle for {@link ContactsSyncAdapter}
     */
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}

