package co.tinode.tindroid.services;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

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

import static androidx.core.content.ContextCompat.checkSelfPermission;

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

    // Prefixes for various contacts
    private static final String TAG_LABEL_PHONE = "tel:";
    private static final String TAG_LABEL_EMAIL = "email:";
    private static final String TAG_LABEL_TINODE = "tinode:";

    // Flags used to select what to fetch.
    private static final int FETCH_EMAIL = 0x1;
    private static final int FETCH_PHONE = 0x2;
    private static final int FETCH_IM = 0x4;

    // Context for loading preferences
    private final Context mContext;
    private final AccountManager mAccountManager;

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    ContactsSyncAdapter(Context context, boolean autoInitialize) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)) {
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
        SparseArray<ContactHolder> contactList = fetchContacts(getContext().getContentResolver(),
                FETCH_EMAIL | FETCH_PHONE);
        StringBuilder contactsBuilder = new StringBuilder();
        for (int i=0; i<contactList.size(); i++) {
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

    /**
     * Read address book contacts from the Contacts content provider.
     * The results are ordered by 'data1' field.
     *
     * @param resolver content resolver to use.
     * @param flags    bit flags indicating types f contacts to fetch.
     * @return contacts
     */
    private static SparseArray<ContactHolder> fetchContacts(ContentResolver resolver, int flags) {
        SparseArray<ContactHolder> map = new SparseArray<>();

        final String[] projection = {
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Email.DATA,
                ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Im.PROTOCOL,
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL,
        };

        // Need to make the list order consistent so the hash does not change too often.
        final String orderBy = ContactsContract.CommonDataKinds.Email.DATA;

        LinkedList<String> args = new LinkedList<>();
        if ((flags & FETCH_EMAIL) != 0) {
            args.add(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        }
        if ((flags & FETCH_PHONE) != 0) {
            args.add(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        }
        if ((flags & FETCH_IM) != 0) {
            args.add(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE);
        }
        if (args.size() == 0) {
            throw new IllegalArgumentException();
        }

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
        Cursor cursor = resolver.query(ContactsContract.Data.CONTENT_URI, projection,
                selection, selectionArgs, orderBy);
        if (cursor == null) {
            Log.d(TAG, "Failed to fetch contacts");
            return map;
        }

        final int contactIdIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
        final int mimeTypeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE);
        final int dataIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);
        final int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE);
        final int imProtocolIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.PROTOCOL);
        final int imProtocolNameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL);

        final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        final String country = Locale.getDefault().getCountry();

        while (cursor.moveToNext()) {
            int contact_id = cursor.getInt(contactIdIdx);
            String data = cursor.getString(dataIdx);
            String mimeType = cursor.getString(mimeTypeIdx);

            ContactHolder holder = map.get(contact_id);
            if (holder == null) {
                holder = new ContactHolder();
                map.put(contact_id, holder);
            }

            switch (mimeType) {
                case ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE:
                    // This is an email
                    // Log.d(TAG, "Adding email '" + data + "' to contact=" + contact_id);
                    holder.putEmail(data);
                    break;
                case ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE:
                    int protocol = cursor.getInt(imProtocolIdx);
                    String protocolName = cursor.getString(imProtocolNameIdx);
                    if (protocol == ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM &&
                            protocolName.equals(Utils.TINODE_IM_PROTOCOL)) {
                        holder.putIm(data);
                    }
                    break;
                case ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE:
                    // This is a phone number. Syncing phones of all types. The 'mobile' marker is ignored
                    // because users ignore it these days.
                    try {
                        // Normalize phone number format
                        Phonenumber.PhoneNumber number = phoneUtil.parse(data, country);
                        if (phoneUtil.isValidNumber(number)) {
                            holder.putPhone(phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
                        } else {
                            Log.i(TAG, "'" + data + "' is not a valid phone number in country '" + country + "'");
                        }
                    } catch (NumberParseException ex) {
                        Log.i(TAG, "Failed to parse phone number '" + data + "' in country '" + country + "'");
                    }
                    break;
            }
        }
        cursor.close();

        return map;
    }

    // Generate a hash from a string.
    private static String hash(String s) {
        if (s == null || s.equals("")) {
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
            e.printStackTrace();
        }

        return String.valueOf(s.hashCode());
    }

    private static class ContactHolder {
        List<String> emails;
        List<String> phones;
        List<String> ims;

        ContactHolder() {
            emails = null;
            phones = null;
            ims = null;
        }

        private static void Stringify(List<String> vals, String label, StringBuilder str) {
            if (vals != null && vals.size() > 0) {
                if (str.length() > 0) {
                    str.append(",");
                }

                for (String entry : vals) {
                    str.append(label);
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

        void putIm(String im) {
            if (ims == null) {
                ims = new LinkedList<>();
            }
            ims.add(im);
        }

        @Override
        @NonNull
        public String toString() {
            StringBuilder str = new StringBuilder();
            Stringify(emails, TAG_LABEL_EMAIL, str);
            Stringify(phones, TAG_LABEL_PHONE, str);
            Stringify(ims, TAG_LABEL_TINODE, str);
            return str.toString();
        }
    }
}

