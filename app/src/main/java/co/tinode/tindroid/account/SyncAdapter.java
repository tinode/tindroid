package co.tinode.tindroid.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
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
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.PrivateType;
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
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mAccountManager = AccountManager.get(context);
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
        final Tinode tinode = Cache.getTinode();
        try {
            Log.i(TAG, "Starting sync for account " + account.name);

            // See if we already have a sync-state attached to this account.
            Date lastSyncMarker = getServerSyncMarker(account);

            // By default, contacts from a 3rd party provider are hidden in the contacts
            // list. So let's set the flag that causes them to be visible, so that users
            // can actually see these contacts.
            if (lastSyncMarker == null) {
                ContactsManager.makeAccountContactsVisibile(mContext, account);
            }

            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
            boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, false);
            String token = AccountManager.get(mContext)
                    .blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
            tinode.connect(hostName, tls).getResult();
            tinode.loginToken(token).getResult();

            // Don't care if it's resolved or rejected
            tinode.subscribe(Tinode.TOPIC_FND, null, null).waitResult();

            final MsgGetMeta meta = MsgGetMeta.sub();
            // FIXME(gene): The following is commented out for debugging
            // MsgGetMeta meta = new MsgGetMeta(null, new MetaGetSub(getServerSyncMarker(account), null), null);
            PromisedReply<ServerMessage> future = tinode.getMeta(Tinode.TOPIC_FND, meta);
            if (future.waitResult()) {
                ServerMessage<?,?,VxCard,PrivateType> pkt = future.getResult();
                if (pkt.meta == null || pkt.meta.sub == null) {
                    // Server did not return any contacts.
                    return;
                }
                // Fetch the list of updated contacts. Group subscriptions will be stored in
                // the address book but as invisible contacts (members of invisible group)
                Collection<Subscription<VxCard,?>> updated = new ArrayList<>();
                for (Subscription<VxCard,?> sub : pkt.meta.sub) {
                    if (Topic.getTopicTypeByName(sub.user) == Topic.TopicType.P2P) {
                        //Log.d(TAG, "contact " + sub.topic + "/" + sub.with + " added to list");
                        updated.add(sub);
                    }
                }
                Date upd = ContactsManager.updateContacts(mContext, account, updated,
                        meta.sub == null ? null : meta.sub.ims);
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
}

