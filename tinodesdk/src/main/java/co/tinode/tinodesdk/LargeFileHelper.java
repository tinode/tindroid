package co.tinode.tinodesdk;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import co.tinode.tinodesdk.model.MsgServerCtrl;

public class LargeFileHelper {
    private static final int BUFFER_SIZE = 65536;
    private static final String TWO_HYPHENS = "--";
    private static final String BOUNDARY = "*****" + Long.toString(System.currentTimeMillis()) + "*****";
    private static final String LINE_END = "\r\n";

    private URL mUrlUpload;
    private String mHost;
    private String mApiKey;
    private String mAuthToken;
    private String mUserAgent;

    private boolean mCancel = false;

    public LargeFileHelper(URL urlUpload, String apikey, String authToken, String userAgent) {
        mUrlUpload = urlUpload;
        mHost = mUrlUpload.getHost();
        mApiKey = apikey;
        mAuthToken = authToken;
        mUserAgent = userAgent;
    }

    // Upload file out of band. This should not be called on the UI thread.
    public MsgServerCtrl upload(InputStream in, String filename, String mimetype, long size,
                                FileHelperProgress progress) throws IOException {
        mCancel = false;
        HttpURLConnection conn = null;
        MsgServerCtrl ctrl;
        try {
            conn = (HttpURLConnection) mUrlUpload.openConnection();
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("User-Agent", mUserAgent);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            conn.setRequestProperty("X-Tinode-APIKey", mApiKey);
            conn.setRequestProperty("Authorization", "Token " + mAuthToken);
            conn.setChunkedStreamingMode(0);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(conn.getOutputStream()));
            out.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);
            // Content-Disposition: form-data; name="file"; filename="1519014549699.pdf"
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + LINE_END);
            // Content-Type: application/pdf
            out.writeBytes("Content-Type: " + mimetype + LINE_END);
            out.writeBytes("Content-Transfer-Encoding: binary" + LINE_END);
            out.writeBytes(LINE_END);

            copyStream(in, out, size, progress);

            out.writeBytes(LINE_END);
            out.writeBytes(TWO_HYPHENS + BOUNDARY + TWO_HYPHENS + LINE_END);
            out.flush();
            out.close();

            if (conn.getResponseCode() != 200) {
                throw new IOException("Failed to upload: " + conn.getResponseMessage() +
                        " (" + conn.getResponseCode() + ")");
            }

            InputStream resp = new BufferedInputStream(conn.getInputStream());
            ctrl = readServerResponse(resp);
            resp.close();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return ctrl;
    }

    // Uploads the file using Runnable, returns PromisedReply. Safe to call on UI thread.
    public PromisedReply<MsgServerCtrl> uploadFuture(final InputStream in,
                                                     final String filename,
                                                     final String mimetype,
                                                     final long size,
                                                     final FileHelperProgress progress) {
        final PromisedReply<MsgServerCtrl> result = new PromisedReply<>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.resolve(upload(in, filename, mimetype, size, progress));
                } catch (Exception ex) {
                    try {
                        result.reject(ex);
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
        return result;
    }

    // Download file from the given URL if the URL's host is the default host. Should not be called on the UI thread.
    public long download(String downloadFrom, OutputStream out, FileHelperProgress progress) throws IOException {
        URL url = new URL(downloadFrom);
        long size = 0;
        if (!url.getHost().equals(mHost)) {
            // As a security measure refuse to download from an absolute URL.
            return size;
        }

        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("X-Tinode-APIKey", mApiKey);
            urlConnection.setRequestProperty("Authorization", "Token " + mAuthToken);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.resolve(download(downloadFrom, out, progress));
                } catch (Exception ex) {
                    try {
                        result.reject(ex);
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
        return result;
    }

    // Try to cancel an ongoing upload or download.
    public void cancel() {
        mCancel = true;
    }

    private int copyStream(InputStream in, OutputStream out, long size, FileHelperProgress p) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int len, sent = 0;
        while ((len = in.read(buffer)) != -1) {
            sent += len;
            out.write(buffer, 0, len);
            if (p != null) {
                p.onProgress(sent, size);
            }

            if (mCancel) {
                mCancel = false;
                throw new IOException("cancelled");
            }
        }
        return sent;
    }

    private MsgServerCtrl readServerResponse(InputStream in) throws IOException {
        MsgServerCtrl ctrl = null;
        ObjectMapper mapper = Tinode.getJsonMapper();
        JsonParser parser = mapper.getFactory().createParser(in);
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException(parser, "Packet must start with an object",
                    parser.getCurrentLocation());
        }
        if (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            parser.nextToken();
            JsonNode node = mapper.readTree(parser);
            if (name.equals("ctrl")) {
                ctrl = mapper.readValue(node.traverse(), MsgServerCtrl.class);
            } else {
                throw new JsonParseException(parser, "Unexpected message '" + name + "'",
                        parser.getCurrentLocation());
            }
        }
        return ctrl;
    }

    public interface FileHelperProgress {
        void onProgress(long sent, long size);
    }

    public Map<String,String> headers() {
        Map<String,String> headers = new HashMap<>();
        headers.put("X-Tinode-APIKey", mApiKey);
        headers.put("Authorization", "Token " + mAuthToken);
        return headers;
    }
}
