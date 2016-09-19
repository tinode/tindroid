package co.tinode.tindroid.account;

import android.accounts.Account;

/**
 * Constants and misc utils
 */

public class Utils {

    // Account management constants
    public static final String TOKEN_TYPE = "co.tinode.token";
    public static final String ACCOUNT_TYPE = "co.tinode.account";
    public static final String IM_PROTOCOL = "Tinode";

    // Constants for accessing shared preferences
    public static final String PREFS_ACCOUNT_NAME = "pref_accountName";
    public static final String PREFS_HOST_NAME = "pref_hostName";

    public static Account GetAccount(String accountName) {
        return new Account(accountName, ACCOUNT_TYPE);
    }

}
