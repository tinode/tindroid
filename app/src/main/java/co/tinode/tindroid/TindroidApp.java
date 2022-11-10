package co.tinode.tindroid;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.SuppressLint;
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
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.work.WorkManager;
import co.tinode.tindroid.account.ContactsObserver;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * A class for providing global context for database access
 */
public class TindroidApp extends Application implements DefaultLifecycleObserver {
    private static final String TAG = "TindroidApp";

    // 32 MB.
    private static final int PICASSO_CACHE_SIZE = 1024 * 1024 * 32;

    private static TindroidApp sContext;

    private static ContentObserver sContactsObserver = null;

    // The Tinode cache is linked from here so it's never garbage collected.
    private static Cache sCache;

    private static String sAppVersion = null;
    private static int sAppBuild = 0;

    private static String sServerHost = null;
    private static boolean sUseTLS = false;

    public TindroidApp() {
        sContext = this;
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

    public static void retainCache(Cache cache) {
        sCache = cache;
        Cache.setServer(sServerHost, sUseTLS);
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

    static synchronized void startWatchingContacts(Context context, Account acc) {
        if (sContactsObserver == null) {
            // Check if we have already obtained contacts permissions.
            if (!UiUtils.isPermissionGranted(context, Manifest.permission.READ_CONTACTS)) {
                // No permissions, can't set up contacts sync.
                return;
            }

            // Create and start a new thread set up as a looper.
            HandlerThread thread = new HandlerThread("ContactsObserverHandlerThread");
            thread.start();

            sContactsObserver = new ContactsObserver(acc, new Handler(thread.getLooper()));
            // Observer which triggers sync when contacts change.
            sContext.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                    true, sContactsObserver);
        }
    }

    static synchronized void stopWatchingContacts() {
        if (sContactsObserver != null) {
            sContext.getContentResolver().unregisterContentObserver(sContactsObserver);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            sAppVersion = pi.versionName;
            if (TextUtils.isEmpty(sAppVersion)) {
                sAppVersion = BuildConfig.VERSION_NAME;
            }
            sAppBuild = pi.versionCode;
            if (sAppBuild <= 0) {
                sAppBuild = BuildConfig.VERSION_CODE;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to retrieve app version", e);
        }

        // Disable Crashlytics for debug builds.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);

        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String token = intent.getStringExtra("token");
                if (token != null && !token.equals("")) {
                    Cache.getTinode().setDeviceToken(token);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(br, new IntentFilter("FCM_REFRESH_TOKEN"));

        createNotificationChannel();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        // Check if preferences already exist. If not, create them.
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        sServerHost = pref.getString("pref_hostName", null);
        if (TextUtils.isEmpty(sServerHost)) {
            // No preferences found. Save default values.
            SharedPreferences.Editor editor = pref.edit();
            sServerHost = getDefaultHostName(this);
            sUseTLS = getDefaultTLS();
            editor.putString("pref_hostName", sServerHost);
            editor.putBoolean("pref_useTLS", sUseTLS);
            editor.apply();
        } else {
            sUseTLS = pref.getBoolean("pref_useTLS", false);
        }
        // Event handlers for video calls.
        Cache.getTinode().addListener(new Tinode.EventListener() {
            @Override
            public void onDataMessage(MsgServerData data) {
                if (Cache.getTinode().isMe(data.from)) {
                    return;
                }
                String webrtc = data.getStringHeader("webrtc");
                if (MsgServerData.parseWebRTC(webrtc) != MsgServerData.WebRTC.STARTED) {
                    return;
                }
                ComTopic topic = (ComTopic) Cache.getTinode().getTopic(data.topic);
                if (topic == null) {
                    return;
                }

                // Check if we have a later version of the message (which means the call
                // has been not yet been either accepted or finished).
                Storage.Message msg = topic.getMessage(data.seq);
                if (msg != null) {
                    webrtc = msg.getStringHeader("webrtc");
                    if (webrtc != null && MsgServerData.parseWebRTC(webrtc) != MsgServerData.WebRTC.STARTED) {
                        return;
                    }
                }

                CallInProgress call = Cache.getCallInProgress();
                if (call == null) {
                    // Call invite from the peer.
                    Intent intent = new Intent();
                    intent.setAction(CallActivity.INTENT_ACTION_CALL_INCOMING);
                    intent.putExtra("topic", data.topic);
                    intent.putExtra("seq", data.seq);
                    intent.putExtra("from", data.from);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    TindroidApp.this.startActivity(intent);
                } else if (!call.equals(data.topic, data.seq)) {
                    // Another incoming call. Decline.
                    topic.videoCallHangUp(data.seq);
                }
            }

            @Override
            public void onInfoMessage(MsgServerInfo info) {
                if (MsgServerInfo.parseWhat(info.what) != MsgServerInfo.What.CALL) {
                    return;
                }
                if (MsgServerInfo.parseEvent(info.event) != MsgServerInfo.Event.ACCEPT) {
                    return;
                }

                CallInProgress call = Cache.getCallInProgress();
                if (Tinode.TOPIC_ME.equals(info.topic) && Cache.getTinode().isMe(info.from) &&
                    call != null && call.equals(info.src, info.seq)) {
                    // Another client has accepted the call. Dismiss call notification.
                    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(TindroidApp.this);
                    Intent intent = new Intent(CallActivity.INTENT_ACTION_CALL_CLOSE);
                    intent.putExtra("topic", info.src);
                    intent.putExtra("seq", info.seq);
                    lbm.sendBroadcast(intent);
                }
            }
        });

        // Clear completed/failed upload tasks.
        WorkManager.getInstance(this).pruneWork();

        // Setting up Picasso with auth headers.
        OkHttpClient client = new OkHttpClient.Builder()
                .cache(new okhttp3.Cache(createDefaultCacheDir(this), PICASSO_CACHE_SIZE))
                .addInterceptor(chain -> {
                    Request picassoReq = chain.request();
                    Map<String, String> headers;
                    if (Cache.getTinode().isTrustedURL(picassoReq.url().url()) &&
                            (headers = Cache.getTinode().getRequestHeaders()) != null) {
                        Request.Builder builder = picassoReq.newBuilder();
                        for (Map.Entry<String, String> el : headers.entrySet()) {
                            builder = builder.addHeader(el.getKey(), el.getValue());
                        }
                        return chain.proceed(builder.build());
                    } else {
                        return chain.proceed(picassoReq);
                    }
                })
                .build();
        Picasso.setSingletonInstance(new Picasso.Builder(this)
                .downloader(new OkHttp3Downloader(client))
                .build());

        // Listen to connectivity changes.
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        NetworkRequest req = new NetworkRequest.
                Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        cm.registerNetworkCallback(req, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Cache.getTinode().reconnectNow(true, false, false);
            }
        });
    }

    static File createDefaultCacheDir(Context context) {
        File cache = new File(context.getApplicationContext().getCacheDir(), "picasso-cache");
        if (!cache.exists()) {
            // noinspection ResultOfMethodCallIgnored
            cache.mkdirs();
        }
        return cache;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // Check if the app has an account already. If so, initialize the shared connection with the server.
        // Initialization may fail if device is not connected to the network.
        String uid = BaseDb.getInstance().getUid();
        if (!TextUtils.isEmpty(uid)) {
            new LoginWithSavedAccount().execute(uid);
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        // Disconnect now, so the connection does not wait for the timeout.
        if (Cache.getTinode() != null) {
            Cache.getTinode().maybeDisconnect(false);
        }
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

    // Read saved account credentials and try to connect to server using them.
    // Suppressed lint warning because TindroidApp won't leak: it must exist for the entire lifetime of the app.
    @SuppressLint("StaticFieldLeak")
    private class LoginWithSavedAccount extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... uidWrapper) {
            final AccountManager accountManager = AccountManager.get(TindroidApp.this);
            final Account account = Utils.getSavedAccount(accountManager, uidWrapper[0]);
            if (account != null) {
                // Check if sync is enabled.
                if (ContentResolver.getMasterSyncAutomatically()) {
                    if (!ContentResolver.getSyncAutomatically(account, Utils.SYNC_AUTHORITY)) {
                        ContentResolver.setSyncAutomatically(account, Utils.SYNC_AUTHORITY, true);
                    }
                }

                // Account found, establish connection to the server and use save account credentials for login.
                String token = null;
                Date expires = null;
                try {
                    token = accountManager.blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
                    String strExp = accountManager.getUserData(account, Utils.TOKEN_EXPIRATION_TIME);
                    // FIXME: remove this check when all clients are updated; Apr 8, 2020.
                    if (!TextUtils.isEmpty(strExp)) {
                        expires = new Date(Long.parseLong(strExp));
                    }
                } catch (OperationCanceledException e) {
                    Log.i(TAG, "Request to get an existing account was canceled.", e);
                } catch (AuthenticatorException e) {
                    Log.e(TAG, "No access to saved account", e);
                } catch (Exception e) {
                    Log.e(TAG, "Failure to login with saved account", e);
                }

                // Must instantiate tinode cache even if token == null. Otherwise logout won't work.
                final Tinode tinode = Cache.getTinode();
                if (!TextUtils.isEmpty(token) && expires != null && expires.after(new Date())) {
                    // Connecting with synchronous calls because this is not the UI thread.
                    tinode.setAutoLoginToken(token);
                    // Connect and login.
                    try {
                        // Sync call throws on error.
                        tinode.connect(sServerHost, sUseTLS, false).getResult();
                        if (!tinode.isAuthenticated()) {
                            // The connection may already exist but not yet authenticated.
                            tinode.loginToken(token).getResult();
                        }
                        Cache.attachMeTopic(null);
                        // Logged in successfully. Save refreshed token for future use.
                        accountManager.setAuthToken(account, Utils.TOKEN_TYPE, tinode.getAuthToken());
                        accountManager.setUserData(account, Utils.TOKEN_EXPIRATION_TIME,
                                String.valueOf(tinode.getAuthTokenExpiration().getTime()));
                        startWatchingContacts(TindroidApp.this, account);
                        // Trigger sync to be sure contacts are up to date.
                        UiUtils.requestImmediateContactsSync(account);
                    } catch (IOException ex) {
                        Log.d(TAG, "Network failure during login", ex);
                        // Do not invalidate token on network failure.
                    } catch (ServerResponseException ex) {
                        Log.w(TAG, "Server rejected login sequence", ex);
                        int code = ex.getCode();
                        // 401: Token expired or invalid login.
                        // 404: 'me' topic is not found (user deleted, but token is still valid).
                        if (code == 401 || code == 404) {
                            // Another try-catch because some users revoke needed permission after granting it.
                            try {
                                // Login failed due to invalid (expired) token or missing/disabled account.
                                accountManager.invalidateAuthToken(Utils.ACCOUNT_TYPE, null);
                                accountManager.setUserData(account, Utils.TOKEN_EXPIRATION_TIME, null);
                            } catch (SecurityException ex2) {
                                Log.e(TAG, "Unable to access android account", ex2);
                            }
                            // Force new login.
                            UiUtils.doLogout(TindroidApp.this);
                        }
                        // 409 Already authenticated should not be possible here.
                    } catch (Exception ex) {
                        Log.e(TAG, "Other failure during login", ex);
                    }
                } else {
                    Log.i(TAG, "No token or expired token. Forcing re-login");
                    try {
                        if (!TextUtils.isEmpty(token)) {
                            accountManager.invalidateAuthToken(Utils.ACCOUNT_TYPE, null);
                        }
                        accountManager.setUserData(account, Utils.TOKEN_EXPIRATION_TIME, null);
                    } catch (SecurityException ex) {
                        Log.e(TAG, "Unable to access android account", ex);
                    }
                    // Force new login.
                    UiUtils.doLogout(TindroidApp.this);
                }
            } else {
                Log.i(TAG, "Account not found or no permission to access accounts");
                // Force new login in case account existed before but was deleted.
                UiUtils.doLogout(TindroidApp.this);
            }
            return null;
        }
    }
}
