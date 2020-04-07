package co.tinode.tindroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginSettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "LoginSettingsFragment";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return;
        }

        setHasOptionsMenu(false);

        ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setHomeButtonEnabled(true);
            bar.setTitle(R.string.settings);
        }

        addPreferencesFromResource(R.xml.login_preferences);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        onSharedPreferenceChanged(sharedPreferences, "pref_hostName");
        onSharedPreferenceChanged(sharedPreferences, "pref_useTLS");
        onSharedPreferenceChanged(sharedPreferences, "pref_wireTransport");
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference preference = findPreference(key);
        Context context = getContext();
        if (preference == null || context == null) {
            return;
        }

        switch (preference.getKey()) {
            case "pref_wireTransport":
                ListPreference listPreference = (ListPreference) preference;
                int prefIndex = listPreference.findIndexOfValue(sharedPreferences.getString(key,null));
                if (prefIndex >= 0) {
                    preference.setSummary(getString(R.string.settings_wire_explained,
                            listPreference.getEntries()[prefIndex]));
                }
                break;
            case "pref_useTLS":
                break;
            case "pref_hostName":
                preference.setSummary(getString(R.string.settings_host_name_explained,
                        sharedPreferences.getString("pref_hostName", TindroidApp.getDefaultHostName(context))));
                break;
            default:
                Log.w(TAG, "Unknown preference '" + key + "'");
                // do nothing.
        }
    }
}
