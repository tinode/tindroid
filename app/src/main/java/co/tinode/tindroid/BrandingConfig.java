package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.Log;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BrandingConfig {
    private static final String TAG = "ClientConfig";
    private static final String HOSTS = "https://hosts.tinode.co/id/";
    private static final int CHUNK_SIZE = 2048;

    private static final String CONFIG_FILE_NAME = "client_config.json";
    private static final String KEY_ID = "id";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_TOS_URL = "tos_url";
    private static final String KEY_PRIVACY_URL = "privacy_url";
    private static final String KEY_CONTACT_US_URL = "contact_url";
    private static final String KEY_SERVICE_NAME = "service_name";
    private static final String KEY_ICON_SMALL = "icon_small";
    private static final String KEY_ICON_LARGE = "icon_large";
    private static final String KEY_ASSET_BASE = "assets_base";

    private static Map<String, String> sRawConfig = null;

    public String id;
    public String api_url;
    public String tos_uri;
    public String privacy_uri;
    public String contact_us_uri;
    public String service_name;
    public String icon_small;
    public String icon_large;

    private static BrandingConfig sConfig = null;

    private BrandingConfig() {}

    public static BrandingConfig getConfig(Context context) {
        if (sConfig == null) {
            if (sRawConfig == null) {
                try {
                    loadConfig(context);
                } catch (IOException ignored) {}
            }

            if (sRawConfig != null) {
                sConfig = new BrandingConfig();
                sConfig.id = sRawConfig.get(KEY_ID);
                sConfig.api_url = sRawConfig.get(KEY_API_URL);
                sConfig.tos_uri = sRawConfig.get(KEY_TOS_URL);
                sConfig.privacy_uri = sRawConfig.get(KEY_PRIVACY_URL);
                sConfig.contact_us_uri = sRawConfig.get(KEY_CONTACT_US_URL);
                sConfig.service_name = sRawConfig.get(KEY_SERVICE_NAME);
                sConfig.icon_small = sRawConfig.get(KEY_ICON_SMALL);
                sConfig.icon_large = sRawConfig.get(KEY_ICON_LARGE);
            }
        }

        return sConfig;
    }

    public static Bitmap getSmallIcon(Context context) {
        BrandingConfig conf = getConfig(context);
        if (conf == null || TextUtils.isEmpty(conf.icon_small)) {
            return null;
        }
        return BitmapFactory.decodeFile(conf.icon_small);
    }

    public static Bitmap getLargeIcon(Context context) {
        BrandingConfig conf = getConfig(context);
        if (conf == null || TextUtils.isEmpty(conf.icon_large)) {
            return null;
        }
        return BitmapFactory.decodeFile(conf.icon_large);
    }

    static void fetchConfigFromServer(final Context context, String short_code, ReadyListener listener) {

        OkHttpClient httpClient = new OkHttpClient();
        Request req = new Request.Builder().url(HOSTS + short_code).build();

        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                Log.w(TAG, "Client config request failed " + resp.code());
                return;
            }

            ResponseBody body = resp.body();
            if (body == null) {
                Log.w(TAG, "Received empty client config");
                return;
            }

            /*
            {
                "id": "AB6WU",
                "api_url": "https://api.tinode.co",
                "tos_url": "https://tinode.co/terms.html",
                "privacy_url": "https://tinode.co/privacy.html",
                "service_name": "Tinode",
                "icon_small": "small/tn-60480b81.png",
                "icon_large": "large/tn-60480b82.png",
                "assets_base": "https://storage.googleapis.com/hosts.tinode.co/"
            }
            */
            String json = body.string();
            try (FileOutputStream fos = context.openFileOutput(CONFIG_FILE_NAME, Context.MODE_PRIVATE)) {
                fos.write(json.getBytes());
            }
            Map<String, String> config = readConfig(json.getBytes());
            String assetBase = config.get(KEY_ASSET_BASE);
            if (TextUtils.isEmpty(assetBase)) {
                return;
            }
            String iconSmall = config.get(KEY_ICON_SMALL);
            if (!TextUtils.isEmpty(assetBase)) {
                saveAsset(context, httpClient, assetBase + iconSmall, KEY_ICON_SMALL);
            }
            String iconLarge = config.get(KEY_ICON_LARGE);
            if (!TextUtils.isEmpty(assetBase)) {
                saveAsset(context, httpClient, assetBase + iconLarge, KEY_ICON_LARGE);
            }
            setRawConfig(context, config);

            String apiUrl = config.get(KEY_API_URL);
            if (!TextUtils.isEmpty(apiUrl)) {
                Uri apiUri = Uri.parse(apiUrl);
                String scheme = apiUri.getScheme();
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                sharedPreferences.edit()
                        .putString(Utils.PREFS_HOST_NAME, apiUri.getAuthority())
                        .putBoolean(Utils.PREFS_USE_TLS,
                                scheme != null && "https".equals(scheme.toLowerCase(Locale.ROOT)))
                        .apply();
            }

            UiUtils.doneAppFirstRun(context);
        } catch (IOException ex) {
            Log.w(TAG, "Failed to fetch client config", ex);
        }

        if (listener != null) {
            listener.onReady(getConfig(context));
        }
    }

    static Map<String, String> readConfig(byte[] input) throws IOException {
        return readConfig(new ByteArrayInputStream(input));
    }

    static @Nullable Map<String, String> readConfig(InputStream input) throws IOException {
        Map<String, String> config = new HashMap<>();
        JsonReader reader = new JsonReader(new InputStreamReader(input));
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            String value = reader.nextString();
            config.put(name, value);
        }
        reader.endObject();
        reader.close();
        return config.isEmpty() ? null : config;
    }

    static void saveAsset(Context context, OkHttpClient  client, String url, String dstFileName) {
        Request request = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                Log.w(TAG, "Asset request failed " + resp.code());
                return;
            }

            ResponseBody body = resp.body();
            if (body == null) {
                Log.w(TAG, "Empty asset " + url);
                return;
            }
            try (FileOutputStream fos = context.openFileOutput(dstFileName, Context.MODE_PRIVATE)) {
                write(body.byteStream(), fos);
            }
        } catch (IOException ex) {
            Log.w(TAG, "Failed to download asset " + url, ex);
        }
    }

    static void write(InputStream inStream, OutputStream outStream) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(inStream)) {
            byte[] dataBuffer = new byte[CHUNK_SIZE];
            int readBytes;
            while ((readBytes = input.read(dataBuffer)) != -1) {
                outStream.write(dataBuffer, 0, readBytes);
            }
        }
    }

    private static void loadConfig(Context context) throws IOException {
        Map<String, String> config;
        try (FileInputStream fis = context.openFileInput(CONFIG_FILE_NAME)) {
            config = readConfig(fis);
        }

        if (config != null) {
            setRawConfig(context, config);
        }
    }

    private static void setRawConfig(Context context, Map<String, String> config) {
        sRawConfig = config;

        File asset = new File(context.getFilesDir(), KEY_ICON_SMALL);
        sRawConfig.put(KEY_ICON_SMALL, asset.getAbsolutePath());
        asset = new File(context.getFilesDir(), KEY_ICON_LARGE);
        sRawConfig.put(KEY_ICON_LARGE, asset.getAbsolutePath());
    }
    // Check if the app was installed from an URL with attributed installation source.

    static void getInstallReferrerFromClient(Context context, InstallReferrerClient referrerClient) {
        referrerClient.startConnection(new InstallReferrerStateListener() {
            @SuppressLint("ApplySharedPref")
            @Override
            public void onInstallReferrerSetupFinished(int responseCode) {
                switch (responseCode) {

                    case InstallReferrerClient.InstallReferrerResponse.OK:
                        ReferrerDetails response;
                        try {
                            response = referrerClient.getInstallReferrer();
                        } catch (RemoteException ex) {
                            Log.w(TAG, "Unable to retrieve installation source", ex);
                            return;
                        }

                        String referrerUrl = response.getInstallReferrer();

                        if (!TextUtils.isEmpty(referrerUrl)) {
                            // https://play.google.com/store/apps/details?id=co.tinode.tindroidx&referrer=utm_source%3Dtinode%26utm_term%3Dshort_code
                            Uri ref = Uri.parse(referrerUrl);
                            String source = ref.getQueryParameter("utm_source");
                            String short_code = ref.getQueryParameter("utm_term");
                            if (!"tinode".equals(source) || TextUtils.isEmpty(short_code)) {
                                Log.w(TAG, "InstallReferrer code is unavailable");
                            } else {
                                fetchConfigFromServer(context, short_code, null);
                            }
                        }

                        referrerClient.endConnection();
                        break;

                    case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                        // API not available on the current Play Store app.
                        Log.w(TAG, "InstallReferrer API not available");
                        break;

                    case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                        // Connection couldn't be established.
                        Log.w(TAG, "Failed to connect to PlayStore: InstallReferrer unavailable");
                        break;
                }
            }

            @Override
            public void onInstallReferrerServiceDisconnected() { }
        });
    }

    public interface ReadyListener {
        void onReady(BrandingConfig config);
    }
}
