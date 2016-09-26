package co.tinode.tindroid.account;

import android.accounts.Account;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;

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

    public static final String TAG_LABEL_PHONE = "tel:";
    public static final String TAG_LABEL_EMAIL = "email:";
    /**
     * MIME-type used when storing a profile {@link ContactsContract.Data} entry.
     */
    public static final String MIME_PROFILE =
            "vnd.android.cursor.item/vnd.tinode.profile";

    public static final String DATA_PID = Data.DATA1;
    public static final String DATA_SUMMARY = Data.DATA2;
    public static final String DATA_DETAIL = Data.DATA3;


    public static Account GetAccount(String accountName) {
        return new Account(accountName, ACCOUNT_TYPE);
    }

}
