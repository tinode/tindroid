package co.tinode.tindroid;

import android.os.Bundle;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;

import android.content.SharedPreferences;

public class AccGeneralFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.acc_general_preferences, rootKey);

        // Set up the mutually exclusive behavior
        setupMutuallyExclusivePreferences();
    }

    private void setupMutuallyExclusivePreferences() {
        CheckBoxPreference lightTheme = findPreference(Utils.PREFS_KEY_LIGHT_THEME);
        CheckBoxPreference darkTheme = findPreference(Utils.PREFS_KEY_DARK_THEME);
        CheckBoxPreference autoTheme = findPreference(Utils.PREFS_KEY_AUTO_THEME);

        // Set initial state based on saved preference
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String currentTheme = prefs.getString(Utils.PREFS_UI_MODE, Utils.PREFS_KEY_AUTO_THEME);
        updateRadioButtons(currentTheme);

        // Set click listeners
        if (lightTheme != null) {
            lightTheme.setOnPreferenceClickListener(preference -> {
                handleThemeSelection(Utils.PREFS_KEY_LIGHT_THEME);
                return true;
            });
        }

        if (darkTheme != null) {
            darkTheme.setOnPreferenceClickListener(preference -> {
                handleThemeSelection(Utils.PREFS_KEY_DARK_THEME);
                return true;
            });
        }

        if (autoTheme != null) {
            autoTheme.setOnPreferenceClickListener(preference -> {
                handleThemeSelection(Utils.PREFS_KEY_AUTO_THEME);
                return true;
            });
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void handleThemeSelection(String selectedKey) {
        // Save the selected preference
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        prefs.edit().putString(Utils.PREFS_UI_MODE, selectedKey).apply();

        // Update radio button states
        updateRadioButtons(selectedKey);

        TindroidApp.onThemeChanged(selectedKey);
    }

    private void updateRadioButtons(String selectedKey) {
        CheckBoxPreference lightTheme = findPreference(Utils.PREFS_KEY_LIGHT_THEME);
        CheckBoxPreference darkTheme = findPreference(Utils.PREFS_KEY_DARK_THEME);
        CheckBoxPreference autoTheme = findPreference(Utils.PREFS_KEY_AUTO_THEME);

        if (lightTheme != null) lightTheme.setChecked(Utils.PREFS_KEY_LIGHT_THEME.equals(selectedKey));
        if (darkTheme != null) darkTheme.setChecked(Utils.PREFS_KEY_DARK_THEME.equals(selectedKey));
        if (autoTheme != null) autoTheme.setChecked(Utils.PREFS_KEY_AUTO_THEME.equals(selectedKey));
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null) {
            prefs.registerOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Handle any additional preference changes if needed
    }
}