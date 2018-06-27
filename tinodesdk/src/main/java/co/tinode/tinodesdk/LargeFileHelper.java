package co.tinode.tinodesdk;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import co.tinode.tinodesdk.model.MsgServerCtrl;

public class LargeFileHelper {
    private URL mUrlUpload;
    private String mHost;
    private String mApiKey;
    private String mAuthToken;

    private boolean mCancel = false;

    public LargeFileHelper(URL urlUpload, String apikey, String authToken) {
        mUrlUpload = urlUpload;
        mHost = mUrlUpload.getHost();
        mApiKey = apikey;
        mAuthToken = authToken;
    }

    // Upload file out of band. This should not be called on the UI thread.
    public MsgServerCtrl upload(InputStream in, long size, FileHelperProgress progress) throws IOException {
        mCancel = false;
        HttpURLConnection urlConnection = null;
        MsgServerCtrl ctrl = null;
        try {
            urlConnection = (HttpURLConnection) mUrlUpload.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("X-Tinode-APIKey", mApiKey);
            urlConnection.setRequestProperty("Authorization", "Token " + mAuthToken);
            urlConnection.setChunkedStreamingMode(0);

            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            copyStream(in, out, size, progress);
            out.close();

            InputStream resp = new BufferedInputStream(urlConnection.getInputStream());
            ctrl = readServerResponse(resp);
            resp.close();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return ctrl;
    }

    // Uploads the file using Runnable, returns PromisedReply.
    public PromisedReply<MsgServerCtrl> uploadFuture(final InputStream in,
                                                     final long size,
                                                     final FileHelperProgress progress) {
        final PromisedReply<MsgServerCtrl> result = new PromisedReply<>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.resolve(upload(in, size, progress));
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

    // Downloads the file using Runnable, returns PromisedReply.
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

    //Try to cancel an ongoing upload or download.
    public void cancel() {
        mCancel = true;
    }

    private int copyStream(InputStream in, OutputStream out, long size, FileHelperProgress p) throws IOException {
        byte[] buffer = new byte[4096];
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
}
