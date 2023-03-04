package co.tinode.tindroid;

import android.content.Context;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ClientConfig {
    private static final String TAG = "ClientConfig";
    private static final String HOSTS = "https://hosts.tinode.co/id/";
    private static final int CHUNK_SIZE = 2048;

    private static final String CONFIG_FILE_NAME = "client_config.json";
    private static final String KEY_ID = "id";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_TOS_URL = "tos_url";
    private static final String KEY_PRIVACY_URL = "privacy_url";
    private static final String KEY_SERVICE_NAME = "service_name";
    private static final String KEY_ICON_SMALL = "icon_small";
    private static final String KEY_ICON_LARGE = "icon_large";
    private static final String KEY_ASSET_BASE = "assets_base";

    static void fetchClientConfig(Context context, String short_code) {
        OkHttpClient httpClient = new OkHttpClient();
        Request req = new Request.Builder().url(HOSTS + short_code).build();

        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                Log.i(TAG, "Client config request failed " + resp.code());
                return;
            }

            ResponseBody body = resp.body();
            if (body == null) {
                Log.i(TAG, "Received empty client config");
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
            Map<String, String> config = readConfig(context, json.getBytes());
            String assetBase = config.get(KEY_ASSET_BASE);
            if (TextUtils.isEmpty(assetBase)) {
                return;
            }
            String iconSmall = config.get(KEY_ICON_SMALL);
            if (!TextUtils.isEmpty(assetBase)) {
                saveAsset(context, httpClient, assetBase + iconSmall, "icon_small");
            }
            String iconLarge = config.get(KEY_ICON_LARGE);
            if (!TextUtils.isEmpty(assetBase)) {
                saveAsset(context, httpClient, assetBase + iconLarge, "icon_large");
            }
        } catch (IOException ex) {
            Log.i(TAG, "Failed to fetch client config", ex);
        }
    }

    static Map<String, String> readConfig(Context context, byte[] input) throws IOException {
        Map<String, String> config = new HashMap<>();
        JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(input)));
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            String value = reader.nextString();
            config.put(name, value);
        }
        reader.endObject();
        reader.close();
        return config;
    }

    static void saveAsset(Context context, OkHttpClient  client, String url, String dstFileName) {
        Request request = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                Log.i(TAG, "Asset request failed " + resp.code());
                return;
            }

            ResponseBody body = resp.body();
            if (body == null) {
                Log.i(TAG, "Empty asset " + url);
                return;
            }
            try (FileOutputStream fos = context.openFileOutput(dstFileName, Context.MODE_PRIVATE)) {
                write(body.byteStream(), fos);
            }
        } catch (IOException ex) {
            Log.i(TAG, "Failed to download asset " + url, ex);
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

    static void loadConfig() {
        
    }
}
