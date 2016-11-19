package co.tinode.tindroid;

import android.*;
import android.accounts.Account;
import android.accounts.AccountManager;
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

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.Tinode;

/**
 * Splash screen on startup
 */
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent;
        Tinode tinode = Cache.getTinode();
        if (tinode.isAuthenticated()) {
            // We already have a live connection to the server. All good.
            // Launch the contacts activity and stop.
            intent = new Intent(this, ContactsActivity.class);
            intent.putExtra(Utils.ACCKEY_UID, tinode.getMyId());
        } else {
            // Get saved account by name.
            String accountName = getAccountName();
            if (TextUtils.isEmpty(accountName)) {
                intent = new Intent(this, LoginActivity.class);
            } else {
                // If successful, go to Contacts, otherwise go to Login.
                Account account = getSavedAccount(accountName);
                if (account == null) {
                    intent = new Intent(this, LoginActivity.class);
                } else {
                    AccountManager accountManager = AccountManager.get(this);
                    String uid = accountManager.getUserData(account, Utils.ACCKEY_UID);
                    if (TextUtils.isEmpty(uid)) {
                        intent = new Intent(this, LoginActivity.class);
                    } else {
                        intent = new Intent(this, ContactsActivity.class);
                        intent.putExtra(Utils.ACCKEY_UID, uid);
                    }
                }
                intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, accountName);
            }
        }

        startActivity(intent);
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

    private Account getSavedAccount(String accountName) {
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

        // Have permission to access accounts. Let's find if we already have a suitable account.
        AccountManager accountManager = AccountManager.get(this);
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
