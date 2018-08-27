package co.tinode.tindroid;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
 * About Dialog
 */
public class AboutDialogFragment extends DialogFragment {
    private static final String TAG = "AboutDialogFragment";

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        Activity activity = getActivity();
        String version = "";
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Failed to retrieve app version", e);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View dialog = View.inflate(activity, R.layout.dialog_about, null);
        ((TextView) dialog.findViewById(R.id.app_version)).setText(version);
        builder.setView(dialog)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int id) {
                        // do nothing
                    }
                });

        return builder.create();
    }
}
