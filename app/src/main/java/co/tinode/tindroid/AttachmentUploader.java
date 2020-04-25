package co.tinode.tindroid;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

public class AttachmentUploader extends Worker {
    private static final String TAG = "AttachmentUploader";

    // Maximum size of file to send in-band. 256KB.
    private static final long MAX_INBAND_ATTACHMENT_SIZE = 1 << 17;
    // Maximum size of file to upload. 8MB.
    private static final long MAX_ATTACHMENT_SIZE = 1 << 23;

    private final static String ARG_OPERATION = "operation";
    final static String ARG_TOPIC_NAME = "topic";
    final static String ARG_SRC_URI = "uri";
    final static String ARG_FILE_PATH = "filePath";
    final static String ARG_MSG_ID = "msgId";
    final static String ARG_IMAGE_CAPTION = "caption";
    final static String ARG_PROGRESS = "progress";
    final static String ARG_FILE_SIZE = "fileSize";
    final static String ARG_ERROR = "error";

    private LargeFileHelper mUploader = null;

    final static String TAG_UPLOAD_WORK = "AttachmentUploader";

    public AttachmentUploader(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public ListenableWorker.Result doWork() {
        return doUpload(getApplicationContext(), getInputData());
    }

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
        final Uri uri = Uri.parse(args.getString(ARG_SRC_URI));
        // filePath is optional
        final String filePath = args.getString(ARG_FILE_PATH);
        final long msgId = args.getLong(ARG_MSG_ID, 0);

        final Data.Builder result = new Data.Builder()
                .putString(ARG_TOPIC_NAME, topicName)
                .putLong(ARG_MSG_ID, msgId);

        final Topic topic = Cache.getTinode().getTopic(topicName);

        Drafty content = null;
        boolean success = false;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            int imageWidth = 0, imageHeight = 0;

            FileDetails fileDetails = getFileDetails(context, uri, filePath);
            String fname = fileDetails.fileName;

            if (fileDetails.fileSize == 0) {
                Log.w(TAG, "File size is zero; uri=" + uri + "; file="+filePath);
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
                Bitmap bmp = null;

                // Make sure the image is not too large.
                if (fileDetails.fileSize > MAX_INBAND_ATTACHMENT_SIZE) {
                    // Resize image to ensure it's under the maximum in-band size.
                    is = resolver.openInputStream(uri);
                    bmp = BitmapFactory.decodeStream(is, null, null);
                    //noinspection ConstantConditions
                    is.close();

                    // noinspection ConstantConditions: NullPointerException is handled explicitly.
                    bmp = UiUtils.scaleBitmap(bmp);
                }

                // Also ensure the image has correct orientation.
                int orientation = ExifInterface.ORIENTATION_UNDEFINED;
                try {
                    // Opening original image, not a scaled copy.
                    if (fileDetails.imageOrientation == -1) {
                        ExifInterface exif = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)  {
                            is = resolver.openInputStream(uri);
                            //noinspection ConstantConditions
                            exif = new ExifInterface(is);
                        } else {
                            if (fileDetails.filePath != null) {
                                exif = new ExifInterface(fileDetails.filePath);
                            }
                        }
                        if (exif != null) {
                            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);
                        }
                        if (is != null) {
                            is.close();
                        }
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
                            if (bmp == null) {
                                is = resolver.openInputStream(uri);
                                bmp = BitmapFactory.decodeStream(is, null, null);
                                //noinspection ConstantConditions
                                is.close();
                            }

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

                if (bmp != null) {
                    imageWidth = bmp.getWidth();
                    imageHeight = bmp.getHeight();

                    is = UiUtils.bitmapToStream(bmp, fileDetails.mimeType);
                    fileDetails.fileSize = is.available();
                }
            }

            if (fileDetails.fileSize > MAX_ATTACHMENT_SIZE) {
                Log.w(TAG, "Unable to process attachment: too big, size=" + fileDetails.fileSize);
                return ListenableWorker.Result.failure(
                        result.putString(ARG_ERROR,
                                context.getString(
                                        R.string.attachment_too_large,
                                        UiUtils.bytesToHumanSize(fileDetails.fileSize),
                                        UiUtils.bytesToHumanSize(MAX_ATTACHMENT_SIZE)))
                                .build());
            } else {
                if (is == null) {
                    is = resolver.openInputStream(uri);
                }

                if ("file".equals(operation) && fileDetails.fileSize > MAX_INBAND_ATTACHMENT_SIZE) {

                    // Update draft with file data.
                    store.msgDraftUpdate(topic, msgId, draftyAttachment(fileDetails.mimeType, fname, uri.toString(), -1));

                    setProgressAsync(new Data.Builder()
                            .putAll(result.build())
                            .putLong(ARG_PROGRESS, 0)
                            .putLong(ARG_FILE_SIZE, fileDetails.fileSize).build());

                    // Upload then send message with a link. This is a long-running blocking call.
                    mUploader = Cache.getTinode().getFileUploader();
                    MsgServerCtrl ctrl = mUploader.upload(is, fname, fileDetails.mimeType, fileDetails.fileSize,
                            new LargeFileHelper.FileHelperProgress() {
                                @Override
                                public void onProgress(long progress, long size) {
                                    setProgressAsync(new Data.Builder()
                                            .putAll(result.build())
                                            .putLong(ARG_PROGRESS, progress)
                                            .putLong(ARG_FILE_SIZE, size)
                                            .build());
                                }
                            });
                    success = (ctrl != null && ctrl.code == 200);
                    if (success) {
                        content = draftyAttachment(fileDetails.mimeType, fname,
                                ctrl.getStringParam("url", null), fileDetails.fileSize);
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
                            BitmapFactory.decodeStream(bais, null, options);
                            bais.close();

                            imageWidth = options.outWidth;
                            imageHeight = options.outHeight;
                        }
                        store.msgDraftUpdate(topic, msgId,
                                draftyImage(args.getString(ARG_IMAGE_CAPTION),
                                        fileDetails.mimeType, bits, imageWidth, imageHeight, fname));
                    }
                    success = true;
                    setProgressAsync(new Data.Builder()
                            .putAll(result.build())
                            .putLong(ARG_PROGRESS, 0)
                            .putLong(ARG_FILE_SIZE, fileDetails.fileSize)
                            .build());
                }
            }
        } catch (IOException | NullPointerException ex) {
            result.putString(ARG_ERROR, ex.getMessage());
            if (!"cancelled".equals(ex.getMessage())) {
                Log.w(TAG, "Failed to attach file", ex);
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {}
            }
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException ignored) {}
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
            }
        }

        result.fileName = fname;
        result.fileSize = fsize;

        return result;
    }

    @SuppressWarnings("UnusedReturnValue")
    static void enqueueWorkRequest(AppCompatActivity activity, String operation, Bundle args) {
        String topicName = args.getString(AttachmentUploader.ARG_TOPIC_NAME);
        // Create a new message which will be updated with upload progress.
        Drafty msg = new Drafty();
        long msgId = BaseDb.getInstance().getStore()
                .msgDraft(Cache.getTinode().getTopic(topicName), msg, Tinode.draftyHeadersFor(msg));
        if (msgId > 0) {
            Uri uri = args.getParcelable(AttachmentUploader.ARG_SRC_URI);
            assert uri != null;

            Data.Builder data = new Data.Builder()
                    .putString(ARG_OPERATION, operation)
                    .putString(ARG_SRC_URI, uri.toString())
                    .putLong(ARG_MSG_ID, msgId)
                    .putString(ARG_TOPIC_NAME, topicName)
                    .putString(ARG_IMAGE_CAPTION, args.getString(ARG_IMAGE_CAPTION))
                    .putString(ARG_FILE_PATH, args.getString(ARG_FILE_PATH));
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            OneTimeWorkRequest upload = new OneTimeWorkRequest.Builder(AttachmentUploader.class)
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

    // Wrap content into Drafty.

    // Send image in-band
    private static Drafty draftyImage(String caption, String mimeType, byte[] bits, int width, int height, String fname) {
        Drafty content = Drafty.fromPlainText(" ");
        content.insertImage(0, mimeType, bits, width, height, fname);
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
}