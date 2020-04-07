package co.tinode.tindroid;

import android.app.Activity;
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
import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.loader.content.AsyncTaskLoader;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.LargeFileHelper;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerCtrl;

class AttachmentUploader {
    private static final String TAG = "AttachmentUploader";

    // Maximum size of file to send in-band. 256KB.
    private static final long MAX_INBAND_ATTACHMENT_SIZE = 1 << 17;
    // Maximum size of file to upload. 8MB.
    private static final long MAX_ATTACHMENT_SIZE = 1 << 23;

    static Bundle getFileDetails(final Context context, Uri uri, String filePath) {
        final ContentResolver resolver = context.getContentResolver();
        String fname = null;
        long fsize = 0L;
        int orientation = -1;

        Bundle result = new Bundle();

        String mimeType = resolver.getType(uri);
        if (mimeType == null) {
            mimeType = UiUtils.getMimeType(uri);
        }
        result.putString("mime", mimeType);

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
        result.putInt("orientation", orientation);

        // Still no size? Try opening directly.
        if (fsize <= 0 || orientation < 0) {
            String path = filePath != null ? filePath : UiUtils.getContentPath(context, uri);
            if (path != null) {
                result.putString("path", path);

                File file = new File(path);
                if (fname == null) {
                    fname = file.getName();
                }
                fsize = file.length();
            }
        }

        result.putString("name", fname);
        result.putLong("size", fsize);

        return result;
    }

    private static <T extends Progress> Result doUpload(final int loaderId, final Context context, final Bundle args,
                                                final WeakReference<T> callbackProgress) {

        final Result result = new Result();

        Storage store = BaseDb.getInstance().getStore();

        final String operation = args.getString("operation");
        final String topicName = args.getString("topic");
        // URI must exist, file is optional.
        final Uri uri = args.getParcelable("uri");
        final String file = args.getString("file");

        result.msgId = args.getLong("msgId");

        if (uri == null) {
            Log.w(TAG, "Received null URI");
            result.error = "Null input data";
            return result;
        }

        final Topic topic = Cache.getTinode().getTopic(topicName);

        Drafty content = null;
        boolean success = false;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            int imageWidth = 0, imageHeight = 0;

            Bundle fileDetails = getFileDetails(context, uri, file);
            String fname = fileDetails.getString("name");
            long fsize = fileDetails.getLong("size");
            String mimeType = fileDetails.getString("mime");

            if (fsize == 0) {
                Log.w(TAG, "File size is zero; uri=" + uri + "; file="+file);
                result.error = context.getString(R.string.invalid_file);
                return result;
            }

            if (fname == null) {
                fname = context.getString(R.string.default_attachment_name);
            }

            final ContentResolver resolver = context.getContentResolver();

            // Image is being attached.
            if ("image".equals(operation)) {
                Bitmap bmp = null;

                // Make sure the image is not too large.
                if (fsize > MAX_INBAND_ATTACHMENT_SIZE) {
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
                    int degrees = fileDetails.getInt("orientation");
                    if (degrees == -1) {
                        ExifInterface exif = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)  {
                            is = resolver.openInputStream(uri);
                            //noinspection ConstantConditions
                            exif = new ExifInterface(is);
                        } else {
                            String path = fileDetails.getString("path");
                            if (path != null) {
                                exif = new ExifInterface(path);
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
                        switch (degrees) {
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

                    is = UiUtils.bitmapToStream(bmp, mimeType);
                    fsize = is.available();
                }
            }

            if (fsize > MAX_ATTACHMENT_SIZE) {
                Log.w(TAG, "Unable to process attachment: too big, size=" + fsize);
                result.error = context.getString(R.string.attachment_too_large,
                        UiUtils.bytesToHumanSize(fsize), UiUtils.bytesToHumanSize(MAX_ATTACHMENT_SIZE));
            } else {
                if (is == null) {
                    is = resolver.openInputStream(uri);
                }

                if ("file".equals(operation) && fsize > MAX_INBAND_ATTACHMENT_SIZE) {

                    // Update draft with file data.
                    store.msgDraftUpdate(topic, result.msgId, draftyAttachment(mimeType, fname, uri.toString(), -1));

                    Progress start = callbackProgress.get();
                    if (start != null) {
                        start.onStart(topicName, result.msgId);
                        // This assignment is needed to ensure that the loader does not keep
                        // a strong reference to activity while potentially slow upload process
                        // is running.
                        //noinspection UnusedAssignment
                        start = null;
                    }

                    // Upload then send message with a link. This is a long-running blocking call.
                    final LargeFileHelper uploader = Cache.getTinode().getFileUploader();
                    MsgServerCtrl ctrl = uploader.upload(is, fname, mimeType, fsize,
                            new LargeFileHelper.FileHelperProgress() {
                                @Override
                                public void onProgress(long progress, long size) {
                                    Progress p = callbackProgress.get();
                                    if (p != null) {
                                        if (!p.onProgress(topicName, loaderId, result.msgId, progress, size)) {
                                            uploader.cancel();
                                        }
                                    }
                                }
                            });
                    success = (ctrl != null && ctrl.code == 200);
                    if (success) {
                        content = draftyAttachment(mimeType, fname, ctrl.getStringParam("url", null), fsize);
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
                        store.msgDraftUpdate(topic, result.msgId, draftyFile(mimeType, bits, fname));
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
                        store.msgDraftUpdate(topic, result.msgId,
                                draftyImage(args.getString("caption"),
                                        mimeType, bits, imageWidth, imageHeight, fname));
                    }
                    success = true;
                    Progress start = callbackProgress.get();
                    if (start != null) {
                        start.onStart(topicName, result.msgId);
                    }
                }
            }
        } catch (IOException | NullPointerException ex) {
            result.error = ex.getMessage();
            if (!"cancelled".equals(result.error)) {
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

        if (result.msgId > 0) {
            if (success) {
                // Success: mark message as ready for delivery. If content==null it won't be saved.
                store.msgReady(topic, result.msgId, content);
            } else {
                // Failure: discard draft.
                store.msgDiscard(topic, result.msgId);
                result.msgId = -1;
            }
        }

        return result;
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

    static class Result {
        String error;
        long msgId = -1;
        boolean processed = false;

        Result() {
        }

        @NonNull
        public String toString() {
            return "msgId=" + msgId + ", error='" + error + "'";
        }
    }

    static class FileUploader extends AsyncTaskLoader<Result> {
        private static WeakReference<Progress> sProgress;
        private final Bundle mArgs;
        private Result mResult = null;

        FileUploader(Activity activity, Bundle args) {
            super(activity);
            mArgs = args;
        }

        static void setProgressHandler(Progress progress) {
            sProgress = new WeakReference<>(progress);
        }

        @Override
        public void onStartLoading() {

            if (mResult != null) {
                // Loader has result already. Deliver it.
                deliverResult(mResult);
            } else if (mArgs.getLong("msgId") <= 0) {
                // Create a new message which will be updated with upload progress.
                Storage store = BaseDb.getInstance().getStore();
                Drafty msg = new Drafty();
                String topicName = mArgs.getString("topic");
                long msgId = store.msgDraft(Cache.getTinode().getTopic(topicName),
                        msg, Tinode.draftyHeadersFor(msg));
                mArgs.putLong("msgId", msgId);
                Progress p = sProgress.get();
                if (p != null) {
                    p.onStart(topicName, msgId);
                }
                forceLoad();
            }
        }

        @Nullable
        @Override
        public Result loadInBackground() {
            // Don't upload again if upload was completed already.
            if (mResult == null) {
                mResult = doUpload(getId(), getContext(), mArgs, sProgress);
            }
            return mResult;
        }

        @Override
        public void onStopLoading() {
            super.onStopLoading();
            cancelLoad();
        }
    }

    interface Progress {
        void onStart(String topicName, long msgId);

        boolean onProgress(String topicName, final int loaderId, final long msgId, final long progress, final long total);
    }
}
