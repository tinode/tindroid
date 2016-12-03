package co.tinode.tindroid;

import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Locale;
import java.util.Map;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.SqlStore;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Shared resources.
 */
public class Cache {
    private static final String TAG = "Cache";

    //public static String sHost = "api.tinode.co"; // remote host
    public static final String HOST_NAME = "10.0.2.2:6060"; // local host

    private static final String API_KEY = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K";

    private static Tinode sTinode;

    private static int sVisibleCount = 0;

    public static Tinode getTinode() {
        if (sTinode == null) {
            Log.d(TAG, "Tinode instantiated");

            sTinode = new Tinode("Tindroid", API_KEY, BaseDb.getStore(), null);
            // Default types for parsing Public, Private, Content fields of messages
            sTinode.setDefaultTypes(VCard.class, String.class, String.class);
            // Set device language
            sTinode.setLanguage(Locale.getDefault().getLanguage());
        }

        sTinode.setDeviceToken(FirebaseInstanceId.getInstance().getToken());
        return sTinode;
    }

    /**
     * Keep counter of visible activities
     *
     * @param visible true if some activity became visible
     * @return
     */
    public static int activityVisible(boolean visible) {
        sVisibleCount += visible ? 1 : -1;
        Log.d(TAG, "Visible count: " + sVisibleCount);
        return sVisibleCount;
    }

    /**
     * @return true if any activity is visible to the user
     */
    public static boolean isInForeground() {
        return sVisibleCount > 0;
    }

    public static boolean isUserOnline(String topic) {
        Tinode tinode = getTinode();
        if (tinode.isConnected()) {
            MeTopic me = tinode.getMeTopic();
            if (me != null) {
                Subscription live = me.getSubscription(topic);
                return live != null && live.online;
            }
        }
        return false;
    }
}
