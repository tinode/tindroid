package co.tinode.tindroid.services;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.util.SparseArray;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.TindroidApp;
import co.tinode.tindroid.account.ContactsManager;
import co.tinode.tindroid.account.Utils;
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
 * <p>This class is instantiated in {@link ContactsSyncService}, which also binds SyncAdapter to the system.
 * SyncAdapter should only be initialized in SyncService, never anywhere else.
 * <p>
 * <p>The system calls onPerformSync() via an RPC call through the IBinder object supplied by
 * SyncService.
 */
class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = "SyncAdapter";

    private static final String ACCKEY_SYNC_MARKER = "co.tinode.tindroid.sync_marker_contacts";
    private static final String ACCKEY_QUERY_HASH = "co.tinode.tindroid.sync_query_hash_contacts";

    // Context for loading preferences
    private final Context mContext;
    private final AccountManager mAccountManager;

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    ContactsSyncAdapter(Context context) {
        super(context, true);
        mContext = context;
        mAccountManager = AccountManager.get(context);
    }

    /**
     * Read address book contacts from the Contacts content provider.
     * The results are ordered by 'data1' field.
     *
     * @param context context to use.
     * @return contacts
     */
    private static SparseArray<ContactHolder> fetchContacts(Context context) {
        SparseArray<ContactHolder> map = new SparseArray<>();

        final String[] projection = {
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Email.DATA,
                ContactsContract.CommonDataKinds.Email.TYPE
        };

        // Need to make the list order consistent so the hash does not change too often.
        final String orderBy = ContactsContract.CommonDataKinds.Email.DATA;

        LinkedList<String> args = new LinkedList<>();
        args.add(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        args.add(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);

        StringBuilder sel = new StringBuilder(ContactsContract.Data.MIMETYPE);
        sel.append(" IN (");
        for (int i = 0; i < args.size(); i++) {
            sel.append("?,");
        }
        // Strip final comma.
        sel.setLength(sel.length() - 1);
        sel.append(")");

        final String selection = sel.toString();

        final String[] selectionArgs = args.toArray(new String[]{});

        // Get contacts from the database.
        Cursor cursor = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection,
                selection, selectionArgs, orderBy);
        if (cursor == null) {
            Log.d(TAG, "Failed to fetch contacts");
            return map;
        }

        // Attempt to determine default country for standardizing phone numbers.
        String countryCode = null;
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            // First try to use country code of the SIM card.
            countryCode = tm.getSimCountryIso();
            if (TextUtils.isEmpty(countryCode)) {
                // Fallback to current network.
                countryCode = tm.getNetworkCountryIso();
            }
            if (TextUtils.isEmpty(countryCode)) {
                // Use device locale country as a last resort.
                countryCode = context.getResources().getConfiguration().getLocales().get(0).getCountry();
            }
        }
        final int contactIdIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
        final int mimeTypeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE);
        final int dataIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);

        while (cursor.moveToNext()) {
            int contact_id = cursor.getInt(contactIdIdx);
            String data = cursor.getString(dataIdx);
            if (data == null) {
                continue;
            }
            data = data.trim();
            String mimeType = cursor.getString(mimeTypeIdx);

            ContactHolder holder = map.get(contact_id);
            if (holder == null) {
                holder = new ContactHolder();
                map.put(contact_id, holder);
            }

            switch (mimeType) {
                case ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE:
                    // This is an email
                    if (!TextUtils.isEmpty(data) && Patterns.EMAIL_ADDRESS.matcher(data).matches()) {
                        holder.putEmail(data);
                    } else {
                        Log.w(TAG, "'" + data + "' is not an email");
                    }
                    break;
                case ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE:
                    // This is a phone number. Syncing phones of all types. The 'mobile' marker is ignored
                    // because users ignore it these days.
                    if (!TextUtils.isEmpty(data) && Patterns.PHONE.matcher(data).matches()) {
                        if (!TextUtils.isEmpty(countryCode)) {
                            // Try to convert the number to E164 format.
                            String e164 = PhoneNumberUtils.formatNumberToE164(data, countryCode);
                            if (!TextUtils.isEmpty(e164)) {
                                data = e164;
                            }
                        }
                        // Remove all characters other than 0-9 and +, save the result.
                        holder.putPhone(data.replaceAll("[^0-9+]", ""));
                    } else {
                        Log.w(TAG, "'" + data + "' is not a valid phone number");
                    }
                    break;
            }
        }
        cursor.close();

        return map;
    }

    // Generate a hash from a string.
    private static String hash(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }

        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            // Create a String from the byte array.
            StringBuilder hexString = new StringBuilder();
            for (byte x : messageDigest) {
                hexString.append(Integer.toString(0xFF & x, 32));
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5 hashing not available, using hashCode", e);
        }

        return String.valueOf(s.hashCode());
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
     * so it is safe to perform blocking I/O here.
     * <p>
     * <p>The syncResult argument allows you to pass information back to the method that triggered
     * the sync.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onPerformSync(final Account account, final Bundle extras, String authority,
                              ContentProviderClient provider, final SyncResult syncResult) {

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No permission to access contacts. Sync failed.");
            syncResult.stats.numAuthExceptions++;
            return;
        }

        boolean success = false;
        final Tinode tinode = Cache.getTinode();

        // See if we already have a sync-state attached to this account.
        Date lastSyncMarker = getServerSyncMarker(account);

        // By default, contacts from a 3rd party provider are hidden in the contacts
        // list. So let's set the flag that causes them to be visible, so that users
        // can actually see these contacts.
        if (lastSyncMarker == null) {
            ContactsManager.makeAccountContactsVisibile(mContext, account);
        }

        // Load contacts and send them to server as fnd.Private.
        SparseArray<ContactHolder> contactList = fetchContacts(getContext());
        StringBuilder contactsBuilder = new StringBuilder();
        for (int i = 0; i < contactList.size(); i++) {
            ContactHolder ch = contactList.get(contactList.keyAt(i));
            String contact = ch.toString();
            if (!TextUtils.isEmpty(contact)) {
                contactsBuilder.append(contact);
                contactsBuilder.append(",");
            }
        }

        if (contactsBuilder.length() > 0) {
            String contacts = contactsBuilder.toString();
            String oldHash = getServerQueryHash(account);
            String newHash = hash(contacts);

            if (!newHash.equals(oldHash)) {
                // If the query has changed, clear the sync marker for a full sync.
                // Otherwise we only going to get updated contacts.
                lastSyncMarker = null;
                setServerQueryHash(account, newHash);
            }

            try {
                final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
                @SuppressLint("UnsafeOptInUsageError")
                String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
                @SuppressLint("UnsafeOptInUsageError")
                boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());
                String token = AccountManager.get(mContext)
                        .blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
                tinode.connect(hostName, tls, true).getResult();

                // It will throw if something is wrong so we will try again later.
                tinode.loginToken(token).getResult();

                // It throws if rejected and we just fail to sync.
                // FND sends no presence notifications thus background flag is not needed.
                tinode.subscribe(Tinode.TOPIC_FND, null, null).getResult();

                if (lastSyncMarker == null) {
                    // Send contacts list to the server only if it has changed since last update, i.e. a full
                    // update is performed.
                    tinode.setMeta(Tinode.TOPIC_FND, new MsgSetMeta.Builder()
                            .with(new MetaSetDesc(null, contacts)).build()).getResult();
                }

                final MsgGetMeta meta = new MsgGetMeta(new MetaGetSub(lastSyncMarker, null));
                PromisedReply<ServerMessage> future = tinode.getMeta(Tinode.TOPIC_FND, meta);
                Date newSyncMarker = null;
                if (future.waitResult()) {
                    ServerMessage<?, ?, VxCard, PrivateType> pkt = future.getResult();
                    if (pkt.meta != null && pkt.meta.sub != null) {
                        // Fetch the list of updated contacts.
                        Collection<Subscription<VxCard, PrivateType>> updated = new ArrayList<>();
                        for (Subscription<VxCard, PrivateType> sub : pkt.meta.sub) {
                            if (Topic.isP2PType(sub.user)) {
                                updated.add(sub);
                            }
                        }
                        newSyncMarker = ContactsManager.updateContacts(mContext, account, tinode,
                                updated, lastSyncMarker, true);
                    }
                }
                tinode.maybeDisconnect(true);
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
        if (hash != null && !hash.isEmpty()) {
            mAccountManager.setUserData(account, ACCKEY_QUERY_HASH, hash);
        }
    }

    private static class ContactHolder {
        List<String> emails;
        List<String> phones;

        ContactHolder() {
            emails = null;
            phones = null;
        }

        private static void Stringify(List<String> vals, StringBuilder str) {
            if (vals != null && !vals.isEmpty()) {
                if (str.length() > 0) {
                    str.append(",");
                }

                for (String entry : vals) {
                    str.append(entry);
                    str.append(",");
                }
                // Strip trailing comma.
                str.setLength(str.length() - 1);
            }
        }

        void putEmail(String email) {
            if (emails == null) {
                emails = new LinkedList<>();
            }
            emails.add(email);
        }

        void putPhone(String phone) {
            if (phones == null) {
                phones = new LinkedList<>();
            }
            phones.add(phone);
        }

        @Override
        @NonNull
        public String toString() {
            StringBuilder str = new StringBuilder();
            Stringify(emails, str);
            Stringify(phones, str);
            return str.toString();
        }
    }
}

