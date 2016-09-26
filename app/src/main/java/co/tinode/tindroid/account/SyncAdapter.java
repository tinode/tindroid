package co.tinode.tindroid.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import co.tinode.tindroid.InmemoryCache;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.MsgGetMeta;
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

    private static final String SYNC_MARKER_KEY = "co.tinode.tindroid.sync_marker_contacts";
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
        Log.i(TAG, "Beginning network synchronization");
        try {
            final Tinode tinode = InmemoryCache.getTinode();

            Log.i(TAG, "Starting sync for account " + account.name);

            fetchEmailsAndPhones(ContactsContract.Data.CONTENT_URI);

            fetchProfileMe();

            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, InmemoryCache.HOST_NAME);
            String token = AccountManager.get(mContext)
                    .blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
            tinode.connect(hostName).getResult();
            tinode.loginToken(token).getResult();

            MsgGetMeta.GetSub sub = new MsgGetMeta.GetSub();
            sub.ims = getServerSyncMarker(account);
            MsgGetMeta subGet = new MsgGetMeta(null, sub, null);
            MeTopic me = tinode.getMeTopic();
            if (me != null) {
                if (!me.isAttached()) {
                    me.subscribe().getResult();
                }
                me.getMeta(subGet).getResult();

                // Fetch the list of updated contacts
                Collection<Subscription> updated = me.getUpdatedSubscriptions(sub.ims);
                Date upd = ContactsManager.updateContacts(mContext, account, updated, sub.ims);
                // FIXME(gene): use timestamp returned by the server
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
        String markerString = mAccountManager.getUserData(account, SYNC_MARKER_KEY);
        if (!TextUtils.isEmpty(markerString)) {
            return new Date(Long.parseLong(markerString));
        }
        return null;
    }

    private void setServerSyncMarker(Account account, Date marker) {
        mAccountManager.setUserData(account, SYNC_MARKER_KEY, Long.toString(marker.getTime()));
    }

    private List<String> fetchEmailsAndPhones(Uri uri) {
        List<String> list = new LinkedList<>();

        String[] projection = {
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Email.DATA,
                ContactsContract.CommonDataKinds.Email.TYPE
        };
        String selection = ContactsContract.Data.MIMETYPE + " in (?, ?)";
        String[] selectionArgs = {
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        };

        // ok, let's work...
        Cursor cursor = mContentResolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor == null) {
            return list;
        }

        final int mimeTypeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE);
        final int dataIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);
        final int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE);
        final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        final String country = Locale.getDefault().getCountry();

        while (cursor.moveToNext()) {
            int type = cursor.getInt(typeIdx);
            String data = cursor.getString(dataIdx);
            String mimeType = cursor.getString(mimeTypeIdx);
            if (mimeType.equals(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
                // This is an email
                list.add(Utils.TAG_LABEL_EMAIL + data);
            } else {
                // This is a phone number.

                // Use mobile phones only.
                if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                    try {
                        // Normalize phone number format
                        data = phoneUtil.format(phoneUtil.parse(data, country),
                                PhoneNumberUtil.PhoneNumberFormat.E164);
                        list.add(Utils.TAG_LABEL_PHONE + data);
                    } catch (NumberParseException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        cursor.close();

        return list;
    }

    private List<String> fetchProfileMe() {
        return fetchEmailsAndPhones(Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                ContactsContract.Contacts.Data.CONTENT_DIRECTORY));
    }
}

