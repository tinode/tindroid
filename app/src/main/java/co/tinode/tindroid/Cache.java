package co.tinode.tindroid;

import android.os.Build;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Locale;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.FndTopic;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Shared resources.
 */
public class Cache {
    private static final String API_KEY = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K";

    private static Tinode sTinode;

    public static Tinode getTinode() {
        if (sTinode == null) {
            sTinode = new Tinode("Tindroid/" + TindroidApp.getAppVersion(), API_KEY,
                    BaseDb.getInstance().getStore(), null);
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
}
