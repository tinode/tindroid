package co.tinode.tindroid;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
}
