package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginSettingsFragment extends PreferenceFragmentCompat
        implements MenuProvider, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "LoginSettingsFragment";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();

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
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
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
                @SuppressLint("UnsafeOptInUsageError")
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
                Log.d(TAG, "Unknown preference '" + key + "'");
                // do nothing.
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menu.clear();
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }
}
