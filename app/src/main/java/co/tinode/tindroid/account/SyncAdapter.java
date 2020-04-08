package co.tinode.tindroid.account;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import co.tinode.tindroid.Cache;
import co.tinode.tindroid.TindroidApp;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MetaGetSub;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgSetMeta;
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
    private static final String ACCKEY_QUERY_HASH = "co.tinode.tindroid.sync_query_hash_contacts";

    // Context for loading preferences
    private final Context mContext;
    private final AccountManager mAccountManager;

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    SyncAdapter(Context context, boolean autoInitialize) {
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
    @SuppressWarnings("unchecked")
    public void onPerformSync(final Account account, final Bundle extras, String authority,
                              ContentProviderClient provider, final SyncResult syncResult) {

        if (!Utils.isPermissionGranted(mContext, Manifest.permission.READ_CONTACTS)) {
            Log.i(TAG, "No permission to access contacts. Sync failed.");
            syncResult.stats.numAuthExceptions++;
            return;
        }

        // Log.i(TAG, "Beginning network synchronization");
        boolean success = false;
        final Tinode tinode = Cache.getTinode();
        Log.i(TAG, "Starting sync for account " + account.name);

        // See if we already have a sync-state attached to this account.
        Date lastSyncMarker = getServerSyncMarker(account);

        // By default, contacts from a 3rd party provider are hidden in the contacts
        // list. So let's set the flag that causes them to be visible, so that users
        // can actually see these contacts.
        if (lastSyncMarker == null) {
            ContactsManager.makeAccountContactsVisibile(mContext, account);
        }

        // Load contacts and send them to server as fnd.Private.
        SparseArray<Utils.ContactHolder> contactList = Utils.fetchContacts(getContext().getContentResolver(),
                Utils.FETCH_EMAIL | Utils.FETCH_PHONE);
        StringBuilder contactsBuilder = new StringBuilder();
        for (int i=0; i<contactList.size(); i++) {
            Utils.ContactHolder ch = contactList.get(contactList.keyAt(i));
            String contact = ch.toString();
            if (!TextUtils.isEmpty(contact)) {
                contactsBuilder.append(contact);
                contactsBuilder.append(",");
            }
        }

        if (contactsBuilder.length() > 0) {
            String contacts = contactsBuilder.toString();
            String oldHash = getServerQueryHash(account);
            String newHash = Utils.hash(contacts);

            if (!newHash.equals(oldHash)) {
                // If the query has changed, clear the sync marker for a full sync.
                // Otherwise we only going to get updated contacts.
                lastSyncMarker = null;
                setServerQueryHash(account, newHash);
            }

            try {
                final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
                String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName(mContext));
                boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());
                String token = AccountManager.get(mContext)
                        .blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
                tinode.connect(hostName, tls).getResult();

                // It will throw if something is wrong so we will try again later.
                tinode.loginToken(token).getResult();

                // It throws if rejected and we just fail to sync.
                // FND sends no presence notifications thus background flag is not needed.
                tinode.subscribe(Tinode.TOPIC_FND, null, null, false).getResult();

                if (lastSyncMarker == null) {
                    // Send contacts list to the server only if it has changed since last update, i.e. a full
                    // update is performed.
                    tinode.setMeta(Tinode.TOPIC_FND,
                            new MsgSetMeta(new MetaSetDesc(null, contacts))).getResult();
                }

                final MsgGetMeta meta = new MsgGetMeta(new MetaGetSub(lastSyncMarker, null));
                PromisedReply<ServerMessage> future = tinode.getMeta(Tinode.TOPIC_FND, meta);
                Date newSyncMarker = null;
                if (future.waitResult()) {
                    ServerMessage<?, ?, VxCard, PrivateType> pkt = future.getResult();
                    if (pkt.meta != null && pkt.meta.sub != null) {
                        // Fetch the list of updated contacts.
                        Collection<Subscription<VxCard, ?>> updated = new ArrayList<>();
                        for (Subscription<VxCard, ?> sub : pkt.meta.sub) {
                            if (Topic.getTopicTypeByName(sub.user) == Topic.TopicType.P2P) {
                                updated.add(sub);
                            }
                        }
                        newSyncMarker = ContactsManager.updateContacts(mContext, account, updated,
                                lastSyncMarker, true);
                    }
                }
                setServerSyncMarker(account, newSyncMarker != null ? newSyncMarker : new Date());
                success = true;
            } catch (IOException e) {
                Log.e(TAG, "Network error while syncing contacts", e);
                syncResult.stats.numIoExceptions++;
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync contacts", e);
                syncResult.stats.numAuthExceptions++;
            }
        } else {
            Log.d(TAG, "No contacts to sync");
            success = true;
            if (lastSyncMarker == null) {
                setServerSyncMarker(account, new Date());
            }

        }

        Log.d(TAG, "Network synchronization " + (success ? "completed" : "failed"));
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

    private String getServerQueryHash(Account account) {
        return mAccountManager.getUserData(account, ACCKEY_QUERY_HASH);
    }

    private void setServerQueryHash(Account account, String hash) {
        // The hash could be empty if user has no contacts
        if (hash != null && !hash.equals("")) {
            mAccountManager.setUserData(account, ACCKEY_QUERY_HASH, hash);
        }
    }

}

