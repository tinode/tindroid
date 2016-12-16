package co.tinode.tindroid;

import android.*;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;

import java.io.IOException;
import java.net.URISyntaxException;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.Tinode;

/**
 * Splash screen on startup
 */
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize DB
        BaseDb.getInstance(getApplicationContext());
        // Init Tinode
        Tinode tinode = Cache.getTinode();

        Class toLaunch;
        if (tinode.isAuthenticated()) {
            // We already have a live connection to the server. All good.
            // Launch the contacts activity and stop.
            toLaunch = ContactsActivity.class;
        } else {
            AccountManager accountManager = AccountManager.get(this);
            // Get saved account by name.
            String accountName = getAccountName();

            if (TextUtils.isEmpty(accountName)) {
                // No saved account name. Go to Login  screen
                toLaunch = LoginActivity.class;
            } else {
                // Get saved account my name
                Account account = getSavedAccount(accountManager, accountName);
                if (account == null) {
                    // Account not found - go to LoginScreen
                    toLaunch = LoginActivity.class;
                } else {
                    // Account found, try to use it for login
                    loginWithSavedAccount(this, accountManager, account);
                    toLaunch = null;
                }
            }
        }

        if (toLaunch != null) {
            startActivity(new Intent(this, toLaunch));
            finish();
        }
    }

    private String getAccountName() {
        // Check if accountName is provided in the intent which launched this activity
        String accountName = getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        Log.d(TAG, "accountName from intent=" + accountName);

        // Account name is not in the intent, try reading one from preferences.
        if (TextUtils.isEmpty(accountName)) {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            accountName = preferences.getString(Utils.PREFS_ACCOUNT_NAME, null);
        }

        return accountName;
    }

    private Account getSavedAccount(AccountManager accountManager, String accountName) {
        Account account = null;

        // Run-time check for permission to GET_ACCOUNTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.GET_ACCOUNTS) !=
                        PackageManager.PERMISSION_GRANTED) {
            // Don't have permission. Apparently this is the first launch.
            // Fail and go to full login.
            Log.d(TAG, "NO permission to get accounts");
            return null;
        }

        // Have permission to access accounts. Let's find out if we already have a suitable account.
        final Account[] availableAccounts = accountManager.getAccountsByType(Utils.ACCOUNT_TYPE);
        if (availableAccounts.length > 0) {
            // Found some accounts, let's find the one with the right name
            for (Account acc : availableAccounts) {
                if (accountName.equals(acc.name)) {
                    account = acc;
                    break;
                }
            }
        }

        return account;
    }

    private static void loginWithSavedAccount(final Activity activity,
                                              final AccountManager accountManager,
                                              final Account account) {
        accountManager.getAuthToken(account, Utils.TOKEN_TYPE, null, false, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                Bundle result = null;
                Class toLaunch;

                try {
                    result = future.getResult(); // This blocks until the future is ready.
                } catch (OperationCanceledException e) {
                    Log.i(TAG, "Get Existing Account canceled.");
                } catch (AuthenticatorException e) {
                    Log.e(TAG, "AuthenticatorException: ", e);
                } catch (IOException e) {
                    Log.e(TAG, "IOException: ", e);
                }

                if (result == null) {
                    // No data for account
                    toLaunch = LoginActivity.class;
                } else {
                    final String token = result.getString(AccountManager.KEY_AUTHTOKEN);
                    if (TextUtils.isEmpty(token)) {
                        // Empty token, continue to login form
                        toLaunch = LoginActivity.class;
                    } else {
                        final SharedPreferences sharedPref
                                = PreferenceManager.getDefaultSharedPreferences(activity);
                        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
                        try {
                            // Connecting with synchronous calls because this is not the UI thread.
                            final Tinode tinode = Cache.getTinode();
                            tinode.connect(hostName).getResult();
                            tinode.loginToken(token).getResult();
                            // Logged in successfully, go to Contacts
                            toLaunch = ContactsActivity.class;
                        } catch (IOException ex) {
                            // Login failed due to network error
                            toLaunch = LoginActivity.class;
                        }
                        catch (Exception err) {
                            // Login failed due to non-network error
                            accountManager.invalidateAuthToken(Utils.ACCOUNT_TYPE, token);
                            toLaunch = LoginActivity.class;
                        }
                    }
                }
                activity.startActivity(new Intent(activity, toLaunch));
            }
        }, null);
    }
}
