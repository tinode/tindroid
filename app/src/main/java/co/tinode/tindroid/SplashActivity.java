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

        AccountManager accountManager = AccountManager.get(this);
        // Get saved account by name.
        String accountName = getAccountName();
        if (!TextUtils.isEmpty(accountName)) {
            // Get saved account my name
            Account account = getSavedAccount(accountManager, accountName);
            if (account != null) {
                // Account found, try to use it for login
                String uid = accountManager.getUserData(account, Utils.ACCKEY_UID);
                if (!TextUtils.isEmpty(uid)) {
                    BaseDb.setAccount(uid);
                }
                UiUtils.loginWithSavedAccount(this, accountManager, account);
                finish();
                return;
            }
        }

        startActivity(new Intent(this, LoginActivity.class));
        finish();
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
}
