package co.tinode.tindroid;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

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
import java.util.HashMap;
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
import androidx.work.Operation;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.LargeFileHelper;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.TheCard;

public class AttachmentHandler extends Worker {
    final static String ARG_OPERATION = "operation";
    final static String ARG_OPERATION_IMAGE = "image";
    final static String ARG_OPERATION_FILE = "file";
    final static String ARG_OPERATION_AUDIO = "audio";
    final static String ARG_OPERATION_VIDEO = "video";

    // Bundle argument names.
    final static String ARG_TOPIC_NAME = "topic";
    final static String ARG_LOCAL_URI = "local_uri";
    final static String ARG_REMOTE_URI = "remote_uri";
    final static String ARG_SRC_BYTES = "bytes";
    final static String ARG_SRC_BITMAP = "bitmap";

    final static String ARG_FILE_PATH = "filePath";
    final static String ARG_FILE_NAME = "fileName";
    final static String ARG_MSG_ID = "msgId";
    final static String ARG_IMAGE_CAPTION = "caption";
    final static String ARG_PROGRESS = "progress";
    final static String ARG_FILE_SIZE = "fileSize";
    final static String ARG_ERROR = "error";
    final static String ARG_FATAL = "fatal";
    final static String ARG_MIME_TYPE = "mime";
    final static String ARG_AVATAR = "square_img";
    final static String ARG_IMAGE_WIDTH = "width";
    final static String ARG_IMAGE_HEIGHT = "height";
    final static String ARG_DURATION = "duration";
    final static String ARG_PREVIEW = "preview";

    final static String TAG_UPLOAD_WORK = "AttachmentUploader";

    private static final String TAG = "AttachmentHandler";
    private LargeFileHelper mUploader = null;

    public AttachmentHandler(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    static UploadDetails getFileDetails(@NonNull final Context context, @NonNull Uri uri, @Nullable String filePath) {
        final ContentResolver resolver = context.getContentResolver();
        String fname = null;
        long fsize = 0L;
        int orientation = -1;

        UploadDetails result = new UploadDetails();
        result.imageWidth = 0;
        result.imageHeight = 0;

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
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    fname = cursor.getString(idx);
                }
                idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) {
                    fsize = cursor.getLong(idx);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    idx = cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION);
                    if (idx >= 0) {
                        orientation = cursor.getInt(idx);
                    }
                }
            }
        } catch (Exception ignored) {
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

    static Operation enqueueMsgAttachmentUploadRequest(AppCompatActivity activity, String operation, Bundle args) {
        String topicName = args.getString(AttachmentHandler.ARG_TOPIC_NAME);
        // Create a new message which will be updated with upload progress.
        Drafty content = new Drafty();
        HashMap<String, Object> head = new HashMap<>();
        head.put("mime", Drafty.MIME_TYPE);
        Storage.Message msg = BaseDb.getInstance().getStore()
                .msgDraft(Cache.getTinode().getTopic(topicName), content, head);
        if (msg != null) {
            Uri uri = args.getParcelable(AttachmentHandler.ARG_LOCAL_URI);
            assert uri != null;

            Data.Builder data = new Data.Builder()
                    .putString(ARG_OPERATION, operation)
                    .putString(ARG_LOCAL_URI, uri.toString())
                    .putLong(ARG_MSG_ID, msg.getDbId())
                    .putString(ARG_TOPIC_NAME, topicName)
                    .putInt(ARG_DURATION, args.getInt(ARG_DURATION))
                    .putString(ARG_FILE_NAME, args.getString(ARG_FILE_NAME))
                    .putLong(ARG_FILE_SIZE, args.getLong(ARG_FILE_SIZE))
                    .putString(ARG_MIME_TYPE, args.getString(ARG_MIME_TYPE))
                    .putString(ARG_IMAGE_CAPTION, args.getString(ARG_IMAGE_CAPTION))
                    .putString(ARG_FILE_PATH, args.getString(ARG_FILE_PATH));
            if (ARG_OPERATION_AUDIO.equals(operation)) {
                byte[] preview = args.getByteArray(ARG_PREVIEW);
                if (preview != null) {
                    data.putByteArray(ARG_PREVIEW, preview);
                }
            }
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            OneTimeWorkRequest upload = new OneTimeWorkRequest.Builder(AttachmentHandler.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG_UPLOAD_WORK)
                    .build();

            return WorkManager.getInstance(activity).enqueueUniqueWork(Long.toString(msg.getDbId()),
                    ExistingWorkPolicy.REPLACE, upload);
        }

        return null;
    }

    @SuppressWarnings("UnusedReturnValue")
    static long enqueueDownloadAttachment(AppCompatActivity activity, Map<String, Object> data,
                                          String fname, String mimeType) {
        long downloadId = -1;
        Object ref = data.get("ref");
        if (ref instanceof String) {
            try {
                URL url = new URL(Cache.getTinode().getBaseUrl(), (String) ref);
                String scheme = url.getProtocol();
                // Make sure the file is downloaded over http or https protocols.
                if (scheme.equals("http") || scheme.equals("https")) {
                    LargeFileHelper lfh = Cache.getTinode().getLargeFileHelper();
                    downloadId = remoteDownload(activity, Uri.parse(url.toString()), fname, mimeType, lfh.headers());
                } else {
                    Log.w(TAG, "Unsupported transport protocol '" + scheme + "'");
                    Toast.makeText(activity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
                }
            } catch (MalformedURLException ex) {
                Log.w(TAG, "Server address is not yet configured", ex);
                Toast.makeText(activity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
            }
        } else {
            Object val = data.get("val");
            byte[] bits = val instanceof String ? Base64.decode((String) val, Base64.DEFAULT) :
                    val instanceof byte[] ? (byte[]) val : null;
            if (bits == null) {
                Log.w(TAG, "Invalid or missing attachment");
                Toast.makeText(activity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
                return downloadId;
            }

            // Create file in a downloads directory by default.
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            // Make sure Downloads folder exists.
            path.mkdirs();

            File file = new File(path, fname);

            if (TextUtils.isEmpty(mimeType)) {
                mimeType = UiUtils.getMimeType(Uri.fromFile(file));
                if (mimeType == null) {
                    mimeType = "*/*";
                }
            }

            Uri result;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    // Save file to local storage.
                    fos.write(bits);
                    result = FileProvider.getUriForFile(activity, "co.tinode.tindroid.provider", file);
                } catch (IOException ex) {
                    Log.w(TAG, "Failed to save attachment to storage", ex);
                    Toast.makeText(activity, R.string.failed_to_save_download, Toast.LENGTH_SHORT).show();
                    return downloadId;
                }
            } else {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fname);
                cv.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                ContentResolver resolver = activity.getContentResolver();
                Uri dst = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                result = resolver.insert(dst, cv);
                if (result != null) {
                    try {
                        new ParcelFileDescriptor.
                                AutoCloseOutputStream(resolver.openFileDescriptor(result, "w")).write(bits);
                    } catch (IOException ex) {
                        Log.w(TAG, "Failed to save attachment to media storage", ex);
                        Toast.makeText(activity, R.string.failed_to_save_download, Toast.LENGTH_SHORT).show();
                        return downloadId;
                    }
                    cv.clear();
                    cv.put(MediaStore.Downloads.IS_PENDING, 0);
                    resolver.update(result, cv, null, null);
                }
            }

            // Make the downloaded file is visible.
            MediaScannerConnection.scanFile(activity,
                    new String[]{file.toString()}, null, null);

            // Open downloaded file.
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(result, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                Log.w(TAG, "No application can handle downloaded file", ex);
                Toast.makeText(activity, R.string.failed_to_open_file, Toast.LENGTH_SHORT).show();
                activity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
            }
        }
        return downloadId;
    }

    private static long remoteDownload(AppCompatActivity activity, final Uri uri, final String fname, final String mime,
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

    private static @Nullable URI wrapRefUrl(@Nullable String refUrl) {
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
        return ref;
    }

    // Send audio recording.
    private static Drafty draftyAudio(String mimeType, byte[] preview, byte[] bits, String refUrl,
                                      int duration, String fname, long size) {
        return new Drafty().insertAudio(0, mimeType, bits, preview, duration, fname, wrapRefUrl(refUrl), size);
    }

    // Send image.
    private static Drafty draftyImage(String caption, String mimeType, byte[] bits, String refUrl,
                                      int width, int height, String fname, long size) {
        Drafty content = new Drafty();
        content.insertImage(0, mimeType, bits, width, height, fname, wrapRefUrl(refUrl), size);
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
        return uploadMessageAttachment(getApplicationContext(), getInputData());
    }

    @Override
    public void onStopped() {
        super.onStopped();
        if (mUploader != null) {
            mUploader.cancel();
        }
    }

    private ListenableWorker.Result uploadMessageAttachment(final Context context, final Data args) {
        Storage store = BaseDb.getInstance().getStore();

        // File upload "file", "image", "audio".
        final String operation = args.getString(ARG_OPERATION);
        final String topicName = args.getString(ARG_TOPIC_NAME);
        // URI must exist.
        final Uri uri = Uri.parse(args.getString(ARG_LOCAL_URI));
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
        Bitmap bmp = null;
        try {
            UploadDetails uploadDetails = getFileDetails(context, uri, filePath);
            String fname = uploadDetails.fileName;

            if (uploadDetails.fileSize == 0) {
                Log.w(TAG, "File size is zero; uri=" + uri + "; file=" + filePath);
                return ListenableWorker.Result.failure(
                        result.putBoolean(ARG_FATAL, true)
                                .putString(ARG_ERROR, context.getString(R.string.unable_to_attach_file)).build());
            }

            if (fname == null) {
                fname = context.getString(R.string.default_attachment_name);
            }

            final ContentResolver resolver = context.getContentResolver();

            // Image is being attached. Ensure the image has correct orientation and size.
            if (ARG_OPERATION_IMAGE.equals(operation)) {
                // Make sure the image is not too large in byte-size and in linear dimensions.
                bmp = prepareImage(resolver, uri, uploadDetails);
                is = UiUtils.bitmapToStream(bmp, uploadDetails.mimeType);
                uploadDetails.fileSize = is.available();
            }

            if (uploadDetails.fileSize > maxFileUploadSize) {
                if (is != null) {
                    is.close();
                }
                // File is too big to be send in-band or out of band.
                Log.w(TAG, "Unable to process attachment: too big, size=" + uploadDetails.fileSize);
                return ListenableWorker.Result.failure(
                        result.putString(ARG_ERROR,
                                context.getString(
                                        R.string.attachment_too_large,
                                        UiUtils.bytesToHumanSize(uploadDetails.fileSize),
                                        UiUtils.bytesToHumanSize(maxFileUploadSize)))
                                .putBoolean(ARG_FATAL, true)
                                .build());
            } else {
                if (is == null) {
                    is = resolver.openInputStream(uri);
                }
                if (is == null) {
                    throw new IOException("Failed to open file at " + uri);
                }

                if (uploadDetails.fileSize > maxInbandAttachmentSize) {
                    byte[] previewBits = null;
                    // Update draft with file or image data.
                    String ref = "mid:uploading-" + msgId;
                    Drafty msgDraft = null;
                    if (ARG_OPERATION_FILE.equals(operation)) {
                        msgDraft = draftyAttachment(uploadDetails.mimeType, fname, ref, uploadDetails.fileSize);
                    } else if (ARG_OPERATION_IMAGE.equals(operation)) {
                        // Create a tiny preview bitmap.
                        if (bmp.getWidth() > UiUtils.IMAGE_PREVIEW_DIM || bmp.getHeight() > UiUtils.IMAGE_PREVIEW_DIM) {
                            previewBits = UiUtils.bitmapToBytes(UiUtils.scaleBitmap(bmp,
                                    UiUtils.IMAGE_PREVIEW_DIM, UiUtils.IMAGE_PREVIEW_DIM, false),
                                    "image/jpeg");
                        }
                        msgDraft = draftyImage(args.getString(ARG_IMAGE_CAPTION), uploadDetails.mimeType, previewBits,
                                ref, uploadDetails.imageWidth, uploadDetails.imageHeight, fname, uploadDetails.fileSize);
                    } else if (ARG_OPERATION_AUDIO.equals(operation)) {
                        uploadDetails.duration = args.getInt(ARG_DURATION, 0);
                        uploadDetails.mimeType = uploadDetails.mimeType == null ?
                                args.getString(ARG_MIME_TYPE) : "audio/aac";
                        msgDraft = draftyAudio(uploadDetails.mimeType, args.getByteArray(ARG_PREVIEW), null,
                                ref, uploadDetails.duration, fname, uploadDetails.fileSize);
                    }

                    if (msgDraft != null) {
                        store.msgDraftUpdate(topic, msgId, msgDraft);
                    } else {
                        throw new IllegalArgumentException("Unknown operation " + operation);
                    }

                    setProgressAsync(new Data.Builder()
                            .putAll(result.build())
                            .putLong(ARG_PROGRESS, 0)
                            .putLong(ARG_FILE_SIZE, uploadDetails.fileSize).build());

                    // Upload then send message with a link. This is a long-running blocking call.
                    mUploader = Cache.getTinode().getLargeFileHelper();
                    ServerMessage msg = mUploader.upload(is, fname, uploadDetails.mimeType, uploadDetails.fileSize,
                            topicName, (progress, size) -> setProgressAsync(new Data.Builder()
                                    .putAll(result.build())
                                    .putLong(ARG_PROGRESS, progress)
                                    .putLong(ARG_FILE_SIZE, size)
                                    .build()));
                    if (mUploader.isCanceled()) {
                        throw new CancellationException();
                    }
                    success = msg != null && msg.ctrl != null && msg.ctrl.code == 200;
                    if (success) {
                        String url = msg.ctrl.getStringParam("url", null);
                        result.putString(ARG_REMOTE_URI, url);
                        switch (operation) {
                            case ARG_OPERATION_FILE:
                                content = draftyAttachment(uploadDetails.mimeType, fname, url, uploadDetails.fileSize);
                                break;
                            case ARG_OPERATION_IMAGE:
                                content = draftyImage(args.getString(ARG_IMAGE_CAPTION), uploadDetails.mimeType,
                                        previewBits, url, uploadDetails.imageWidth, uploadDetails.imageHeight,
                                        fname, uploadDetails.fileSize);
                                break;
                            case ARG_OPERATION_AUDIO:
                                content = draftyAudio(uploadDetails.mimeType, args.getByteArray(ARG_PREVIEW),
                                        null, url, uploadDetails.duration, fname, uploadDetails.fileSize);
                                break;
                        }
                    }
                } else {
                    baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    byte[] bits = baos.toByteArray();
                    Drafty msgDraft = null;
                    if (ARG_OPERATION_FILE.equals(operation)) {
                        msgDraft = draftyFile(uploadDetails.mimeType, bits, fname);
                    } else if (ARG_OPERATION_IMAGE.equals(operation)) {
                        if (uploadDetails.imageWidth == 0) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            InputStream bais = new ByteArrayInputStream(bits);
                            len = bais.available();
                            BitmapFactory.decodeStream(bais, null, options);
                            bais.close();

                            uploadDetails.imageWidth = options.outWidth;
                            uploadDetails.imageHeight = options.outHeight;
                        }
                        msgDraft = draftyImage(args.getString(ARG_IMAGE_CAPTION), uploadDetails.mimeType, bits,
                                null, uploadDetails.imageWidth, uploadDetails.imageHeight, fname, len);
                    } else if (ARG_OPERATION_AUDIO.equals(operation)) {
                        uploadDetails.duration = args.getInt(ARG_DURATION, 0);
                        uploadDetails.mimeType = uploadDetails.mimeType == null ? args.getString(ARG_MIME_TYPE) : "audio/aac";
                        msgDraft = draftyAudio(uploadDetails.mimeType, args.getByteArray(ARG_PREVIEW),
                                bits, null, uploadDetails.duration, fname, bits.length);
                    }

                    if (msgDraft != null) {
                        store.msgDraftUpdate(topic, msgId, msgDraft);
                    } else {
                        throw new IllegalArgumentException("Unknown operation " + operation);
                    }

                    success = true;
                    setProgressAsync(new Data.Builder()
                            .putAll(result.build())
                            .putLong(ARG_PROGRESS, 0)
                            .putLong(ARG_FILE_SIZE, uploadDetails.fileSize)
                            .build());
                }
            }
        } catch (CancellationException ignored) {
        } catch (IOException | SecurityException ex) {
            result.putString(ARG_ERROR, ex.getMessage());
            Log.w(TAG, "Failed to upload file", ex);
        } finally {
            if (bmp != null) {
                bmp.recycle();
            }
            if (ARG_OPERATION_AUDIO.equals(operation) && filePath != null) {
                new File(filePath).delete();
            }
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
            // Failure. Draft has been discarded earlier. We cannot discard it here because
            // copyStream cannot be interrupted.
            return ListenableWorker.Result.failure(result.build());
        }
    }

    /**
     * Scale the avatar to appropriate size and upload it to the server of necessary.
     * @param pub VxCard to save avatar to.
     * @param bmp new avatar; no action is taken if avatar is null.
     * @return result of the operation.
     */
    static PromisedReply<ServerMessage> uploadAvatar(@NotNull final VxCard pub, @Nullable Bitmap bmp,
                                                     @Nullable String topicName) {
        if (bmp == null) {
            // No action needed.
            return new PromisedReply<>((ServerMessage) null);
        }

        final String mimeType= "image/png";

        int width = bmp.getWidth();
        int height = bmp.getHeight();
        if (width < UiUtils.MIN_AVATAR_SIZE || height < UiUtils.MIN_AVATAR_SIZE) {
            // FAIL.
            return new PromisedReply<>(new Exception("Image is too small"));
        }

        if (width != height || width > UiUtils.MAX_AVATAR_SIZE) {
            bmp = UiUtils.scaleSquareBitmap(bmp, UiUtils.MAX_AVATAR_SIZE);
            width = bmp.getWidth();
            height = bmp.getHeight();
        }

        if (pub.photo == null) {
            pub.photo = new TheCard.Photo();
        }
        pub.photo.width = width;
        pub.photo.height = height;

        PromisedReply<ServerMessage> result;
        try (InputStream is = UiUtils.bitmapToStream(bmp, mimeType)) {
            long fileSize = is.available();
            if (fileSize > UiUtils.MAX_INBAND_AVATAR_SIZE) {
                // Sending avatar out of band.

                // Generate small avatar preview.
                pub.photo.data = UiUtils.bitmapToBytes(UiUtils.scaleSquareBitmap(bmp, UiUtils.AVATAR_THUMBNAIL_DIM), mimeType);
                // Upload then return result with a link. This is a long-running blocking call.
                LargeFileHelper uploader = Cache.getTinode().getLargeFileHelper();
                result = uploader.uploadFuture(is, System.currentTimeMillis() + ".png", mimeType, fileSize,
                                topicName, null).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                                if (msg != null && msg.ctrl != null && msg.ctrl.code == 200) {
                                    pub.photo.ref = msg.ctrl.getStringParam("url", null);
                                }
                                return null;
                            }
                        });
            } else {
                // Can send a small avatar in-band.
                pub.photo.data = UiUtils.bitmapToBytes(UiUtils.scaleSquareBitmap(bmp, UiUtils.AVATAR_THUMBNAIL_DIM), mimeType);
                result = new PromisedReply<>((ServerMessage) null);
            }
        } catch (IOException | IllegalArgumentException ex) {
            Log.w(TAG, "Failed to upload avatar", ex);
            result = new PromisedReply<>(ex);
        }

        return result;
    }

    // Make sure the image is not too large in byte-size and in linear dimensions, has correct orientation.
    private static Bitmap prepareImage(ContentResolver r, Uri src, UploadDetails uploadDetails) throws IOException {
        InputStream is = r.openInputStream(src);
        if (is == null) {
            throw new IOException("Decoding bitmap: source not available");
        }
        Bitmap bmp = BitmapFactory.decodeStream(is, null, null);
        is.close();

        if (bmp == null) {
            throw new IOException("Failed to decode bitmap");
        }

        // Make sure the image dimensions are not too large.
        if (bmp.getWidth() > UiUtils.MAX_BITMAP_SIZE || bmp.getHeight() > UiUtils.MAX_BITMAP_SIZE) {
            bmp = UiUtils.scaleBitmap(bmp, UiUtils.MAX_BITMAP_SIZE, UiUtils.MAX_BITMAP_SIZE, false);

            byte[] bits = UiUtils.bitmapToBytes(bmp, uploadDetails.mimeType);
            uploadDetails.fileSize = bits.length;
        }

        // Also ensure the image has correct orientation.
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;
        try {
            // Opening original image, not a scaled copy.
            if (uploadDetails.imageOrientation == -1) {
                is = r.openInputStream(src);
                if (is != null) {
                    ExifInterface exif = new ExifInterface(is);
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_UNDEFINED);
                    is.close();
                }
            } else {
                switch (uploadDetails.imageOrientation) {
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

        uploadDetails.imageWidth = bmp.getWidth();
        uploadDetails.imageHeight = bmp.getHeight();

        return bmp;
    }

    static class UploadDetails {
        String mimeType;
        int imageOrientation;
        String filePath;
        String fileName;
        long fileSize;
        int imageWidth;
        int imageHeight;
        int duration;
    }
}