package co.tinode.tindroid.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.TindroidApp;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.SqlStore;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgGetMeta;

/**
 * Constants and misc utils
 */
public class Utils {
    // Account management constants
    public static final String TOKEN_TYPE = "co.tinode.token";
    public static final String TOKEN_EXPIRATION_TIME = "co.tinode.token_expires";
    public static final String ACCOUNT_TYPE = "co.tinode.account";
    public static final String SYNC_AUTHORITY = "com.android.contacts";
    public static final String TINODE_IM_PROTOCOL = "Tinode";
    // Constants for accessing shared preferences
    public static final String PREFS_HOST_NAME = "pref_hostName";
    public static final String PREFS_USE_TLS = "pref_useTLS";
    /**
     * MIME-type used when storing a profile {@link ContactsContract.Data} entry.
     */
    public static final String MIME_TINODE_PROFILE =
            "vnd.android.cursor.item/vnd.co.tinode.im";
    public static final String DATA_PID = Data.DATA1;
    static final String DATA_SUMMARY = Data.DATA2;
    static final String DATA_DETAIL = Data.DATA3;
    private static final String TAG = "Utils";

    public static Account createAccount(String uid) {
        return new Account(uid, ACCOUNT_TYPE);
    }

    public static Account getSavedAccount(final AccountManager accountManager,
                                          final @NonNull String uid) {
        Account account = null;

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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean loginNow(Context context) {
        String uid = BaseDb.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) {
            Log.w(TAG, "Data fetch failed: no login credentials");
            // Unknown if data is available, assuming it is.
            return false;
        }

        final AccountManager am = AccountManager.get(context);
        final Account account = getSavedAccount(am, uid);
        if (account == null) {
            Log.w(TAG, "Data fetch failed: account not found");
            return false;
        }

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName(context));
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final Tinode tinode = Cache.getTinode();

        try {

            String token = AccountManager.get(context).blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
            // Will return immediately if it's already connected.
            tinode.connect(hostName, tls, true).getResult();
            tinode.loginToken(token).getResult();
        } catch (Exception ex) {
            Log.w(TAG, "Failed to connect to server", ex);
            return false;
        }

        return true;
    }

    /**
     * Fetch messages (and maybe topic description and subscriptions) in background.
     * <p>
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
            if (!loginNow(context)) {
                return false;
            }
            dataAvailable = true;
            PromisedReply result = null;
            // Check if contacts have been synced already.
            if (tinode.getTopicsUpdated() == null) {
                // Background sync of contacts.
                result = Cache.attachMeTopic(null);
            }

            // Check again if topic has attached while we tried to connect. It does not guarantee that there
            // is no race condition to subscribe.
            if (!topic.isAttached()) {
                // Fully asynchronous. We don't need to do anything with the result.
                // The new data will be automatically saved.
                topic.subscribe(null, builder.build());
                topic.getMeta(builder.reset().withLaterData(24).build());
                result = topic.getMeta(builder.reset().withLaterDel(24).build());
                topic.leave();
            }
            if (result != null) {
                try {
                    // Wait for result before disconnecting.
                    result.getResult();
                } catch (Exception ignored) {
                }
            }
            tinode.disconnect(true);
        }
        return dataAvailable;
    }

    /**
     * Fetch description of a previously unknown topic or user in background.
     * Fetch subscriptions for GRP topics.
     * <p>
     * This method SHOULD NOT be called on UI thread.
     *
     * @param context   context to use for resources.
     * @param topicName name of the topic to sync.
     */
    public static void backgroundMetaFetch(Context context, String topicName) {
        Log.d(TAG, "Fetching description for " + topicName);

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
                Cache.attachMeTopic(null).getResult();
                tinode.getMeTopic().leave();
            }

            // Request description, wait for result. Tinode will save new topic to DB.
            tinode.getMeta(topicName, MsgGetMeta.desc()).getResult();
        } catch (Exception ex) {
            Log.i(TAG, "Background Meta fetch failed", ex);
        }
        tinode.disconnect(true);
    }

    /**
     * Update cached seq id of the last read message.
     * <p>
     * This method SHOULD NOT be called on UI thread.
     *
     * @param context   context to use for resources.
     * @param topicName name of the topic to sync.
     * @param seq new 'read' value.
     */
    public static void backgroundUpdateRead(Context context, String topicName, int seq) {
        Topic topic = Cache.getTinode().getTopic(topicName);
        if (topic == null) {
            // Don't need to handle 'read' notifications for an unknown topic.
            return;
        }
        if (topic.getRead() < seq) {
            topic.setRead(seq);
            SqlStore store = BaseDb.getInstance().getStore();
            if (store != null) {
                store.setRead(topic, seq);
            }
        }
    }
}
