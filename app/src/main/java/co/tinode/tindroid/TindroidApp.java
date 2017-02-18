package co.tinode.tindroid;

import android.app.Application;
import android.content.Context;

/**
 * A class for providing global context for database access
 */
public class TindroidApp extends Application {

    private static TindroidApp sContext;

    public TindroidApp() {
        sContext = this;
    }

    public static Context getAppContext() {
        return sContext;
    }
}
