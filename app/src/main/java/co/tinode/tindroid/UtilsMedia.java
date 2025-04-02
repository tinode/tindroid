package co.tinode.tindroid;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

public class UtilsMedia {
    private static final String TAG = "UtilsMedia";

    // Registers a photo picker activity launcher in single-select mode.
    @NonNull
    static ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher(final @NonNull Fragment fragment,
                                                                            final @NonNull MediaPreviewer previewer) {
        return fragment.registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null) {
                Log.d(TAG, "pickMediaLauncher returned no data");
                return;
            }
            final Bundle args = new Bundle();
            args.putParcelable(AttachmentHandler.ARG_LOCAL_URI, uri);
            previewer.handleMedia(args);
        });
    }

    static ActivityResultLauncher<Void> takePreviewPhotoLauncher(final @NonNull Fragment fragment,
                                                             final @NonNull MediaPreviewer previewer) {
        return fragment.registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap == null) {
                Log.d(TAG, "takePreviewPhotoLauncher failed");
                return;
            }
            final Bundle args = new Bundle();
            args.putParcelable(AttachmentHandler.ARG_SRC_BITMAP, bitmap);
            previewer.handleMedia(args);
        });
    }

    public interface MediaPreviewer {
        void handleMedia(Bundle args);
    }

    // Create temporary file to store image or video and return its Uri.
    @NonNull
    private static Uri createTempUri(Context context, String prefix, String suffix) throws IOException {
        String imageFileName = prefix +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_";
        File file = File.createTempFile(imageFileName, suffix,
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES));

        // Make sure path exists.
        File path = file.getParentFile();
        if (path != null) {
            path.mkdirs();
        }

        return FileProvider.getUriForFile(context, "co.tinode.tindroid.provider", file);
    }

    // Create temporary file to store image and return its Uri.
    @NonNull
    static Uri createTempPhotoUri(Context context) throws IOException {
        return createTempUri(context, "IMG_", ".jpg");
    }

    // Create temporary file to store video and return its Uri.
    @NonNull
    static Uri createTempVideoUri(Context context) throws IOException {
        return createTempUri(context, "VID_", ".mp4");
    }
}
