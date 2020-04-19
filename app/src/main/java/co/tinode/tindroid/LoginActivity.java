package co.tinode.tindroid;


import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.db.BaseDb;

/**
 * LoginActivity is a FrameLayout which switches between fragments:
 *  - LoginFragment
 *  - SignUpFragment
 *  - LoginSettingsFragment
 *  - PasswordResetFragment
 *  - CredentialsFragment
 *
 *  1. If connection to the server is already established and authenticated, launch ContactsActivity
 *  2. If no connection to the server, get the last used account:
 *  3.1 Connect to server
 *  3.1.1 If connection is successful, authenticate with the token received from the account
 *  3.1.1.1 If authentication is successful go to 1.
 *  3.1.1.2 If not, go to 4.
 *  3.1.2 If connection is not successful
 *  3.1.2 Show offline indicator
 *  3.1.3 Access locally stored account.
 *  3.1.3.1 If locally stored account is found, launch ContactsActivity
 *  3.1.3.2 If not found, go to 4.
 *  4. If account not found, show login form
 *
 */

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    public static final String EXTRA_CONFIRM_CREDENTIALS = "confirmCredentials";
    public static final String EXTRA_ADDING_ACCOUNT = "addNewAccount";

    static final String FRAGMENT_LOGIN = "login";
    static final String FRAGMENT_SIGNUP = "signup";
    static final String FRAGMENT_SETTINGS = "settings";
    static final String FRAGMENT_RESET = "reset";
    static final String FRAGMENT_CREDENTIALS = "cred";

    static final String PREFS_LAST_LOGIN = "pref_lastLogin";

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    private Bundle mResultBundle = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        PreferenceManager.setDefaultValues(this, R.xml.login_preferences, false);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Handle clicks on the '<-' arrow in the toolbar.
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSupportFragmentManager().popBackStack();
            }
        });

        BaseDb db = BaseDb.getInstance();
        if (db.isReady()) {
            // We already have a configured account. All good. Launch ContactsActivity and stop.
            Intent intent = new Intent(this, ChatsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            return;
        }

        // Check if we need full authentication or just credentials.
        showFragment(db.isCredValidationRequired() ? FRAGMENT_CREDENTIALS : FRAGMENT_LOGIN,
                null, false);

        // Check and possibly request runtime permissions.
        // requestPermissions();
    }

    @Override
    public void onResume() {
        super.onResume();
        UiUtils.setupToolbar(this, null, null, false);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    void reportError(final Exception err, final Button button, final int attachTo, final int errId) {
        String message = getText(errId).toString();
        String errMessage = err != null ? err.getMessage() : "";

        if (err != null) {
            Throwable cause = err;
            while ((cause = cause.getCause()) != null) {
                errMessage = cause.getMessage();
            }
        }
        final String finalMessage = message +
                (!TextUtils.isEmpty(errMessage) ? (": " + errMessage + "") : "");
        Log.i(TAG, finalMessage, err);

        runOnUiThread(new Runnable() {
            public void run() {
                if (button != null) {
                    button.setEnabled(true);
                }
                EditText field = attachTo != 0 ? (EditText) findViewById(attachTo) : null;
                if (field != null) {
                    field.setError(finalMessage);
                } else {
                    Toast.makeText(LoginActivity.this, finalMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings: {
                showFragment(FRAGMENT_SETTINGS);
                return true;
            }
            case R.id.action_signup: {
                showFragment(FRAGMENT_SIGNUP);
                return true;
            }
            case R.id.action_about:
                DialogFragment about = new AboutDialogFragment();
                about.show(getSupportFragmentManager(), "about");
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void showFragment(String tag) {
        showFragment(tag, null, true);
    }

    private void showFragment(String tag, Bundle args, Boolean addToBackstack) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_LOGIN:
                    fragment = new LoginFragment();
                    break;
                case FRAGMENT_SETTINGS:
                    fragment = new LoginSettingsFragment();
                    break;
                case FRAGMENT_SIGNUP:
                    fragment = new SignUpFragment();
                    break;
                case FRAGMENT_RESET:
                    fragment = new PasswordResetFragment();
                    break;
                case FRAGMENT_CREDENTIALS:
                    fragment = new CredentialsFragment();
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }

        if (args != null) {
            fragment.setArguments(args);
        }

        FragmentTransaction tx = fm.beginTransaction()
                .replace(R.id.contentFragment, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        if (addToBackstack) {
            tx = tx.addToBackStack(null);
        }
        tx.commitAllowingStateLoss();
    }

    /**
     * Sends the result or a Constants.ERROR_CODE_CANCELED error if a result isn't present.
     */
    @Override
    public void finish() {
        if (mAccountAuthenticatorResponse != null) {
            // send the result bundle back if set, otherwise send an error.
            if (mResultBundle != null) {
                mAccountAuthenticatorResponse.onResult(mResultBundle);
            } else {
                mAccountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
            }
            mAccountAuthenticatorResponse = null;
        }
        super.finish();
    }
}
