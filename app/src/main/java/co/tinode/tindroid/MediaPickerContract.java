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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

public class MediaPickerContract extends ActivityResultContract<Object, Uri> {

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
        mediaUri = intent.getData();
        return mediaUri;
    }

    private Intent openImageIntent(Context context) {
        Intent camIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        mediaUri = createPhotoTakenUri(context);
        camIntent.putExtra(MediaStore.EXTRA_OUTPUT, mediaUri);

        Intent gallIntent = new Intent(Intent.ACTION_GET_CONTENT);
        gallIntent.setType("image/*");

        List<Intent> yourIntentsList = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        List<ResolveInfo> found = pm.queryIntentActivities(camIntent, PackageManager.MATCH_ALL);
        for (ResolveInfo ri : found) {
            Intent finalIntent = new Intent(camIntent);
            finalIntent.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            yourIntentsList.add(finalIntent);
        }

        found = pm.queryIntentActivities(gallIntent, PackageManager.MATCH_ALL);
        for (ResolveInfo ri : found) {
            Intent finalIntent = new Intent(gallIntent);
            finalIntent.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            yourIntentsList.add(finalIntent);
        }

        Intent chooser = Intent.createChooser(gallIntent, context.getString(R.string.select_image_or_video));
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, yourIntentsList.toTypedArray());

        return chooser;
    }

    private Uri createPhotoTakenUri(Context context) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";
        File file = File.createTempFile(imageFileName, ".jpg",
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES));
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
    }
}