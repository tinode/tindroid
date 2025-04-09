package co.tinode.tinodesdk;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.ServerMessage;

public class LargeFileHelper {
    private static final int BUFFER_SIZE = 65536;
    private static final String TWO_HYPHENS = "--";
    private static final String BOUNDARY = "*****" + System.currentTimeMillis() + "*****";
    private static final String LINE_END = "\r\n";

    private final URL mUrlUpload;
    private final String mHost;
    private final String mApiKey;
    private final String mAuthToken;
    private final String mUserAgent;

    private boolean mCanceled = false;

    private int mReqId = 1;

    public LargeFileHelper(URL urlUpload, String apikey, String authToken, String userAgent) {
        mUrlUpload = urlUpload;
        mHost = mUrlUpload.getHost();
        mApiKey = apikey;
        mAuthToken = authToken;
        mUserAgent = userAgent;
    }

    // Upload file out of band. Blocking operation: it should not be called on the UI thread.
    public ServerMessage upload(@NotNull InputStream in, @NotNull String filename, @NotNull String mimetype, long size,
                                @Nullable String topic, @Nullable FileHelperProgress progress)
            throws IOException, CancellationException {
        mCanceled = false;
        HttpURLConnection conn = null;
        ServerMessage msg;
        try {
            conn = (HttpURLConnection) mUrlUpload.openConnection();
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("User-Agent", mUserAgent);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            conn.setRequestProperty("X-Tinode-APIKey", mApiKey);
            if (mAuthToken != null) {
                // mAuthToken could be null when uploading avatar on sign up.
                conn.setRequestProperty("X-Tinode-Auth", "Token " + mAuthToken);
            }
            conn.setChunkedStreamingMode(0);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(conn.getOutputStream()));
            // Write req ID.
            out.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);
            out.writeBytes("Content-Disposition: form-data; name=\"id\"" + LINE_END);
            out.writeBytes(LINE_END);
            out.writeBytes(++mReqId + LINE_END);

            // Write topic.
            if (topic != null) {
                out.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);
                out.writeBytes("Content-Disposition: form-data; name=\"topic\"" + LINE_END);
                out.writeBytes(LINE_END);
                out.writeBytes(topic + LINE_END);
            }

            // File section.
            out.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);
            // Content-Disposition: form-data; name="file"; filename="1519014549699.pdf"
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; ");
            String encFileName = URLEncoder.encode(filename, "UTF-8");
            if (filename.equals(encFileName)) {
                // Plain ASCII file name.
                out.writeBytes("filename=\"" + filename + "\"");
            } else {
                // URL-encoded file name.
                out.writeBytes("filename*=UTF-8''" + encFileName);
            }
            out.writeBytes(LINE_END);
            // Content-Type: application/pdf
            out.writeBytes("Content-Type: " + mimetype + LINE_END);
            out.writeBytes("Content-Transfer-Encoding: binary" + LINE_END);
            out.writeBytes(LINE_END);

            // File bytes.
            copyStream(in, out, size, progress);
            out.writeBytes(LINE_END);

            // End of form boundary.
            out.writeBytes(TWO_HYPHENS + BOUNDARY + TWO_HYPHENS + LINE_END);
            out.flush();
            out.close();

            if (conn.getResponseCode() != 200) {
                throw new IOException("Failed to upload: " + conn.getResponseMessage() +
                        " (" + conn.getResponseCode() + ")");
            }

            InputStream resp = new BufferedInputStream(conn.getInputStream());
            msg = readServerResponse(resp);
            resp.close();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return msg;
    }

    // Uploads the file using Runnable, returns PromisedReply. Safe to call on UI thread.
    public PromisedReply<ServerMessage> uploadAsync(@NotNull InputStream in, @NotNull String filename,
                                                    @NotNull String mimetype, long size,
                                                    @Nullable String topic, @Nullable FileHelperProgress progress) {
        final PromisedReply<ServerMessage> result = new PromisedReply<>();
        new Thread(() -> {
            try {
                ServerMessage msg = upload(in, filename, mimetype, size, topic, progress);
                if (mCanceled) {
                    throw new CancellationException("Cancelled");
                }
                result.resolve(msg);
            } catch (Exception ex) {
                try {
                    result.reject(ex);
                } catch (Exception ignored) {
                }
            }
        }).start();
        return result;
    }

    // Download file from the given URL if the URL's host is the default host. Should not be called on the UI thread.
    public long download(String downloadFrom, OutputStream out, FileHelperProgress progress)
            throws IOException, CancellationException {
        URL url = new URL(downloadFrom);
        long size = 0;
        String scheme = url.getProtocol();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            // As a security measure refuse to download using non-http(s) protocols.
            return size;
        }
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            if (url.getHost().equals(mHost)) {
                // Send authentication only if the host is known.
                urlConnection.setRequestProperty("X-Tinode-APIKey", mApiKey);
                urlConnection.setRequestProperty("X-Tinode-Auth", "Token " + mAuthToken);
            }
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            return copyStream(in, out, urlConnection.getContentLength(), progress);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    // Downloads the file using Runnable, returns PromisedReply. Safe to call on UI thread.
    public PromisedReply<Long> downloadFuture(final String downloadFrom,
                                                 final OutputStream out,
                                                 final FileHelperProgress progress) {
        final PromisedReply<Long> result = new PromisedReply<>();
        new Thread(() -> {
            try {
                Long size = download(downloadFrom, out, progress);
                if (mCanceled) {
                    throw new CancellationException("Cancelled");
                }
                result.resolve(size);
            } catch (Exception ex) {
                try {
                    result.reject(ex);
                } catch (Exception ignored) {
                }
            }
        }).start();
        return result;
    }

    // Try to cancel an ongoing upload or download.
    public void cancel() {
        mCanceled = true;
    }

    public boolean isCanceled() {
        return mCanceled;
    }

    private int copyStream(@NotNull InputStream in, @NotNull OutputStream out, long size, @Nullable FileHelperProgress p)
            throws IOException, CancellationException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int len, sent = 0;
        while ((len = in.read(buffer)) != -1) {
            if (mCanceled) {
                throw new CancellationException("Cancelled");
            }

            sent += len;
            out.write(buffer, 0, len);

            if (mCanceled) {
                throw new CancellationException("Cancelled");
            }

            if (p != null) {
                p.onProgress(sent, size);
            }
        }
        return sent;
    }

    private ServerMessage readServerResponse(InputStream in) throws IOException {
        MsgServerCtrl ctrl = null;
        ObjectMapper mapper = Tinode.getJsonMapper();
        JsonParser parser = mapper.getFactory().createParser(in);
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException(parser, "Packet must start with an object",
                    parser.currentLocation());
        }
        if (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.currentName();
            parser.nextToken();
            JsonNode node = mapper.readTree(parser);
            if (name.equals("ctrl")) {
                ctrl = mapper.readValue(node.traverse(), MsgServerCtrl.class);
            } else {
                throw new JsonParseException(parser, "Unexpected message '" + name + "'",
                        parser.currentLocation());
            }
        }
        return new ServerMessage(ctrl);
    }

    public interface FileHelperProgress {
        void onProgress(long sent, long size);
    }

    public Map<String,String> headers() {
        Map<String,String> headers = new HashMap<>();
        headers.put("X-Tinode-APIKey", mApiKey);
        headers.put("X-Tinode-Auth", "Token " + mAuthToken);
        return headers;
    }
}
