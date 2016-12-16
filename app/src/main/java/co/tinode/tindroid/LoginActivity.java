package co.tinode.tindroid;


import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.SetDesc;

/**
 * LoginActivity is a FrameLayout which switches between three fragments:
 *  - LoginFragment
 *  - SignUpFragment
 *  - LoginSettingsFragment
 *
 *  1. If connection to the server is already established and authenticated, launch ContactsActivity
 *  2. If no connection to the server, get the last used account:
 *  2.1 Check intent if account name if provided there
 *  2.2 If not, check if account name is provided in Preferences
 *  2.3 If not, choose an account of correct type
 *  2.3.1 If just one account of correct type found, use it
 *  2.3.2 If more accounts found, show an account picker form, use selected account
 *  3. If account found, use it to log in:
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
    private static final int PERMISSIONS_REQUEST_GET_ACCOUNTS = 100;
    private static final int ACCOUNT_PICKER_CODE = 101;

    public static final String EXTRA_CONFIRM_CREDENTIALS = "confirmCredentials";
    public static final String EXTRA_ADDING_ACCOUNT = "addNewAccount";

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    private Bundle mResultBundle = null;
    public String mAccountName = null;

    private LoginFragment mLoginFragment = null;
    private SignUpFragment mSignUpFragment = null;
    private LoginSettingsFragment mSettingsFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        PreferenceManager.setDefaultValues(this, R.xml.login_preferences, false);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Handle clicks on the '<-' arrow in the toolbar.
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLoginFragment();
            }
        });

        Tinode tinode = Cache.getTinode();
        if (tinode.isAuthenticated()) {
            // We already have a live connection to the server. All good.
            // Launch the contacts activity and stop.
            Intent intent = new Intent(this, ContactsActivity.class);
            intent.putExtra(Utils.ACCKEY_UID, tinode.getMyId());
            startActivity(intent);
            finish();
            return;
        }

        // Display the login form.
        showLoginFragment();

        Log.d("TAG", "DONE onCreate");
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

    private Account fetchAccount(SharedPreferences preferences) {
        Account account = null;

        // Check if accountName is provided in the intent which launched this activity
        mAccountName = getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        Log.d(TAG, "accountName from intent=" + mAccountName);

        // Account name is not in the intent, try reading one from preferences.
        if (TextUtils.isEmpty(mAccountName)) {
            mAccountName = preferences.getString(Utils.PREFS_ACCOUNT_NAME, null);
        }

        // Got account name, Let's try lo load it.
        if (!TextUtils.isEmpty(mAccountName)) {

            // Run-time check for permission to GET_ACCOUNTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) !=
                            PackageManager.PERMISSION_GRANTED) {
                // Don't have permission, request it
                Log.d(TAG, "NO permission to get accounts");
                requestPermissions(new String[]{Manifest.permission.GET_ACCOUNTS}, PERMISSIONS_REQUEST_GET_ACCOUNTS);
                return null;
            }
            /*
            // Have permission to access accounts. Let's find if we already have a suitable account.
            final Account[] availableAccounts = mAccountManager.getAccountsByType(Utils.ACCOUNT_TYPE);
            if (availableAccounts.length > 0) {
                // Found some accounts, let's find the one saved from before or ask user to choose/create one
                account = pickAccountByName(mAccountName, availableAccounts);
                if (account == null) {
                    if (availableAccounts.length == 1) {
                        // We only have one account to choose from, so use it.
                        account = availableAccounts[0];
                        preferences.edit().putString(Utils.PREFS_ACCOUNT_NAME, mAccountName).apply();
                    } else {
                        // Let user choose an account from the list
                        String[] names = new String[availableAccounts.length];
                        for (int i = 0; i < availableAccounts.length; i++) {
                            names[i] = availableAccounts[i].name;
                        }
                        // Account picker sets mAccountName
                        displayAccountPicker(names, preferences);
                        account = pickAccountByName(mAccountName, availableAccounts);
                    }
                }
            }
            */
        }

        return account;
    }

    private void accountPicker() {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            intent = AccountManager.newChooseAccountIntent(null, null,
                    new String[]{Utils.ACCOUNT_TYPE},
                    false, null, null, null, null);
        } else {
            intent = AccountManager.newChooseAccountIntent(null, null,
                    new String[]{Utils.ACCOUNT_TYPE},
                    null, null, null, null);
        }
        startActivityForResult(intent, ACCOUNT_PICKER_CODE);
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (reqCode == ACCOUNT_PICKER_CODE) {
                String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

            }
        } else {
            showLoginFragment();
        }
    }

    private Account pickAccountByName(String name, Account[] list) {
        if (!TextUtils.isEmpty(name)) {
            for (Account acc : list) {
                if (mAccountName.equals(acc.name)) {
                    Log.d(TAG, "Account found: " + mAccountName);
                    return acc;
                }
            }
        }
        return null;
    }

    void reportError(final Exception err, final Button button, final int errId) {
        String message = err.getMessage();
        Log.i(TAG, getText(errId) + " " + message);

        Throwable cause = err;
        while ((cause = cause.getCause()) != null) {
            message = cause.getMessage();
        }
        final String finalMessage = message;

        runOnUiThread(new Runnable() {
            public void run() {
                if (button != null) {
                    button.setEnabled(true);
                }
                Toast.makeText(LoginActivity.this,
                        getText(errId) + " " + finalMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_GET_ACCOUNTS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                Log.d(TAG, "Access granted");
            } else {
                Log.d(TAG, "Access denied");
            }
        }
    }

    /**
     * Account picker
     */
    private void displayAccountPicker(final String[] accountList, final SharedPreferences preferences) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pick_account)
                .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, accountList),
                        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mAccountName = accountList[which];
                preferences.edit().putString(Utils.PREFS_ACCOUNT_NAME, mAccountName).apply();
            }
        }).create();
        dialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings: {
                showSettingsFragment();
                return true;
            }
            case R.id.action_signup: {
                showSignUpFragment();
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

    /** Display the login form */
    private void showLoginFragment() {
        if (mLoginFragment == null) {
            mLoginFragment = new LoginFragment();
        }
        FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
        trx.replace(R.id.contentFragment, mLoginFragment);
        trx.commit();
    }

    /** Display the sign up form */
    private void showSignUpFragment() {
        if (mSignUpFragment == null) {
            mSignUpFragment = new SignUpFragment();
        }
        FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
        trx.replace(R.id.contentFragment, mSignUpFragment);
        trx.commit();
    }

    /** Display the sign up form */
    private void showSettingsFragment() {
        if (mSettingsFragment == null) {
            mSettingsFragment = new LoginSettingsFragment();
        }
        FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
        trx.replace(R.id.contentFragment, mSettingsFragment);
        trx.commit();
    }
    /**
     * Called when the account needs to be added
     * @param account account t to be added
     * @param password password
     * @param token auth token
     */
    public void onTokenReceived(Account account, String password, String token) {
        final AccountManager am = AccountManager.get(this);
        final Bundle result = new Bundle();
        if (am.addAccountExplicitly(account, password, new Bundle())) {
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            result.putString(AccountManager.KEY_AUTHTOKEN, token);
            am.setAuthToken(account, account.type, token);
        } else {
            result.putString(AccountManager.KEY_ERROR_MESSAGE, getString(R.string.account_already_exists));
        }
        setAccountAuthenticatorResult(result);
        setResult(RESULT_OK);
        finish();
    }

    /**
     * Set the result that is to be sent as the result of the request that caused this
     * Activity to be launched. If result is null or this method is never called then
     * the request will be canceled.
     * @param result this is returned as the result of the AbstractAccountAuthenticator request
     */
    public final void setAccountAuthenticatorResult(Bundle result) {
        mResultBundle = result;
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
