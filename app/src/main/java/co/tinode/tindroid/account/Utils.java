package co.tinode.tindroid.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.TindroidApp;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgGetMeta;

import static androidx.core.content.ContextCompat.checkSelfPermission;

/**
 * Constants and misc utils
 */
public class Utils {
    private static final String TAG = "Utils";

    // Account management constants
    public static final String TOKEN_TYPE = "co.tinode.token";
    public static final String TOKEN_EXPIRATION_TIME = "co.tinode.token_expires";

    public static final String ACCOUNT_TYPE = "co.tinode.account";
    public static final String SYNC_AUTHORITY = "com.android.contacts";
    public static final String TINODE_IM_PROTOCOL = "Tinode";

    // Constants for accessing shared preferences
    public static final String PREFS_HOST_NAME = "pref_hostName";
    public static final String PREFS_USE_TLS = "pref_useTLS";

    // Prefixes for various contacts
    public static final String TAG_LABEL_PHONE = "tel:";
    public static final String TAG_LABEL_EMAIL = "email:";
    public static final String TAG_LABEL_TINODE = "tinode:";

    public static final int FETCH_EMAIL = 0x1;
    public static final int FETCH_PHONE = 0x2;
    public static final int FETCH_IM = 0x4;

    /**
     * MIME-type used when storing a profile {@link ContactsContract.Data} entry.
     */
    public static final String MIME_TINODE_PROFILE =
            "vnd.android.cursor.item/vnd.co.tinode.im";
    public static final String DATA_PID = Data.DATA1;
    public static final String DATA_SUMMARY = Data.DATA2;
    public static final String DATA_DETAIL = Data.DATA3;

    public static Account createAccount(String uid) {
        return new Account(uid, ACCOUNT_TYPE);
    }

    /**
     * Read address book contacts from the Contacts content provider.
     * The results are ordered by 'data1' field.
     *
     * @param resolver content resolver to use.
     * @param flags    bit flags indicating types f contacts to fetch.
     * @return contacts
     */
    static SparseArray<ContactHolder> fetchContacts(ContentResolver resolver, int flags) {
        SparseArray<ContactHolder> map = new SparseArray<>();

        final String[] projection = {
                Data.CONTACT_ID,
                Data.MIMETYPE,
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

        StringBuilder sel = new StringBuilder(Data.MIMETYPE);
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

        final int contactIdIdx = cursor.getColumnIndex(Data.CONTACT_ID);
        final int mimeTypeIdx = cursor.getColumnIndex(Data.MIMETYPE);
        final int dataIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);
        final int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE);
        final int imProtocolIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.PROTOCOL);
        final int imProtocolNameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL);

        final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        final String country = Locale.getDefault().getCountry();

        while (cursor.moveToNext()) {
            int type = cursor.getInt(typeIdx);
            int contact_id = cursor.getInt(contactIdIdx);
            String data = cursor.getString(dataIdx);
            String mimeType = cursor.getString(mimeTypeIdx);

            // Log.d(TAG, "Got id=" + contact_id + ", mime='" + mimeType +"', val='" + data + "'");

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
                            protocolName.equals(TINODE_IM_PROTOCOL)) {
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
    static String hash(String s) {
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

    public static Account getSavedAccount(final Context context, final AccountManager accountManager,
                                          final @NonNull String uid) {
        Account account = null;

        /*
        // On API <= 22 we have static permission to access accounts. On API >= 23 we can access our own accounts
        // without a dynamic permission check.
        if (!isPermissionGranted(context, android.Manifest.permission.GET_ACCOUNTS)) {
            // Don't have permission. It's the first launch or the user denied access.
            // Fail and go to full login. We should not ask for permission on the splash screen.
            Log.d(TAG, "NO permission to get accounts");
            return null;
        }
        */

        // Let's find out if we already have a suitable account. If one is not found, go to full login. It will create
        // an account with suitable name.
        final Account[] availableAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        if (availableAccounts.length > 0) {
            // Found some accounts, let's find the one with the right name
            for (Account acc : availableAccounts) {
                if (uid.equals(acc.name)) {
                    account = acc;
                    break;
                }
            }
        }

        return account;
    }

    public static class ContactHolder {
        List<String> emails;
        List<String> phones;
        List<String> ims;

        ContactHolder() {
            emails = null;
            phones = null;
            ims = null;
        }

        // Inverse of toString: deserialize contacts from
        ContactHolder(final String[] matches) {
            // Initialize all content to null.
            this();
            // Log.i(TAG, "Processing matches: " + Arrays.toString(matches));
            // Parse contacts.
            for (String match : matches) {
                if (match.indexOf(TAG_LABEL_EMAIL) == 0) {
                    putEmail(match.substring(TAG_LABEL_EMAIL.length()));
                } else if (match.indexOf(TAG_LABEL_PHONE) == 0) {
                    putPhone(match.substring(TAG_LABEL_PHONE.length()));
                } else if (match.indexOf(TAG_LABEL_TINODE) == 0) {
                    putIm(match.substring(TAG_LABEL_TINODE.length()));
                }
            }
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

    static boolean isPermissionGranted(Context context, String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean loginNow(Context context) {
        String uid = BaseDb.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) {
            Log.w(TAG, "Data fetch failed: not logged in");
            // Unknown if data is available, assuming it is.
            return false;
        }

        final AccountManager am = AccountManager.get(context);
        final Account account = getSavedAccount(context, am, uid);
        if (account == null) {
            Log.w(TAG, "Data fetch failed: account not found");
            return false;
        }

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName(context));
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final Tinode tinode = Cache.getTinode();

        try {
            // Will return immediately if it's already connected.
            tinode.connect(hostName, tls).getResult();

            String token = AccountManager.get(context).blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);

            tinode.loginToken(token).getResult();
        }  catch (Exception ex) {
            Log.w(TAG, "Failed to connect to server", ex);
            return false;
        }

        return true;
    }

    /**
     * Fetch messages (and maybe topic description and subscriptions) in background.
     *
     * This method SHOULD NOT be called on UI thread.
     *
     * @param context   context to use for resources.
     * @param topicName name of the topic to sync.
     * @param seq       sequence ID of the new message to fetch.
     * @return true if new data was available or data status was unknown, false if no data was available.
     */
    public static boolean backgroundDataFetch(Context context, String topicName, int seq) {
        Log.d(TAG, "Fetching messages for " + topicName);

        final Tinode tinode = Cache.getTinode();

        // noinspection unchecked
        ComTopic<VxCard> topic = (ComTopic<VxCard>) tinode.getTopic(topicName);
        Topic.MetaGetBuilder builder;
        if (topic == null) {
            // New topic. Create it.
            // noinspection unchecked
            topic = (ComTopic<VxCard>) tinode.newTopic(topicName, null);
            builder = topic.getMetaGetBuilder().withDesc().withSub();
        } else {
            // Existing topic.
            builder = topic.getMetaGetBuilder();
        }

        if (topic.isAttached()) {
            Log.d(TAG, "Topic is already attached");
            // No need to fetch: topic is already subscribed and got data through normal channel.
            // Assuming that data was available.
            return true;
        }

        boolean dataAvailable = false;
        if (topic.getSeq() < seq) {
            dataAvailable = true;
            if (loginNow(context)) {
                // Check if contacts have been synced already.
                if (tinode.getTopicsUpdated() == null) {
                    // Background sync of contacts.
                    Cache.attachMeTopic(null, true);
                    tinode.getMeTopic().leave();
                }

                // Check again if topic has attached while we tried to connect. It does not guarantee that there
                // is no race condition to subscribe.
                if (!topic.isAttached()) {
                    // Fully asynchronous. We don't need to do anything with the result.
                    // The new data will be automatically saved.
                    topic.subscribe(null, builder.withLaterData(24).withLaterDel(24).build(), true);
                    topic.leave();
                }
            }
        }
        return dataAvailable;
    }

    /**
     * Fetch description of a previously unknown topic or user in background.
     * Fetch subscriptions for GRP topics.
     *
     * This method SHOULD NOT be called on UI thread.
     *
     * @param context   context to use for resources.
     * @param topicName name of the topic to sync.
     */
    public static void backgroundMetaFetch(Context context, String topicName) {
        Log.d(TAG, "Fetching description for " + topicName);

        Topic.TopicType tp = Topic.getTopicTypeByName(topicName);

        final Tinode tinode = Cache.getTinode();

        if (tinode.getTopic(topicName) != null) {
            // Ignoring notification for a known topic.
            return;
        }

        if (!loginNow(context)) {
            // Failed to connect or to login.
            return;
        }

        // Fetch description without subscribing.
        try {
            // Check if contacts have been synced already.
            if (tinode.getTopicsUpdated() == null) {
                // Background sync of all contacts.
                Cache.attachMeTopic(null, true).getResult();
                tinode.getMeTopic().leave();
            }

            // Request description, wait for result. Tinode will save new topic to DB.
            tinode.getMeta(topicName, MsgGetMeta.desc()).getResult();
        } catch (Exception ex) {
            Log.i(TAG, "Background Meta fetch failed", ex);
        }
    }
}
