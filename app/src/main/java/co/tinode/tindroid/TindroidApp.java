package co.tinode.tindroid;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import co.tinode.tinodesdk.Tinode;

/**
 * A class for providing global context for database access
 */
public class TindroidApp extends Application {

    private static TindroidApp sContext;

    // The Tinode cache is linked from here so it's never garbage collected.
    @SuppressWarnings("unused, FieldCanBeLocal")
    private static Tinode sTinodeCache;

    private static String sAppVersion = null;

    public TindroidApp() {
        sContext = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            sAppVersion = pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d("TindroidApp", "Failed to retrieve app version");
        }

        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String token = intent.getStringExtra("token");
                if (token != null && !token.equals("")) {
                    sTinodeCache.setDeviceToken(token);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(br,
                new IntentFilter("FCM_REFRESH_TOKEN"));

        createNotificationChannel();
    }

    public static Context getAppContext() {
        return sContext;
    }

    public static String getAppVersion() {
        return sAppVersion;
    }

    public static void retainTinodeCache(Tinode tinode) {
        sTinodeCache = tinode;
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("new_message",
                    getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_channel_description));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
