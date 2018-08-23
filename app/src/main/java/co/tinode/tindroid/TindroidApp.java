package co.tinode.tindroid;

import android.app.Application;
import android.content.Context;
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

    public TindroidApp() {
        sContext = this;
    }

    public static Context getAppContext() {
        return sContext;
    }

    public static void retainTinodeCache(Tinode tinode) {
        sTinodeCache = tinode;
    }
}
