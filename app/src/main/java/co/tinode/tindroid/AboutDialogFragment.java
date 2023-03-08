package co.tinode.tindroid;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
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

        BrandingConfig branding = BrandingConfig.getConfig(activity);

        String serverUrl = Cache.getTinode().getHttpOrigin();
        if (TextUtils.isEmpty(serverUrl)) {
            if (branding != null && !TextUtils.isEmpty(branding.api_url)) {
                serverUrl = branding.api_url;
            } else {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
                String hostname = pref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
                String scheme = pref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS()) ? "https://" : "http://";
                serverUrl = scheme + hostname;
            }
        }

        View dialog = View.inflate(activity, R.layout.dialog_about, null);
        UiUtils.fillAboutTinode(dialog, serverUrl, branding);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(dialog)
                .setPositiveButton(android.R.string.ok, (d, id) -> {
                    // do nothing
                });

        return builder.create();
    }
}
