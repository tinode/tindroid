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
        ((TextView) dialog.findViewById(R.id.app_version)).setText(TindroidApp.getAppVersion());
        ((TextView) dialog.findViewById(R.id.app_build)).setText(String.format(Locale.US, "%d",
                TindroidApp.getAppBuild()));
        ((TextView) dialog.findViewById(R.id.app_server)).setText(serverUrl);
        if (branding != null) {
            Bitmap logo = BrandingConfig.getLargeIcon(activity);
            if (logo != null) {
                ((ImageView) dialog.findViewById(R.id.imageLogo)).setImageBitmap(logo);
            }
            if (!TextUtils.isEmpty(branding.service_name)) {
                ((TextView) dialog.findViewById(R.id.appTitle)).setText(branding.service_name);
            }
            if (!TextUtils.isEmpty(branding.tos_url)) {
                String homePage = Uri.parse(branding.tos_url).getAuthority();
                if (!TextUtils.isEmpty(homePage)) {
                    ((TextView) dialog.findViewById(R.id.appHomePage)).setText(homePage);
                }
            }

            View byTinode = dialog.findViewById(R.id.byTinode);
            byTinode.setVisibility(View.VISIBLE);
            byTinode.setOnClickListener(arg ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://tinode.co"))));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(dialog)
                .setPositiveButton(android.R.string.ok, (d, id) -> {
                    // do nothing
                });

        return builder.create();
    }
}
