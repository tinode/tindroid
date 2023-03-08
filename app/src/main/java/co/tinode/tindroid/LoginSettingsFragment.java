package co.tinode.tindroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginSettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "LoginSettingsFragment";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();

        setHasOptionsMenu(false);

        ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setHomeButtonEnabled(true);
            bar.setTitle(R.string.settings);
        }

        addPreferencesFromResource(R.xml.login_preferences);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        onSharedPreferenceChanged(sharedPreferences, Utils.PREFS_HOST_NAME);
        onSharedPreferenceChanged(sharedPreferences, Utils.PREFS_USE_TLS);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
        if (sp != null) {
            sp.registerOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
        if (sp != null) {
            sp.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference preference = findPreference(key);
        if (preference == null) {
            return;
        }

        switch (preference.getKey()) {
            case Utils.PREFS_USE_TLS:
                break;
            case Utils.PREFS_HOST_NAME:
                String hostName = TindroidApp.getDefaultHostName();
                if (TextUtils.isEmpty(hostName)) {
                    BrandingConfig config = BrandingConfig.getConfig(requireContext());
                    if (config != null && !TextUtils.isEmpty(config.api_url)) {
                        Uri serverUri = Uri.parse(config.api_url);
                        if (serverUri != null) {
                            hostName = serverUri.getAuthority();
                        }
                    }
                }
                preference.setSummary(getString(R.string.settings_host_name_explained,
                        sharedPreferences.getString(Utils.PREFS_HOST_NAME, hostName)));
                break;
            default:
                Log.i(TAG, "Unknown preference '" + key + "'");
                // do nothing.
        }
    }
}
