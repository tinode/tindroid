package co.tinode.tindroid.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;


import java.io.IOException;
import java.util.Date;

import co.tinode.tindroid.InmemoryCache;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.MsgGetMeta;

/**
 * Define a sync adapter for the app.
 * <p>
 * <p>This class is instantiated in {@link SyncService}, which also binds SyncAdapter to the system.
 * SyncAdapter should only be initialized in SyncService, never anywhere else.
 * <p>
 * <p>The system calls onPerformSync() via an RPC call through the IBinder object supplied by
 * SyncService.
 */
class SyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = "SyncAdapter";

    // Context for loading preferences
    private final Context mContext;

    /**
     * Content resolver, for performing database operations.
     */
    private final ContentResolver mContentResolver;


    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    /**
     * Called by the Android system in response to a request to run the sync adapter. The work
     * required to read data from the network, parse it, and store it in the content provider is
     * done here. Extending AbstractThreadedSyncAdapter ensures that all methods within SyncAdapter
     * run on a background thread. For this reason, blocking I/O and other long-running tasks can be
     * run <em>in situ</em>, and you don't have to set up a separate thread for them.
     * .
     * <p>
     * <p>This is where we actually perform any work required to perform a sync.
     * {@link android.content.AbstractThreadedSyncAdapter} guarantees that this will be called on a non-UI thread,
     * so it is safe to peform blocking I/O here.
     * <p>
     * <p>The syncResult argument allows you to pass information back to the method that triggered
     * the sync.
     */
    @Override
    public void onPerformSync(final Account account, final Bundle extras, String authority,
                              ContentProviderClient provider, final SyncResult syncResult) {
        Log.i(TAG, "Beginning network synchronization");
        try {
            final Tinode tinode = InmemoryCache.getTinode();

            Log.i(TAG, "Starting sync for account " + account.name);
            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, InmemoryCache.HOST_NAME);
            Bundle tokenBundle = AccountManager.get(mContext)
                    .getAuthToken(account, Utils.TOKEN_TYPE, null, false, null, null).getResult();
            String token = tokenBundle.getString(AccountManager.KEY_AUTHTOKEN);
            tinode.connect(hostName).getResult();
            tinode.loginToken(token).getResult();
            MsgGetMeta subGet = new MsgGetMeta();
            subGet.sub = new MsgGetMeta.GetSub();
            subGet.sub.ims = new Date();
            tinode.subscribe(MeTopic.NAME, null, subGet).getResult();
        } catch (IOException e) {
            syncResult.stats.numIoExceptions++;
        } catch (Exception e) {
            syncResult.stats.numAuthExceptions++;
        }
        Log.i(TAG, "Network synchronization complete");
    }
}

