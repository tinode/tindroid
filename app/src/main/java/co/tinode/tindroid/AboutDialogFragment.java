package co.tinode.tindroid;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;

/**
 * About Dialog
 */
public class AboutDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = requireActivity();

        String serverUrl = Cache.getTinode().getHttpOrigin();
        if (TextUtils.isEmpty(serverUrl)) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
            String hostname = pref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
            String scheme = pref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS()) ? "https://" : "http://";
            serverUrl = scheme + hostname;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View dialog = View.inflate(activity, R.layout.dialog_about, null);
        ((TextView) dialog.findViewById(R.id.app_version)).setText(TindroidApp.getAppVersion());
        ((TextView) dialog.findViewById(R.id.app_build)).setText(String.format(Locale.US, "%d",
                TindroidApp.getAppBuild()));
        ((TextView) dialog.findViewById(R.id.app_server)).setText(serverUrl);
        builder.setView(dialog)
                .setPositiveButton(android.R.string.ok, (d, id) -> {
                    // do nothing
                });

        return builder.create();
    }
}
