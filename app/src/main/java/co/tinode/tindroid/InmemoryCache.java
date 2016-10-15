package co.tinode.tindroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Locale;

import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;

/**
 * Shared resources.
 */
public class InmemoryCache {
    private static final String TAG = "InmemoryCache";
    private static final String API_KEY = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K";

    private static Tinode sTinode;

    public static final String HOST_NAME = "10.0.2.2:6060"; // local
    //public static String sHost = "api.tinode.co"; // remote

    private static int sVisibleCount = 0;

    public static Tinode getTinode() {
        if (sTinode == null) {
            Log.d(TAG, "Tinode instantiated");

            sTinode = new Tinode("Tindroid", API_KEY);
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
}
