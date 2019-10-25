package co.tinode.tindroid;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

/**
 * About Dialog
 */
public class AboutDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        final Activity activity = getActivity();
        if (activity == null) {
            throw new IllegalStateException("Activity is null");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View dialog = View.inflate(activity, R.layout.dialog_about, null);
        ((TextView) dialog.findViewById(R.id.app_version)).setText(TindroidApp.getAppVersion());
        ((TextView) dialog.findViewById(R.id.app_build)).setText(String.format(Locale.US, "%d", TindroidApp.getAppBuild()));
        ((TextView) dialog.findViewById(R.id.app_server)).setText(Cache.getTinode().getHttpOrigin());
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
