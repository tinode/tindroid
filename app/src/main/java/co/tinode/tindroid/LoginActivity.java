package co.tinode.tindroid;


import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentResolver;
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
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.SetDesc;

/**
 * LoginActivity is a FrameLayout which switches between three fragments:
 *  - LoginFragment
 *  - NewAccountFragment
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

    public static final String EXTRA_CONFIRM_CREDENTIALS = "confirmCredentials";
    public static final String EXTRA_ADDING_ACCOUNT = "addNewAccount";

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    private Bundle mResultBundle = null;

    private AccountManager mAccountManager;

    public String mAccountName = null;

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
                FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
                trx.replace(R.id.contentFragment, new LoginFragment());
                trx.commit();
            }
        });

        if (Cache.getTinode().isAuthenticated()) {
            // We already have a live connection to the server. All good.
            // Launch the contacts activity and stop.
            startActivity(new Intent(getApplicationContext(), ContactsActivity.class));
            finish();
            return;
        }

        // See if we can get an auth token from a saved account
        mAccountManager = AccountManager.get(getBaseContext());
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Account account = fetchAccount(preferences);

        if (account != null) {
            // Got account, let's use it to log in (it may fail though).
            loginWithSavedAccount(account);
        } else {
            // Display the login form.
            showLoginForm();
        }
        Log.d("TAG", "DONE onCreate");
    }

    @Override
    public void onResume() {
        super.onResume();
        UiUtils.setupToolbar(this, null, null);
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
        }

        return account;
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

    private void loginWithSavedAccount(final Account account) {
        Log.d(TAG, "accountName=" + account.name + "; accountType=" + account.type + ";");
        final Button signIn = (Button) findViewById(R.id.singnIn);
        // signIn.setEnabled(false);
        // Fetch password
        // String password = mAccountManager.getPassword(account);

        mAccountManager.getAuthToken(account, Utils.TOKEN_TYPE, null, false, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                Bundle result = null;
                try {
                    result = future.getResult(); // This blocks until the future is ready.
                } catch (OperationCanceledException e) {
                    Log.i(TAG, "Get Existing Account canceled, exiting.");
                    finish();
                } catch (AuthenticatorException e) {
                    Log.e(TAG, "AuthenticatorException: ", e);
                } catch (IOException e) {
                    Log.e(TAG, "IOException: ", e);
                }
                if (result == null) {
                    return;
                }

                final String token = result.getString(AccountManager.KEY_AUTHTOKEN);
                if (TextUtils.isEmpty(token)) {
                    // Empty token, continue to login form
                    showLoginForm();
                    return;
                }

                final SharedPreferences sharedPref
                        = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
                String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
                try {
                    // Connecting with synchronous calls because this is not the UI thread.
                    final Tinode tinode = Cache.getTinode();
                    tinode.connect(hostName).getResult();
                    tinode.loginToken(token).getResult();
                    onLoginSuccess(account, signIn);
                } catch (Exception err) {
                    mAccountManager.invalidateAuthToken(Utils.ACCOUNT_TYPE, token);
                    reportError(err, signIn, R.string.error_login_failed);
                    showLoginForm();
                }
            }
        }, null);

        Log.d(TAG, "EXIT loginWithSavedAccount");
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
                FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
                trx.replace(R.id.contentFragment, new LoginSettingsFragment());
                trx.commit();
                return true;
            }
            case R.id.action_signup: {
                FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
                trx.replace(R.id.contentFragment, new NewAccountFragment());
                trx.commit();
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

    /**
     * Login button pressed.
     * @param v ignored
     */
    public void onLogin(View v) {
        EditText loginInput = (EditText) findViewById(R.id.editLogin);
        EditText passwordInput = (EditText) findViewById(R.id.editPassword);

        final String login = loginInput.getText().toString().trim();
        if (login.isEmpty()) {
            loginInput.setError(getText(R.string.login_required));
            return;
        }
        final String password = passwordInput.getText().toString().trim();
        if (password.isEmpty()) {
            passwordInput.setError(getText(R.string.password_required));
            return;
        }

        final Button signIn = (Button) findViewById(R.id.singnIn);
        signIn.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
        final Tinode tinode = Cache.getTinode();
        try {
            // This is called on the websocket thread.
            tinode.connect(hostName)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    return tinode.loginBasic(
                                            login,
                                            password);
                                }
                            },
                            null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    final Account acc = addAndroidAccount(sharedPref, login, password);
                                    ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, new Bundle());
                                    onLoginSuccess(acc, signIn);
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                                    reportError(err, signIn, R.string.error_login_failed);
                                    return null;
                                }
                            });
        } catch (Exception err) {
            Log.e(TAG, "Something went wrong", err);
            reportError(err, signIn, R.string.error_login_failed);
        }
    }

    private void reportError(final Exception err, final Button button, final int errId) {
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
                Toast.makeText(getApplicationContext(),
                        getText(errId) + " " + finalMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Login successful. Show contacts activity */
    private void onLoginSuccess(Account acc, final Button button) {
        if (button != null) {
            runOnUiThread(new Runnable() {
                public void run() {
                    button.setEnabled(true);
                }
            });
        }

        // Initialize database
        String uid = mAccountManager.getUserData(acc, Utils.ACCKEY_UID);
        BaseDb.getInstance(this, uid);

        startActivity(new Intent(getApplicationContext(),
                ContactsActivity.class));
        finish();
    }

    /** Display the login form */
    private void showLoginForm() {
        FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
        trx.replace(R.id.contentFragment, new LoginFragment());
        trx.commit();
    }

    /**
     * Create new account
     * @param v button pressed
     */
    public void onSignUp(View v) {
        final String login = ((EditText) findViewById(R.id.newLogin)).getText().toString().trim();
        if (login.isEmpty()) {
            ((EditText) findViewById(R.id.newLogin)).setError(getText(R.string.login_required));
            return;
        }
        final String password = ((EditText) findViewById(R.id.newPassword)).getText().toString().trim();
        if (password.isEmpty()) {
            ((EditText) findViewById(R.id.newPassword)).setError(getText(R.string.password_required));
            return;
        }

        String password2 = ((EditText) findViewById(R.id.repeatPassword)).getText().toString();
        // Check if passwords match. If not, report error.
        if (!password.equals(password2)) {
            ((EditText) findViewById(R.id.repeatPassword)).setError(getText(R.string.passwords_dont_match));
            Toast.makeText(this, getText(R.string.passwords_dont_match), Toast.LENGTH_SHORT).show();
            return;
        }

        final Button signUp = (Button) findViewById(R.id.singnUp);
        signUp.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
        final String fullName = ((EditText) findViewById(R.id.fullName)).getText().toString().trim();
        final ImageView avatar = (ImageView) findViewById(R.id.imageAvatar);
        final Tinode tinode = Cache.getTinode();
        try {
            // This is called on the websocket thread.
            tinode.connect(hostName)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) throws Exception {
                                    // Try to create a new account.
                                    Bitmap bmp = null;
                                    try {
                                        bmp = ((BitmapDrawable) avatar.getDrawable()).getBitmap();
                                    } catch (ClassCastException ignored) {
                                        // If image is not loaded, the drawable is a vector.
                                        // Ignore it.
                                    }
                                    VCard vcard = new VCard(fullName, bmp);
                                    return tinode.createAccountBasic(
                                            login, password, true,
                                            new SetDesc<VCard,String>(vcard, null));
                                }
                            }, null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    // Flip back to login screen on success;
                                    LoginActivity.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            signUp.setEnabled(true);
                                            FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
                                            trx.replace(R.id.contentFragment, new LoginFragment());
                                            trx.commit();
                                        }
                                    });
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                                    reportError(err, signUp, R.string.error_new_account_failed);
                                    return null;
                                }
                            });

        } catch (Exception e) {
            Log.e(TAG, "Something went wrong", e);
            signUp.setEnabled(true);
        }
    }

    public void onFacebookUp(View v) {
        Toast.makeText(getApplicationContext(), "Facebook: not implemented", Toast.LENGTH_SHORT).show();
    }

    public void onGoogleUp(View v) {
        Toast.makeText(getApplicationContext(), "Google: Not implemented", Toast.LENGTH_SHORT).show();
    }

    private Account addAndroidAccount(final SharedPreferences sharedPref, final String login,
                                   final String password) {
        final Account acc = Utils.GetAccount(login);
        sharedPref.edit().putString(Utils.PREFS_ACCOUNT_NAME, login).apply();
        mAccountManager.addAccountExplicitly(acc, password, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAccountManager.notifyAccountAuthenticated(acc);
        }
        final String token = Cache.getTinode().getAuthToken();
        if (!TextUtils.isEmpty(token)) {
            mAccountManager.setAuthToken(acc, Utils.TOKEN_TYPE, token);
        }
        final String uid = Cache.getTinode().getMyId();
        if (!TextUtils.isEmpty(uid)) {
            mAccountManager.setUserData(acc, Utils.ACCKEY_UID, uid);
        }
        return acc;
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
