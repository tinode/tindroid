package co.tinode.tindroid.services;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import co.tinode.tindroid.LoginActivity;
import co.tinode.tindroid.account.Utils;

/**
 * Authenticator service: make Tinode login work nicely with the Android authentication system.
 */
public class TinodeAccountService extends Service {
    private static final String TAG = "TinodeAccountService";

    private Authenticator mAuthenticator;

    @Override
    public void onCreate() {
        mAuthenticator = new Authenticator(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }

    public static class Authenticator extends AbstractAccountAuthenticator {
        private static final String TAG = "TinodeAuthenticator";
        private final Context mContext;

        Authenticator(Context context) {
            super(context);
            mContext = context;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
                                 String[] features, Bundle options) {
            Log.d(TAG, "addAccount, accountType=" + accountType + "; tokenType=" + authTokenType);

            final Intent intent = new Intent(mContext, LoginActivity.class);
            intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, accountType);
            intent.putExtra(LoginActivity.EXTRA_ADDING_ACCOUNT, true);
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

            final Bundle bundle = new Bundle();
            if (options != null) {
                bundle.putAll(options);
            }
            bundle.putParcelable(AccountManager.KEY_INTENT, intent);
            return bundle;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Bundle editProperties(AccountAuthenticatorResponse accountAuthenticatorResponse,
                                     String s) {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Bundle confirmCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse,
                                         Account account, Bundle bundle) {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType,
                                   Bundle options) {
            Log.d(TAG, "getAuthToken: " + authTokenType + "/" + account.type);

            if (!authTokenType.equals(Utils.TOKEN_TYPE)) {
                Log.e(TAG, "Invalid token type " + authTokenType + "; expected " + Utils.TOKEN_TYPE);

                final Bundle result = new Bundle();
                result.putString(AccountManager.KEY_ERROR_MESSAGE, "invalid authTokenType");
                return result;
            }

            final Bundle result = new Bundle();
            final AccountManager am = AccountManager.get(mContext);

            String authToken = am.peekAuthToken(account, authTokenType);

            // Got auth token?
            if (!TextUtils.isEmpty(authToken)) {
                // Yes, got auth token, either stored or a new one by using stored password
                result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
                result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
            } else {
                // No password or wrong password
                final Intent intent = new Intent(mContext, LoginActivity.class);
                intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
                result.putParcelable(AccountManager.KEY_INTENT, intent);
            }

            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getAuthTokenLabel(String s) {
            // Multiple token labels are not supported.
            Log.d(TAG, "getAuthTokenLabel()");
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Bundle updateCredentials(AccountAuthenticatorResponse response,
                                        Account account, String authTokenType, Bundle loginOptions) {
            Log.d(TAG, "updateCredentials()");
            final Intent intent = new Intent(mContext, LoginActivity.class);
            intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
            intent.putExtra(LoginActivity.EXTRA_CONFIRM_CREDENTIALS, false);
            final Bundle bundle = new Bundle();
            if (loginOptions != null) {
                bundle.putAll(loginOptions);
            }
            bundle.putParcelable(AccountManager.KEY_INTENT, intent);
            return bundle;
        }

        @Override
        public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) {
            // This call is used to query whether the Authenticator supports
            // specific features. We don't expect to get called, so we always
            // return false (no) for any queries.
            final Bundle result = new Bundle();
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
            return result;
        }
    }

}

