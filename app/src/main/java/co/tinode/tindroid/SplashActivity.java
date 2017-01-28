package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;

/**
 * Splash screen on startup
 */
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize database helper with global context.
        BaseDb.init(getApplicationContext());
        String uid = BaseDb.getInstance().getUid();

        if (!TextUtils.isEmpty(uid)) {
            AccountManager accountManager = AccountManager.get(this);
            // If uid is non-null, get account to use it to login by saved token
            Account account = getSavedAccount(accountManager, uid);
            if (account != null) {
                // Check if sync is enabled.
                if (ContentResolver.getMasterSyncAutomatically()) {
                    if (!ContentResolver.getSyncAutomatically(account, Utils.SYNC_AUTHORITY)) {
                        ContentResolver.setSyncAutomatically(account, Utils.SYNC_AUTHORITY, true);
                    }
                }

                // Account found, try to use it for login
                UiUtils.loginWithSavedAccount(this, accountManager, account);
                finish();
                return;
            }
        }

        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private Account getSavedAccount(final AccountManager accountManager, final @NonNull String uid) {
        Account account = null;

        // Run-time check for permission to GET_ACCOUNTS
        if (!UiUtils.checkAccountAccessPermission(this)) {
            // Don't have permission. It's the first launch or the user denied access.
            // Fail and go to full login. We should not ask for permission on the splash screen.
            Log.d(TAG, "NO permission to get accounts");
            return null;
        }

        // Have permission to access accounts. Let's find out if we already have a suitable account.
        // If one is not found, go to full login. It will create an account with suitable name.
        final Account[] availableAccounts = accountManager.getAccountsByType(Utils.ACCOUNT_TYPE);
        if (availableAccounts.length > 0) {
            // Found some accounts, let's find the one with the right name
            for (Account acc : availableAccounts) {
                if (uid.equals(acc.name)) {
                    account = acc;
                    break;
                }
            }
        }

        return account;
    }
}
