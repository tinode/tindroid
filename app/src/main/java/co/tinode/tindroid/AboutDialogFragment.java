package co.tinode.tindroid;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

/**
 * About Dialog
 */
public class AboutDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        Activity activity = getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View dialog = View.inflate(activity, R.layout.dialog_about, null);
        ((TextView) dialog.findViewById(R.id.app_version)).setText(TindroidApp.getAppVersion());
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
