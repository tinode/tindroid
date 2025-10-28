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
import android.database.CursorWindow;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StrictMode;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.android.installreferrer.api.InstallReferrerClient;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.provider.FontRequest;
import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.FontRequestEmojiCompatConfig;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

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
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Tinode;

import coil.Coil;
import coil.ComponentRegistry;
import coil.ImageLoader;
import coil.intercept.Interceptor;
import coil.key.Keyer;
import coil.request.ImageRequest;
import coil.request.ImageResult;
import coil.util.DebugLogger;
import kotlin.coroutines.Continuation;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * A class for providing global context for database access
 */
public class TindroidApp extends Application implements DefaultLifecycleObserver {
    private static final String TAG = "TindroidApp";

    // 256 MB.
    private static final int COIL_CACHE_SIZE = 1024 * 1024 * 256;
    private static final int VIDEO_CACHE_SIZE = 1024 * 1024 * 256;

    private static TindroidApp sContext;

    private static ContentObserver sContactsObserver = null;

    // The Tinode cache is linked from here so it's never garbage collected.
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static Cache sCache;

    @OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private static SimpleCache sVideoCache;

    private static String sAppVersion = null;
    private static int sAppBuild = 0;

    private AudioControl mAudioControl = null;

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

    public static String getDefaultHostName() {
        return sContext.getResources().getString(isEmulator() ?
                R.string.emulator_host_name :
                R.string.default_host_name);
    }

    public static boolean getDefaultTLS() {
        return !isEmulator();
    }

    public static void retainCache(Cache cache) {
        sCache = cache;
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

        // Hack to increase the size of CursorWindow to 8MB.
        try {
            @SuppressLint("DiscouragedPrivateApi")
            Field field = CursorWindow.class.getDeclaredField("sCursorWindowSize");
            field.setAccessible(true);
            Object val = field.get(null);
            final int size = 8 * 1024 * 1024;
            if (val instanceof Number && ((Number) val).intValue() < size) {
                // Increase cursor window size from 2MB to 8MB.
                field.set(null, size);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set CursorWindow size", e);
        }

        if (BuildConfig.DEBUG) {
            // Enable StrictMode to catch accidental disk or network access on the main thread.
            StrictMode.setVmPolicy(
                    new StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                            .detectLeakedClosableObjects()
                            .penaltyLog() // or penaltyDeath()
                            .build()
            );
        }

        // Load emoji font and initialize EmojiCompat library.
        FontRequest fontRequest = new FontRequest(
                "com.google.android.gms.fonts",
                "com.google.android.gms",
                "Noto Color Emoji Compat",
                R.array.com_google_android_gms_fonts_certs);
        EmojiCompat.init(new FontRequestEmojiCompatConfig(this, fontRequest));

        // Disable Crashlytics for debug builds.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);

        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String token = intent.getStringExtra("token");
                if (token != null && !token.isEmpty()) {
                    Cache.getTinode().setDeviceToken(token);
                }
            }
        };
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(br, new IntentFilter("FCM_REFRESH_TOKEN"));
        lbm.registerReceiver(new HangUpBroadcastReceiver(), new IntentFilter(Const.INTENT_ACTION_CALL_CLOSE));

        createNotificationChannels();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        // Check if preferences already exist. If not, create them.
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if (TextUtils.isEmpty(pref.getString(Utils.PREFS_HOST_NAME, null))) {
            // No preferences found. Save default values.
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(Utils.PREFS_HOST_NAME, getDefaultHostName());
            editor.putBoolean(Utils.PREFS_USE_TLS, getDefaultTLS());
            editor.apply();
        }

        // Set UI mode.
        onThemeChanged(pref.getString(Utils.PREFS_UI_MODE, Utils.PREFS_KEY_AUTO_THEME));

        // Clear completed/failed upload tasks.
        WorkManager.getInstance(this).pruneWork();

        // Setting up Coil with auth headers.
        OkHttpClient client = new OkHttpClient.Builder()
                .cache(new okhttp3.Cache(createDefaultCacheDir(this), COIL_CACHE_SIZE))
                .addInterceptor(chain -> {
                    // Add authentication headers to requests.
                    Tinode tinode = Cache.getTinode();
                    Request request = chain.request();
                    Map<String, String> headers;
                    if (tinode.isTrustedURL(request.url().url())) {
                        headers = tinode.getRequestHeaders();
                        Request.Builder rb = request.newBuilder();
                        for (Map.Entry<String, String> el : headers.entrySet()) {
                            rb = rb.addHeader(el.getKey(), el.getValue());
                        }
                        return chain.proceed(rb.build());
                    } else {
                        return chain.proceed(request);
                    }
                })
                .build();
        ComponentRegistry.Builder crb = new ComponentRegistry().newBuilder();
        crb.add((Keyer<Uri>) (uri, options) -> {
            // For caching purposes, remove transient query parameters from Uri
            // such as X-Amz-Signature or X-Goog-Date.
            // Credential is removed because X-Amz-Credential contains current date.
            String[] tempKeys = {"credential", "date", "signature"};
            Set<String> query = uri.getQueryParameterNames();
            query.removeIf(key -> Arrays.stream(tempKeys).anyMatch(key.toLowerCase()::contains));
            uri.buildUpon().clearQuery().query(query.toString());
            return uri.toString();
        }, Uri.class);
        crb.add(new Interceptor() {
            @Nullable
            @Override
            public Object intercept(@NonNull Chain chain, @NonNull Continuation<? super ImageResult> continuation) {
                // Rewrite relative URIs to absolute.
                ImageRequest request = chain.getRequest();
                String data = request.getData().toString();
                if (Tinode.isUrlRelative(data)) {
                    URL url = Cache.getTinode().toAbsoluteURL(data);
                    if (url != null) {
                        return chain.proceed(request.newBuilder().data(url.toString()).build(), continuation);
                    }
                }
                return chain.proceed(request, continuation);
            }
        });
        ImageLoader loader = new ImageLoader.Builder(this)
                .okHttpClient(client)
                .components(crb.build())
                .logger(new DebugLogger(Log.VERBOSE))
                .build();
        Coil.setImageLoader(loader);

        mAudioControl = new AudioControl(this);

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
                if (!TextUtils.isEmpty(BaseDb.getInstance().getUid())) {
                    // Connect right away if UID is available.
                    Cache.getTinode().reconnectNow(true, false, false);
                }
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
        // Check if the app was installed from an URL with attributed installation source.
        // If yes, get the config from hosts.tinode.co.
        if (UiUtils.isAppFirstRun(sContext)) {
            Executors.newSingleThreadExecutor().execute(() ->
                    BrandingConfig.getInstallReferrerFromClient(sContext,
                            InstallReferrerClient.newBuilder(this).build()));
        }

        // Check if the app has an account already. If so, initialize the shared connection with the server.
        // Initialization may fail if device is not connected to the network.
        String uid = BaseDb.getInstance().getUid();
        if (!TextUtils.isEmpty(uid)) {
            Executors.newSingleThreadExecutor().execute(() -> loginInBackground(uid));
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        // Disconnect now, so the connection does not wait for the timeout.
        if (Cache.getTinode() != null) {
            Cache.getTinode().maybeDisconnect(false);
        }
    }

    private void createNotificationChannels() {
        // Create the NotificationChannel.
        NotificationChannel newMessage = new NotificationChannel(Const.NEWMSG_NOTIFICATION_CHAN_ID,
                getString(R.string.new_message_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        newMessage.setDescription(getString(R.string.new_message_channel_description));
        newMessage.enableLights(true);
        newMessage.setLightColor(Color.WHITE);

        NotificationChannel videoCall = new NotificationChannel(Const.CALL_NOTIFICATION_CHAN_ID,
                getString(R.string.video_call_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        videoCall.setDescription(getString(R.string.video_call_channel_description));
        videoCall.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                        new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .build());
        videoCall.setVibrationPattern(new long[]{0, 1000, 500, 1000});
        videoCall.enableVibration(true);
        videoCall.enableLights(true);
        videoCall.setLightColor(Color.RED);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(newMessage);
            nm.createNotificationChannel(videoCall);
        }
    }

//This declaration is opt-in and its usage should be marked with `@androidx.media3.common.util.UnstableApi` or `@OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)`
    @OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    public static synchronized SimpleCache getVideoCache() {
        String dirs = sContext.getCacheDir().getAbsolutePath();
        if (sVideoCache == null) {
            String path = dirs + File.separator + "video";
            boolean isLocked = SimpleCache.isCacheFolderLocked(new File(path));
            if (!isLocked) {
                sVideoCache = new SimpleCache(new File(path),
                        new LeastRecentlyUsedCacheEvictor(VIDEO_CACHE_SIZE),
                        new StandaloneDatabaseProvider(sContext));
            }
        }

        return sVideoCache;
    }

    public static AudioManager getAudioManager() {
        return sContext.mAudioControl.getAudioManager();
    }
    public static void setAudioMode(int mode) {
        sContext.mAudioControl.setMode(mode);
    }

    public static void setMicrophoneMute(boolean mute) {
        sContext.mAudioControl.setMicrophoneMute(mute);
    }

    public static boolean setSpeakerphoneOn(boolean enable) {
        return sContext.mAudioControl.setSpeakerphoneOn(enable);
    }

    public static void abandonAudioFocus() {
        sContext.mAudioControl.abandonAudioFocus();
    }

    public static boolean isSpeakerphoneOn() {
        return sContext.mAudioControl.isSpeakerphoneOn();
    }

    // Read saved account credentials and try to connect to server using them.
    private void loginInBackground(String uid) {
        final AccountManager accountManager = AccountManager.get(TindroidApp.this);
        final Account account = Utils.getSavedAccount(accountManager, uid);
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
                Log.d(TAG, "Request to get an existing account was canceled.", e);
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
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(TindroidApp.this);
                    // Sync call throws on error.
                    tinode.connect(pref.getString(Utils.PREFS_HOST_NAME, getDefaultHostName()),
                            pref.getBoolean(Utils.PREFS_USE_TLS, getDefaultTLS()),
                            false).getResult();
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
            Log.w(TAG, "Account not found or no permission to access accounts");
            // Force new login in case account existed before but was deleted.
            UiUtils.doLogout(TindroidApp.this);
        }
    }

    public static void onThemeChanged(@NonNull String themeKey) {
        switch (themeKey) {
            case Utils.PREFS_KEY_LIGHT_THEME:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case Utils.PREFS_KEY_DARK_THEME:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
