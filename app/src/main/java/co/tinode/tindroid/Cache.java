package co.tinode.tindroid;

import android.os.Build;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

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
    private static final String TAG = "Cache";

    // public static String sHost = "api.tinode.co"; // remote host
    public static final String HOST_NAME = "10.0.2.2:6060"; // local host

    private static final String API_KEY = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K";

    private static Tinode sTinode;

    private static int sUniqueCounter = 100;

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

        FirebaseInstanceId
                .getInstance()
                .getInstanceId()
                .addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
            @Override
            public void onSuccess(InstanceIdResult instanceIdResult) {
                sTinode.setDeviceToken(instanceIdResult.getToken());
            }
        });
        return sTinode;
    }

    // Invalidate existing cache.
    static void invalidate() {
        if (sTinode != null) {
            sTinode.logout();
            sTinode = null;
        }
    }

    synchronized static int getUniqueCounter() {
        return ++sUniqueCounter;
    }

    // Connect to 'me' topic.
    @SuppressWarnings("unchecked")
    static PromisedReply<ServerMessage> attachMeTopic(MeTopic.MeListener l) {
        final MeTopic<VxCard> me = getTinode().getOrCreateMeTopic();
        me.setListener(l);

        if (!me.isAttached()) {
            return me.subscribe(null, me
                    .getMetaGetBuilder()
                    .withGetDesc()
                    .withGetSub().build());
        } else {
            return new PromisedReply<>((ServerMessage) null);
        }
    }

    static PromisedReply<ServerMessage> attachFndTopic(FndTopic.FndListener<VxCard> l) {
        final FndTopic<VxCard> fnd = getTinode().getOrCreateFndTopic();
        fnd.setListener(l);

        if (!fnd.isAttached()) {
            return fnd.subscribe(null, fnd
                    .getMetaGetBuilder()
                    .withGetSub().build());
        } else {
            return new PromisedReply<>((ServerMessage) null);
        }
    }
}
