package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import java.io.IOException;
import java.util.Iterator;

import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Tinode;

import co.tinode.tinodesdk.model.ServerMessage;
import io.fabric.sdk.android.Fabric;

/**
 * A class for providing global context for database access
 */
public class TindroidApp extends Application {
    private static final String TAG = "TindroidApp";

    private static TindroidApp sContext;

    // The Tinode cache is linked from here so it's never garbage collected.
    @SuppressWarnings("unused, FieldCanBeLocal")
    private static Tinode sTinodeCache;

    private static String sAppVersion = null;
    private static int sAppBuild = 0;

    public TindroidApp() {
        sContext = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            sAppVersion = pi.versionName;
            sAppBuild = pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to retrieve app version", e);
        }

        if (isEmulator()) {
            Log.i(TAG, "Running in emulator: disabling Crashlytics");
            CrashlyticsCore disabled = new CrashlyticsCore.Builder().disabled(true).build();
            Fabric.with(this, new Crashlytics.Builder().core(disabled).build());
        }

        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String token = intent.getStringExtra("token");
                if (token != null && !token.equals("") && sTinodeCache != null) {
                    sTinodeCache.setDeviceToken(token);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(br,
                new IntentFilter("FCM_REFRESH_TOKEN"));

        createNotificationChannel();

        // Check if preferences already exist. If not, create them.
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String hostName = pref.getString("pref_hostName", null);
        if (TextUtils.isEmpty(hostName)) {
            // No preferences found. Save default values.
            SharedPreferences.Editor editor = pref.edit();
            editor.putString("pref_hostName", getDefaultHostName(this));
            editor.putBoolean("pref_useTLS", !isEmulator());
            editor.apply();
        }

        // Check if the app has an account already. If so, initialize the shared connection with the server.
        // Initialization may fail if device is not connected to the network.
        String uid = BaseDb.getInstance().getUid();
        if (!TextUtils.isEmpty(uid)) {
            final AccountManager accountManager = AccountManager.get(this);
            // If uid is non-null, get account to use it to login by saved token
            final Account account = UiUtils.getSavedAccount(this, accountManager, uid);
            if (account != null) {
                // Check if sync is enabled.
                if (ContentResolver.getMasterSyncAutomatically()) {
                    if (!ContentResolver.getSyncAutomatically(account, Utils.SYNC_AUTHORITY)) {
                        ContentResolver.setSyncAutomatically(account, Utils.SYNC_AUTHORITY, true);
                    }
                }

                // Account found, establish connection to the server and use save account credentials for login.
                loginWithSavedAccount(accountManager, account);
            } else {
                Log.i(TAG, "Account not found or no permission to access accounts");
            }
        }
    }

    public static Context getAppContext() {
        return sContext;
    }

    public static String getAppVersion() {
        return sAppVersion;
    }

    public static int getAppBuild() {
        return sAppBuild;
    }

    public static String getDefaultHostName(Context context) {
        return context.getResources().getString(isEmulator() ?
                R.string.emulator_host_name :
                R.string.default_host_name);
    }

    public static boolean getDefaultTLS() {
        return !isEmulator();
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

    // Detect if the code is running in an emulator.
    // Used mostly for convenience to use correct server address i.e. 10.0.2.2:6060 vs sandbox.tinode.co and
    // to enable/disable Crashlytics. It's OK if it's imprecise.
    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("sdk_gphone_x86")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT)
                || Build.PRODUCT.startsWith("sdk")
                || Build.PRODUCT.startsWith("vbox");
    }

    // Read saved account credentials and try to connect to server using them.
    private void loginWithSavedAccount(final AccountManager accountManager,
                                       final Account account) {
        Log.i(TAG, "loginWithSavedAccount");

        accountManager.getAuthToken(account, Utils.TOKEN_TYPE, null, false, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    // Got tinode authentication token. Use it to connect to the server.
                    Bundle result = future.getResult(); // This blocks until the future is ready.
                    if (result != null) {
                        final String token = result.getString(AccountManager.KEY_AUTHTOKEN);
                        if (!TextUtils.isEmpty(token)) {
                            // Get server address.
                            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(TindroidApp.this);
                            String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName(TindroidApp.this));
                            boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());
                            if (TextUtils.isEmpty(hostName)) {
                                // Something is misconfigured, use defaults.
                                hostName = TindroidApp.getDefaultHostName(TindroidApp.this);
                                tls = TindroidApp.getDefaultTLS();
                            }

                            // Connecting with synchronous calls because this is not the UI thread.
                            final Tinode tinode = Cache.getTinode();
                            tinode.setAutoLoginToken(token);
                            // Connect and login.
                            try {
                                // Sync call throws on error.
                                tinode.connect(hostName, tls).getResult();

                                // Logged in successfully. Save refreshed token for future use.
                                accountManager.setAuthToken(account, Utils.TOKEN_TYPE, tinode.getAuthToken());
                            } catch (IOException ex) {
                                Log.d(TAG, "Network failure during login", ex);
                                // Do not invalidate token on network failure.
                            } catch (Exception ex) {
                                if (ex instanceof ServerResponseException) {
                                    Log.w(TAG, "Server rejected login sequence", ex);
                                    // Login failed due to invalid (expired) token or missing/disabled account.
                                    accountManager.invalidateAuthToken(Utils.ACCOUNT_TYPE, token);
                                    // Force login.
                                    BaseDb.getInstance().logout();
                                    // 409 Already authenticated should not be possible here.
                                } else {
                                    Log.e(TAG, "Other failure during login", ex);
                                }
                            }
                        }
                    }
                } catch (OperationCanceledException e) {
                    Log.i(TAG, "Request to get an existing account was canceled.", e);
                } catch (AuthenticatorException e) {
                    Log.e(TAG, "No access to saved account", e);
                } catch (Exception e) {
                    Log.e(TAG, "Failure to login with saved account", e);
                }

            }
        }, null);

    }
}
