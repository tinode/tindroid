package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceFragmentCompat;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginSettingsFragment extends PreferenceFragmentCompat {

    public LoginSettingsFragment() {
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setHasOptionsMenu(false);

        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        addPreferencesFromResource(R.xml.login_preferences);
    }
}
