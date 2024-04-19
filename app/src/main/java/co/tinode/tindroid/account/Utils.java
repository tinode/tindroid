package co.tinode.tindroid.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;

import co.tinode.tindroid.db.BaseDb;

/**
 * Constants and misc utils
 */
public class Utils {
    // Account management constants
    public static final String TOKEN_TYPE = "co.tinode.token";
    public static final String TOKEN_EXPIRATION_TIME = "co.tinode.token_expires";
    public static final String ACCOUNT_TYPE = "co.tinode.account";
    public static final String SYNC_AUTHORITY = "com.android.contacts";
    public static final String TINODE_IM_PROTOCOL = "Tinode";
    // Constants for accessing shared preferences
    public static final String PREFS_HOST_NAME = "pref_hostName";
    public static final String PREFS_USE_TLS = "pref_useTLS";
    /**
     * MIME-type used when storing a profile {@link ContactsContract.Data} entry.
     */
    public static final String MIME_TINODE_PROFILE =
            "vnd.android.cursor.item/vnd.co.tinode.im";
    public static final String DATA_PID = Data.DATA1;
    static final String DATA_SUMMARY = Data.DATA2;
    static final String DATA_DETAIL = Data.DATA3;
    private static final String TAG = "Utils";

    public static Account createAccount(String uid) {
        return new Account(uid, ACCOUNT_TYPE);
    }

    public static Account getSavedAccount(final AccountManager accountManager,
                                          final @NonNull String uid) {
        Account account = null;

        // Let's find out if we already have a suitable account. If one is not found, go to full login. It will create
        // an account with suitable name.
        final Account[] availableAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        // Found some accounts, let's find the one with the right name
        for (Account acc : availableAccounts) {
            if (uid.equals(acc.name)) {
                account = acc;
                break;
            }
        }

        return account;
    }

    /**
     * Obtain authentication token for the currently active account.
     * @param context application context to use for AccountManager.
     *
     * @return token or null.
     */
    public static String getLoginToken(Context context) {
        String uid = BaseDb.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) {
            Log.w(TAG, "Data fetch failed: no login credentials");
            // Unknown if data is available, assuming it is.
            return null;
        }

        final AccountManager am = AccountManager.get(context);
        final Account account = getSavedAccount(am, uid);
        if (account == null) {
            Log.w(TAG, "Failed to obtain auth token: account not found");
            return null;
        }

        try {
            return AccountManager.get(context).blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
        } catch (AuthenticatorException | IOException | OperationCanceledException ex) {
            return null;
        }
    }
}
