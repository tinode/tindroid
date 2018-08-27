package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

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
        String uid = BaseDb.getInstance().getUid();
        if (!TextUtils.isEmpty(uid)) {
            final AccountManager accountManager = AccountManager.get(this);
            // If uid is non-null, get account to use it to login by saved token
            final Account account = UiUtils.getSavedAccount(this, accountManager, uid);
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
}
