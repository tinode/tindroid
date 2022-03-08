package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Locale;

import co.tinode.tinsdk.ComTopic;
import co.tinode.tinsdk.FndTopic;
import co.tinode.tinsdk.MeTopic;
import co.tinode.tinsdk.PromisedReply;
import co.tinode.tinsdk.Tinode;
import co.tinode.tinsdk.Topic;
import co.tinode.tinsdk.model.MsgGetMeta;
import co.tinode.tinsdk.model.PrivateType;
import co.tinode.tinsdk.model.ServerMessage;
import co.tinode.tinui.TinodeClient;
import co.tinode.tinui.account.Utils;
import co.tinode.tinui.db.BaseDb;
import co.tinode.tinui.media.VxCard;

/**
 * Shared resources.
 */
public class Cache {
    private static final String TAG = "Cache";
    private static final String API_KEY = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K";

    private static Tinode sTinode = null;

    public static void buildTinode() {
        if (sTinode == null) {
            sTinode = new TinodeClient.Builder("Tindroid/" + TindroidApp.getAppVersion(), API_KEY)
                    .setStorage(new BaseDb.Builder(TindroidApp.getAppContext()).build().getStore())
                    .build();
            sTinode.setOsString(Build.VERSION.RELEASE);

            // Default types for parsing Public, Private fields of messages
            sTinode.setDefaultTypeOfMetaPacket(VxCard.class, PrivateType.class);
            sTinode.setMeTypeOfMetaPacket(VxCard.class);
            sTinode.setFndTypeOfMetaPacket(VxCard.class);

            // Set device language
            sTinode.setLanguage(Locale.getDefault().toString());

            // Keep in app to prevent garbage collection.
            TindroidApp.retainTinodeCache(sTinode);
        }
    }

    public static Tinode getTinode() {
        if (sTinode == null) {
            throw new IllegalStateException("Cache::buildTinode() must be called before obtaining Tinode instance");
        }

        FirebaseMessaging fbId = FirebaseMessaging.getInstance();
        //noinspection ConstantConditions: Google lies about getInstance not returning null.
        if (fbId != null) {
            fbId.getToken().addOnSuccessListener(token -> {
                if (sTinode != null) {
                    sTinode.setDeviceToken(token);
                }
            });
        }
        return sTinode;
    }

    // Invalidate existing cache.
    static void invalidate() {
        if (sTinode != null) {
            sTinode.logout();
            sTinode = null;
            FirebaseMessaging.getInstance().deleteToken();
        }
    }

    // Connect to 'me' topic.
    @SuppressWarnings("unchecked")
    public static PromisedReply<ServerMessage> attachMeTopic(MeTopic.MeListener l) {
        final MeTopic<VxCard> me = getTinode().getOrCreateMeTopic();
        if (l != null) {
            me.setListener(l);
        }

        if (!me.isAttached()) {
            return me.subscribe(null, me
                    .getMetaGetBuilder()
                    .withCred()
                    .withDesc()
                    .withSub()
                    .withTags()
                    .build());
        } else {
            return new PromisedReply<>((ServerMessage) null);
        }
    }

    static PromisedReply<ServerMessage> attachFndTopic(FndTopic.FndListener<VxCard> l) {
        final FndTopic<VxCard> fnd = getTinode().getOrCreateFndTopic();
        if (l != null) {
            fnd.setListener(l);
        }

        if (!fnd.isAttached()) {
            // Don't request anything here.
            return fnd.subscribe(null, null);
        } else {
            return new PromisedReply<>((ServerMessage) null);
        }
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
        final Account account = Utils.getSavedAccount(am, uid);
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
}
