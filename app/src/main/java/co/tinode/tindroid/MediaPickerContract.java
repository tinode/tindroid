package co.tinode.tindroid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

public class MediaPickerContract extends ActivityResultContract<Object, Uri> {
    private static final String TAG = "MediaPickerContract";

    private Uri mediaUri = null;

    @Override
    @NonNull
    public Intent createIntent(@NonNull Context context, Object input) {
        return openImageIntent(context);
    }

    @Override
    public Uri parseResult(int resultCode, Intent intent) {
        if (resultCode != Activity.RESULT_OK) {
            return null;
        }

        Uri result = intent != null ? intent.getData() : null;
        return result != null ? result: mediaUri;
    }

    private Intent openImageIntent(Context context) {
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            mediaUri = createTempPhotoUri(context);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, mediaUri);
        } catch (IOException ex) {
            Log.w(TAG, "Failed to create a temp file for taking a photo", ex);
        }

        Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        gallery.setType("*/*");
        gallery.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});

        List<Intent> foundIntents = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        // Get all available cameras.
        List<ResolveInfo> found = pm.queryIntentActivities(camera, PackageManager.MATCH_ALL);
        for (ResolveInfo ri : found) {
            Intent intent = new Intent(camera);
            intent.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            foundIntents.add(intent);
        }

        // Find default gallery app.
        found = pm.queryIntentActivities(gallery, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : found) {
            Intent intent = new Intent(gallery);
            intent.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            foundIntents.add(intent);
        }

        Intent chooser = Intent.createChooser(gallery, context.getString(R.string.select_image_or_video));
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, foundIntents.toArray(new Intent[]{}));

        return chooser;
    }

    private Uri createTempPhotoUri(Context context) throws IOException {
        String imageFileName = "IMG_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_";
        File file = File.createTempFile(imageFileName, ".jpg",
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES));

        // Make sure path exists.
        File path = file.getParentFile();
        if (path != null) {
            path.mkdirs();
        }

        return FileProvider.getUriForFile(context, "co.tinode.tindroid.provider", file);
    }
}