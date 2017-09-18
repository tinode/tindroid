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
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import co.tinode.tindroid.Cache;
import co.tinode.tindroid.media.VCard;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

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

    private static final String ACCKEY_SYNC_MARKER = "co.tinode.tindroid.sync_marker_contacts";
    private static final String ACCKEY_INVISIBLE_GROUP = "co.tinode.tindroid.invisible_group_id";

    // Context for loading preferences
    private final Context mContext;
    private final AccountManager mAccountManager;

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
        mAccountManager = AccountManager.get(context);
        mContentResolver = context.getContentResolver();
    }

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mContext = context;
        mAccountManager = AccountManager.get(context);
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
        //Log.i(TAG, "Beginning network synchronization");
        try {
            Log.i(TAG, "Starting sync for account " + account.name);

            // See if we already have a sync-state attached to this account.
            Date lastSyncMarker = getServerSyncMarker(account);
            long invisibleGroupId = getInvisibleGroupId(account);

            // By default, contacts from a 3rd party provider are hidden in the contacts
            // list. So let's set the flag that causes them to be visible, so that users
            // can actually see these contacts.
            if (lastSyncMarker == null) {
                ContactsManager.makeAccountContactsVisibile(mContext, account);
                invisibleGroupId = ContactsManager.createInvisibleTinodeGroup(mContext, account);
                setInvisibleGroupId(account, invisibleGroupId);
            }

            final Tinode tinode = Cache.getTinode();

            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
            boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, false);
            String token = AccountManager.get(mContext)
                    .blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
            tinode.connect(hostName, tls).getResult();
            tinode.loginToken(token).getResult();

            // Don't care if it's resolved or rejected
            tinode.subscribe(Tinode.TOPIC_ME, null, null).waitResult();

            final MsgGetMeta meta = MsgGetMeta.sub();
            // FIXME(gene): The following is commented out for debugging
            // MsgGetMeta meta = new MsgGetMeta(null, new MetaGetSub(getServerSyncMarker(account), null), null);
            PromisedReply<ServerMessage> future = tinode.getMeta(Tinode.TOPIC_ME, meta);
            if (future.waitResult()) {
                ServerMessage<?,VCard,String> pkt = future.getResult();
                // Fetch the list of updated contacts. Group subscriptions will be stored in
                // the address book but as invisible contacts (members of invisible group)
                Collection<Subscription<VCard, String>> updated = new ArrayList<>();
                for (Subscription<VCard, String> sub : pkt.meta.sub) {
                    // Log.d(TAG, "updating contact " + sub.topic);
                    if (Topic.getTopicTypeByName(sub.topic) == Topic.TopicType.P2P) {
                        //Log.d(TAG, "contact " + sub.topic + "/" + sub.with + " added to list");
                        updated.add(sub);
                    }
                }
                Date upd = ContactsManager.updateContacts(mContext, account, updated,
                        meta.sub == null ? null : meta.sub.ims, invisibleGroupId);
                setServerSyncMarker(account, upd);
            }
        } catch (IOException e) {
            e.printStackTrace();
            syncResult.stats.numIoExceptions++;
        } catch (Exception e) {
            e.printStackTrace();
            syncResult.stats.numAuthExceptions++;
        }
        Log.i(TAG, "Network synchronization complete");
    }

    private Date getServerSyncMarker(Account account) {
        String markerString = mAccountManager.getUserData(account, ACCKEY_SYNC_MARKER);
        if (!TextUtils.isEmpty(markerString)) {
            return new Date(Long.parseLong(markerString));
        }
        return null;
    }

    private void setServerSyncMarker(Account account, Date marker) {
        // The marker could be null if user has no contacts
        if (marker != null) {
            mAccountManager.setUserData(account, ACCKEY_SYNC_MARKER, Long.toString(marker.getTime()));
        }
    }

    private long getInvisibleGroupId(Account account) {
        String idString = mAccountManager.getUserData(account, ACCKEY_INVISIBLE_GROUP);
        if (!TextUtils.isEmpty(idString)) {
            return Long.parseLong(idString);
        }
        return -1;
    }

    private void setInvisibleGroupId(Account account, long id) {
        mAccountManager.setUserData(account, ACCKEY_INVISIBLE_GROUP, Long.toString(id));
    }
}

