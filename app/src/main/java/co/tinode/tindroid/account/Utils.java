package co.tinode.tindroid.account;

import android.accounts.Account;

/**
 * Constants and misc utils
 */

public class Utils {

    public static final String TOKEN_TYPE = "co.tinode.token";
    public static final String ACCOUNT_TYPE = "co.tinode.account";

    public static Account GetAccount(String accountName) {
        return new Account(accountName, ACCOUNT_TYPE);
    }

}
