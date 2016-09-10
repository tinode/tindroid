package co.tinode.tindroid;


import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.SetDesc;

/**
 * LoginActivity is a FrameLayout which switches between three fragments:
 *  - LoginFragment
 *  - NewAccountFragment
 *  - LoginSettingsFragment
 */

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int PERMISSIONS_REQUEST_GET_ACCOUNTS = 100;

    public static final String EXTRA_CONFIRM_CREDENTIALS = "confirmCredentials";
    public static final String EXTRA_ADDING_ACCOUNT = "addNewAccount";
    public static final String EXTRA_TOKEN_TYPE = "tokenType";

    public static final String TOKEN_TYPE = "co.tinode.token";
    public static final String ACCOUNT_TYPE = "co.tinode.account";

    public static final String PREFS_ACCOUNT_NAME = "pref_accountName";
    public static final String PREFS_HOST_NAME = "pref_hostName";

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    private Bundle mResultBundle = null;

    private AccountManager mAccountManager;
    private String mAuthTokenType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        PreferenceManager.setDefaultValues(this, R.xml.login_preferences, false);

        /*
        // Retreives the AccountAuthenticatorResponse from either the intent or the savedInstanceState
        mAccountAuthenticatorResponse =
                getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);

        if (mAccountAuthenticatorResponse != null) {
            mAccountAuthenticatorResponse.onRequestContinued();
        }
        */

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Handle clicks on the '<-' arrow
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
                trx.replace(R.id.contentFragment, new LoginFragment());
                trx.commit();
            }
        });

        if (InmemoryCache.getTinode().isAuthenticated()) {
            // We already have a live connection to the server. All good.
            // Launch the contacts activity and stop.
            startActivity(new Intent(getApplicationContext(), ContactsActivity.class));
            finish();
            return;
        }

        // Display the login form
        FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
        trx.replace(R.id.contentFragment, new LoginFragment());
        trx.commit();


        mAuthTokenType = getIntent().getStringExtra(EXTRA_TOKEN_TYPE);
        if (mAuthTokenType == null) {
            mAuthTokenType = TOKEN_TYPE;
        }

        // See if we can get an auth token from a saved account
        mAccountManager = AccountManager.get(getBaseContext());
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Account account = fetchAccount(preferences);

        // Got account, let's use it to log in
        if (account != null) {
            Log.d(TAG, "accountName=" + account.name + "; accountType=" + account.type + ";");
            AccountManagerFuture<Bundle> result
                    = mAccountManager.getAuthToken(account, TOKEN_TYPE, null, false, null, null);
        } else {
            Log.d(TAG, "NO account found, do nothing here");
            /*

            // Let's add an account
            mAccountManager.addAccount(ACCOUNT_TYPE, TOKEN_TYPE, null, null, this, new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    Log.d(TAG, "HoHoHo adding account completed!");
                }
            }, null);
            */
        }
    }

    private Account fetchAccount(SharedPreferences preferences) {
        Account account = null;

        // Check if accountName is provided in the intent which launched this activity
        String accountName = getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        Log.d(TAG, "accountName from intent=" + accountName);

        // Account name is not in the intent, try reading one from preferences.
        if (TextUtils.isEmpty(accountName)) {
            accountName = preferences.getString(PREFS_ACCOUNT_NAME, null);
        }
        Log.d(TAG, "accountName from preferences=" + accountName);

        // Got account name, Let's try lo load it.
        if (!TextUtils.isEmpty(accountName)) {
            // Show user which account we found
            //((TextView)findViewById(R.id.editLogin)).setText(accountName);

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
            final Account[] availableAccounts = mAccountManager.getAccountsByType(ACCOUNT_TYPE);
            if (availableAccounts.length > 0) {
                // Found some accounts, let's find the one saved from before or ask user to choose/create one
                if (!TextUtils.isEmpty(accountName)) {
                    for (Account acc : availableAccounts) {
                        if (accountName.equals(acc.name)) {
                            account = acc;
                            Log.d(TAG, "Account found: " + accountName);
                        }
                    }
                } else if (availableAccounts.length == 1) {
                    // We only have one account to choose from, so use it.
                    account = availableAccounts[0];
                    preferences.edit().putString(PREFS_ACCOUNT_NAME, accountName).apply();
                } else {
                    // TODO: Display account chooser dialog
                }
            }
        }

        return account;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_GET_ACCOUNTS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                Log.d(TAG, "Access granted");
            } else {
                Log.d(TAG, "Access denied");
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
            trx.replace(R.id.contentFragment, new LoginSettingsFragment());
            trx.commit();
            return true;
        } else if (id == R.id.action_signup) {
            FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
            trx.replace(R.id.contentFragment, new NewAccountFragment());
            trx.commit();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onLogin(View v) {
        final String login = ((EditText) findViewById(R.id.editLogin)).getText().toString().trim();
        if (login.isEmpty()) {
            ((EditText) findViewById(R.id.editLogin)).setError(getText(R.string.login_required));
            return;
        }
        final String password = ((EditText) findViewById(R.id.editPassword)).getText().toString().trim();
        if (password.isEmpty()) {
            ((EditText) findViewById(R.id.editLogin)).setError(getText(R.string.password_required));
            return;
        }
        final Button signIn = (Button) findViewById(R.id.singnIn);
        signIn.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String hostName = sharedPref.getString(PREFS_HOST_NAME, InmemoryCache.HOST_NAME);

        try {
            // This is called on the websocket thread.
            InmemoryCache.getTinode().connect(hostName)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    return InmemoryCache.getTinode().loginBasic(
                                            login,
                                            password);
                                }
                            },
                            null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {

                                    LoginActivity.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            signIn.setEnabled(true);
                                        }
                                    });

                                    final Account acc = new Account(login, ACCOUNT_TYPE);
                                    sharedPref.edit().putString(PREFS_ACCOUNT_NAME, login).apply();
                                    mAccountManager.addAccountExplicitly(acc, password, null);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        mAccountManager.notifyAccountAuthenticated(acc);
                                    }
                                    final String token = InmemoryCache.getTinode().getAuthToken();
                                    if (!TextUtils.isEmpty(token)) {
                                        mAccountManager.setAuthToken(acc, TOKEN_TYPE, token);
                                    }
                                    startActivity(new Intent(getApplicationContext(),
                                            ContactsActivity.class));
                                    finish();
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
        } catch (Exception e) {
            Log.e(TAG, "Something went wrong", e);
            signIn.setEnabled(true);
        }
    }

    private void reportError(Exception err, final Button button, final int errId) {
        final String message = err.getMessage();
        Log.i(TAG, "connection failed :( " + message);
        LoginActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                button.setEnabled(true);
                Toast.makeText(getApplicationContext(),
                        getText(errId) + message, Toast.LENGTH_LONG).show();
            }
        });
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
        String hostName = sharedPref.getString(PREFS_HOST_NAME, InmemoryCache.HOST_NAME);
        final String fullName = ((EditText) findViewById(R.id.fullName)).getText().toString().trim();
        final ImageView avatar = (ImageView) findViewById(R.id.imageAvatar);
        try {
            // This is called on the websocket thread.
            InmemoryCache.getTinode().connect(hostName)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) throws Exception {
                                    // Try to create a new account.
                                    Bitmap bmp = null;
                                    try {
                                        bmp = ((BitmapDrawable)avatar.getDrawable()).getBitmap();
                                    } catch (ClassCastException ignored) {
                                        // If image is not loaded, the drawable is a vector.
                                        // Ignore it.
                                    }
                                    VCard vcard = new VCard(fullName, bmp);
                                    return InmemoryCache.getTinode().createAccountBasic(
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
