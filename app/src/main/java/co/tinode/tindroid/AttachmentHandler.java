package co.tinode.tindroid;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CancellationException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.exifinterface.media.ExifInterface;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.LargeFileHelper;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerCtrl;

public class AttachmentHandler extends Worker {
    final static String ARG_OPERATION = "operation";
    final static String ARG_OPERATION_IMAGE = "image";
    final static String ARG_OPERATION_FILE = "file";
    // Bundle argument names.
    final static String ARG_TOPIC_NAME = "topic";
    final static String ARG_SRC_LOCAL_URI = "local_uri";
    final static String ARG_SRC_REMOTE_URI = "remote_url";
    final static String ARG_SRC_BYTES = "bytes";

    final static String ARG_FILE_PATH = "filePath";
    final static String ARG_FILE_NAME = "fileName";
    final static String ARG_MSG_ID = "msgId";
    final static String ARG_IMAGE_CAPTION = "caption";
    final static String ARG_PROGRESS = "progress";
    final static String ARG_FILE_SIZE = "fileSize";
    final static String ARG_ERROR = "error";
    final static String ARG_MIME_TYPE = "mime";
    final static String ARG_AVATAR = "square_img";
    final static String ARG_IMAGE_WIDTH = "width";
    final static String ARG_IMAGE_HEIGHT = "height";

    final static String TAG_UPLOAD_WORK = "AttachmentUploader";

    private static final String TAG = "AttachmentHandler";
    private LargeFileHelper mUploader = null;

    public AttachmentHandler(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    static FileDetails getFileDetails(@NonNull final Context context, @NonNull Uri uri, @Nullable String filePath) {
        final ContentResolver resolver = context.getContentResolver();
        String fname = null;
        long fsize = 0L;
        int orientation = -1;

        FileDetails result = new FileDetails();

        String mimeType = resolver.getType(uri);
        if (mimeType == null) {
            mimeType = UiUtils.getMimeType(uri);
        }
        result.mimeType = mimeType;

        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, MediaStore.MediaColumns.ORIENTATION};
        } else {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        try (Cursor cursor = resolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                fname = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                fsize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int idx = cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION);
                    if (idx >= 0) {
                        orientation = cursor.getInt(idx);
                    }
                }
            }
        }
        // In degrees.
        result.imageOrientation = orientation;

        // Still no size? Try opening directly.
        if (fsize <= 0 || orientation < 0) {
            String path = filePath != null ? filePath : UiUtils.getContentPath(context, uri);
            if (path != null) {
                result.filePath = path;

                File file = new File(path);
                if (fname == null) {
                    fname = file.getName();
                }
                fsize = file.length();
            } else {
                try {
                    DocumentFile df = DocumentFile.fromSingleUri(context, uri);
                    if (df != null) {
                        fname = df.getName();
                        fsize = df.length();
                    }
                } catch (SecurityException ignored) {
                }
            }
        }

        result.fileName = fname;
        result.fileSize = fsize;

        return result;
    }

    @SuppressWarnings("UnusedReturnValue")
    static void enqueueUploadRequest(AppCompatActivity activity, String operation, Bundle args) {
        String topicName = args.getString(AttachmentHandler.ARG_TOPIC_NAME);
        // Create a new message which will be updated with upload progress.
        Drafty msg = new Drafty();
        long msgId = BaseDb.getInstance().getStore()
                .msgDraft(Cache.getTinode().getTopic(topicName), msg, Tinode.draftyHeadersFor(msg));
        if (msgId > 0) {
            Uri uri = args.getParcelable(AttachmentHandler.ARG_SRC_LOCAL_URI);
            assert uri != null;

            Data.Builder data = new Data.Builder()
                    .putString(ARG_OPERATION, operation)
                    .putString(ARG_SRC_LOCAL_URI, uri.toString())
                    .putLong(ARG_MSG_ID, msgId)
                    .putString(ARG_TOPIC_NAME, topicName)
                    .putString(ARG_IMAGE_CAPTION, args.getString(ARG_IMAGE_CAPTION))
                    .putString(ARG_FILE_PATH, args.getString(ARG_FILE_PATH));
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            OneTimeWorkRequest upload = new OneTimeWorkRequest.Builder(AttachmentHandler.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG_UPLOAD_WORK)
                    .build();

            // If send or upload is retried,
            WorkManager.getInstance(activity).enqueueUniqueWork(Long.toString(msgId), ExistingWorkPolicy.REPLACE, upload);
        } else {
            Log.w(TAG, "Failed to insert new message to DB");
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    static long enqueueDownloadAttachment(AppCompatActivity activity, Map<String, Object> data,
                                          String fname, String mimeType) {
        long downloadId = -1;
        // Create file in a downloads directory by default.
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, fname);
        Uri fileUri = Uri.fromFile(file);

        if (TextUtils.isEmpty(mimeType)) {
            mimeType = UiUtils.getMimeType(fileUri);
            if (mimeType == null) {
                mimeType = "*/*";
            }
        }

        FileOutputStream fos = null;
        try {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                Log.w(TAG, "External storage not mounted: " + path);
            } else if (!(path.mkdirs() || path.isDirectory())) {
                Log.w(TAG, "Path is not a directory - " + path);
            }

            Object ref = data.get("ref");
            if (ref instanceof String) {
                URL url = new URL(Cache.getTinode().getBaseUrl(), (String) ref);
                String scheme = url.getProtocol();
                // Make sure the file is downloaded over http or https protocols.
                if (scheme.equals("http") || scheme.equals("https")) {
                    LargeFileHelper lfh = Cache.getTinode().getFileUploader();
                    downloadId = startDownload(activity, Uri.parse(url.toString()), fname, mimeType, lfh.headers());
                } else {
                    Log.w(TAG, "Unsupported transport protocol '" + scheme + "'");
                    Toast.makeText(activity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
                }
            } else {
                Object val = data.get("val");
                if (val != null) {
                    fos = new FileOutputStream(file);
                    fos.write(val instanceof String ?
                            Base64.decode((String) val, Base64.DEFAULT) :
                            (byte[]) val);

                    Intent intent = new Intent();
                    intent.setAction(android.content.Intent.ACTION_VIEW);
                    intent.setDataAndType(FileProvider.getUriForFile(activity,
                            "co.tinode.tindroid.provider", file), mimeType);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        activity.startActivity(intent);
                    } catch (ActivityNotFoundException ignored) {
                        activity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                    }
                } else {
                    Log.w(TAG, "Invalid or missing attachment");
                    Toast.makeText(activity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
                }
            }

        } catch (NullPointerException | ClassCastException | IOException ex) {
            Log.w(TAG, "Failed to save attachment to storage", ex);
            Toast.makeText(activity, R.string.failed_to_save_download, Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException ex) {
            Log.w(TAG, "No application can handle downloaded file");
            Toast.makeText(activity, R.string.failed_to_open_file, Toast.LENGTH_SHORT).show();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {
                }
            }
        }
        return downloadId;
    }

    private static long startDownload(AppCompatActivity activity, final Uri uri, final String fname, final String mime,
                                      final Map<String, String> headers) {

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            return -1;
        }

        // Ensure directory exists.
        Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .mkdirs();

        DownloadManager.Request req = new DownloadManager.Request(uri);
        // Always add Origin header to satisfy CORS. If server does not need CORS it won't hurt anyway.
        req.addRequestHeader("Origin", Cache.getTinode().getHttpOrigin());
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                req.addRequestHeader(entry.getKey(), entry.getValue());
            }
        }

        return dm.enqueue(
                req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI |
                        DownloadManager.Request.NETWORK_MOBILE)
                        .setMimeType(mime)
                        .setAllowedOverRoaming(false)
                        .setTitle(fname)
                        .setDescription(activity.getString(R.string.download_title))
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setVisibleInDownloadsUi(true)
                        .setDestinationUri(Uri.fromFile(new File(Environment
                                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fname))));
    }

    // Send image in-band
    private static Drafty draftyImage(String caption, String mimeType, byte[] bits, String refUrl,
                                      int width, int height, String fname, long size) {
        Drafty content = Drafty.fromPlainText(" ");
        URI ref = null;
        if (refUrl != null) {
            try {
                ref = new URI(refUrl);
                if (ref.isAbsolute()) {
                    ref = new URI(Cache.getTinode().getBaseUrl().toString()).relativize(ref);
                }
            } catch (URISyntaxException | MalformedURLException ignored) {
            }
        }
        content.insertImage(0, mimeType, bits, width, height, fname, ref, size);
        if (!TextUtils.isEmpty(caption)) {
            content.appendLineBreak()
                    .append(Drafty.fromPlainText(caption));
        }
        return content;
    }

    // Send file in-band
    private static Drafty draftyFile(String mimeType, byte[] bits, String fname) {
        Drafty content = new Drafty();
        content.attachFile(mimeType, bits, fname);
        return content;
    }

    // Send file as a link.
    private static Drafty draftyAttachment(String mimeType, String fname, String refUrl, long size) {
        Drafty content = new Drafty();
        content.attachFile(mimeType, fname, refUrl, size);
        return content;
    }

    @NonNull
    @Override
    public ListenableWorker.Result doWork() {
        return doUpload(getApplicationContext(), getInputData());
    }

    // Wrap content into Drafty.

    @Override
    public void onStopped() {
        super.onStopped();
        if (mUploader != null) {
            mUploader.cancel();
        }
    }

    private ListenableWorker.Result doUpload(final Context context, final Data args) {
        Storage store = BaseDb.getInstance().getStore();

        // File upload "file" or "image".
        final String operation = args.getString(ARG_OPERATION);
        final String topicName = args.getString(ARG_TOPIC_NAME);
        // URI must exist.
        final Uri uri = Uri.parse(args.getString(ARG_SRC_LOCAL_URI));
        // filePath is optional
        final String filePath = args.getString(ARG_FILE_PATH);
        final long msgId = args.getLong(ARG_MSG_ID, 0);

        final Data.Builder result = new Data.Builder()
                .putString(ARG_TOPIC_NAME, topicName)
                .putLong(ARG_MSG_ID, msgId);

        final Topic topic = Cache.getTinode().getTopic(topicName);

        // Maximum size of file to send in-band. The default is 256KB reduced by base64 expansion
        // factor 3/4 and minus overhead = 195584.
        final long maxInbandAttachmentSize = Cache.getTinode().getServerLimit(Tinode.MAX_MESSAGE_SIZE,
                (1L << 18)) * 3 / 4 - 1024;
        // Maximum size of file to upload. Default: 8MB.
        final long maxFileUploadSize = Cache.getTinode().getServerLimit(Tinode.MAX_FILE_UPLOAD_SIZE, 1L << 23);

        Drafty content = null;
        boolean success = false;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            int imageWidth = 0, imageHeight = 0;

            FileDetails fileDetails = getFileDetails(context, uri, filePath);
            String fname = fileDetails.fileName;

            if (fileDetails.fileSize == 0) {
                Log.w(TAG, "File size is zero; uri=" + uri + "; file=" + filePath);
                store.msgDiscard(topic, msgId);
                return ListenableWorker.Result.failure(
                        result.putString(ARG_ERROR, context.getString(R.string.unable_to_attach_file)).build());
            }

            if (fname == null) {
                fname = context.getString(R.string.default_attachment_name);
            }

            final ContentResolver resolver = context.getContentResolver();

            // Image is being attached. Ensure the image has correct orientation and size.
            if ("image".equals(operation)) {
                // Make sure the image is not too large in byte-size and in linear dimensions.
                is = resolver.openInputStream(uri);
                if (is == null) {
                    throw new IOException("Failed to open " + uri.toString());
                }
                Bitmap bmp = BitmapFactory.decodeStream(is, null, null);
                is.close();
                if (bmp == null) {
                    throw new IOException("Failed to decode bitmap");
                }

                // Make sure the image dimensions are not too large.
                if (bmp.getWidth() > UiUtils.MAX_BITMAP_SIZE || bmp.getHeight() > UiUtils.MAX_BITMAP_SIZE) {
                    bmp = UiUtils.scaleBitmap(bmp);

                    is = UiUtils.bitmapToStream(bmp, fileDetails.mimeType);
                    fileDetails.fileSize = is.available();
                    is.close();
                }

                // Also ensure the image has correct orientation.
                int orientation = ExifInterface.ORIENTATION_UNDEFINED;
                try {
                    // Opening original image, not a scaled copy.
                    if (fileDetails.imageOrientation == -1) {
                        is = resolver.openInputStream(uri);
                        //noinspection ConstantConditions
                        ExifInterface exif = new ExifInterface(is);
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_UNDEFINED);
                        is.close();
                    } else {
                        switch (fileDetails.imageOrientation) {
                            case 0:
                                orientation = ExifInterface.ORIENTATION_NORMAL;
                                break;
                            case 90:
                                orientation = ExifInterface.ORIENTATION_ROTATE_90;
                                break;
                            case 180:
                                orientation = ExifInterface.ORIENTATION_ROTATE_180;
                                break;
                            case 270:
                                orientation = ExifInterface.ORIENTATION_ROTATE_270;
                                break;
                            default:
                        }
                    }

                    switch (orientation) {
                        default:
                            // Rotate image to ensure correct orientation.
                            bmp = UiUtils.rotateBitmap(bmp, orientation);
                            break;
                        case ExifInterface.ORIENTATION_NORMAL:
                            break;
                        case ExifInterface.ORIENTATION_UNDEFINED:
                            Log.d(TAG, "Unable to obtain image orientation");
                    }
                } catch (IOException ex) {
                    Log.w(TAG, "Failed to obtain image orientation", ex);
                }

                imageWidth = bmp.getWidth();
                imageHeight = bmp.getHeight();

                is = UiUtils.bitmapToStream(bmp, fileDetails.mimeType);
                fileDetails.fileSize = is.available();
            }

            if (fileDetails.fileSize > maxFileUploadSize) {
                // File is too big to be send in-band or out of band.
                Log.w(TAG, "Unable to process attachment: too big, size=" + fileDetails.fileSize);
                return ListenableWorker.Result.failure(
                        result.putString(ARG_ERROR,
                                context.getString(
                                        R.string.attachment_too_large,
                                        UiUtils.bytesToHumanSize(fileDetails.fileSize),
                                        UiUtils.bytesToHumanSize(maxFileUploadSize)))
                                .build());
            } else {
                if (is == null) {
                    is = resolver.openInputStream(uri);
                }

                if (fileDetails.fileSize > maxInbandAttachmentSize) {
                    // Update draft with file or image data.
                    String ref = "mid:uploading-" + msgId;
                    if ("file".equals(operation)) {
                        store.msgDraftUpdate(topic, msgId, draftyAttachment(fileDetails.mimeType,
                                fname, ref, -1));
                    } else {
                        store.msgDraftUpdate(topic, msgId,
                                draftyImage(args.getString(ARG_IMAGE_CAPTION),
                                        fileDetails.mimeType, null, ref, imageWidth, imageHeight,
                                        fname, -1));
                    }

                    setProgressAsync(new Data.Builder()
                            .putAll(result.build())
                            .putLong(ARG_PROGRESS, 0)
                            .putLong(ARG_FILE_SIZE, fileDetails.fileSize).build());

                    // Upload then send message with a link. This is a long-running blocking call.
                    mUploader = Cache.getTinode().getFileUploader();
                    MsgServerCtrl ctrl = mUploader.upload(is, fname, fileDetails.mimeType, fileDetails.fileSize,
                            (progress, size) -> setProgressAsync(new Data.Builder()
                                    .putAll(result.build())
                                    .putLong(ARG_PROGRESS, progress)
                                    .putLong(ARG_FILE_SIZE, size)
                                    .build()));
                    if (mUploader.isCanceled()) {
                        throw new CancellationException();
                    }
                    success = ctrl != null && ctrl.code == 200;
                    if (success) {
                        if ("file".equals(operation)) {
                            content = draftyAttachment(fileDetails.mimeType, fname,
                                    ctrl.getStringParam("url", null), fileDetails.fileSize);
                        } else {
                            content = draftyImage(args.getString(ARG_IMAGE_CAPTION), fileDetails.mimeType,
                                    null, ctrl.getStringParam("url", null),
                                    imageWidth, imageHeight, fname, fileDetails.fileSize);
                        }
                    }
                } else {
                    baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16384];
                    int len;
                    // noinspection ConstantConditions: NullPointerException is handled explicitly.
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    byte[] bits = baos.toByteArray();
                    if ("file".equals(operation)) {
                        store.msgDraftUpdate(topic, msgId, draftyFile(fileDetails.mimeType, bits, fname));
                    } else {
                        if (imageWidth == 0) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            InputStream bais = new ByteArrayInputStream(bits);
                            len = bais.available();
                            BitmapFactory.decodeStream(bais, null, options);
                            bais.close();

                            imageWidth = options.outWidth;
                            imageHeight = options.outHeight;
                        }
                        store.msgDraftUpdate(topic, msgId,
                                draftyImage(args.getString(ARG_IMAGE_CAPTION),
                                        fileDetails.mimeType, bits, null, imageWidth, imageHeight, fname, len));
                    }
                    success = true;
                    setProgressAsync(new Data.Builder()
                            .putAll(result.build())
                            .putLong(ARG_PROGRESS, 0)
                            .putLong(ARG_FILE_SIZE, fileDetails.fileSize)
                            .build());
                }
            }
        } catch (CancellationException ignored) {
        } catch (IOException | NullPointerException | SecurityException ex) {
            result.putString(ARG_ERROR, ex.getMessage());
            Log.w(TAG, "Failed to upload file", ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException ignored) {
                }
            }
        }

        if (success) {
            // Success: mark message as ready for delivery. If content==null it won't be saved.
            store.msgReady(topic, msgId, content);
            return ListenableWorker.Result.success(result.build());
        } else {
            // Failure: discard draft.
            store.msgFailed(topic, msgId);
            return ListenableWorker.Result.failure(result.build());
        }
    }

    static class FileDetails {
        String mimeType;
        int imageOrientation;
        String filePath;
        String fileName;
        long fileSize;
    }
}