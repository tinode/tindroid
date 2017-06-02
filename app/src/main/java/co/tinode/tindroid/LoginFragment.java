package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginFragment extends Fragment  implements View.OnClickListener {

    private static final String TAG = "LoginFragment";
    private AccountManager mAccountManager;

    public LoginFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(false);
        }

        mAccountManager = AccountManager.get(getActivity().getBaseContext());

        View fragment = inflater.inflate(R.layout.fragment_login, container, false);
        String login = ((LoginActivity) getActivity()).mAccountName;
        if (!TextUtils.isEmpty(login)) {
            TextView loginView = (TextView) fragment.findViewById(R.id.editLogin);
            if (loginView != null) {
                loginView.setText(login);
            }
        }

        fragment.findViewById(R.id.signIn).setOnClickListener(this);

        return fragment;
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_login, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Login button pressed.
     * @param v ignored
     */
    public void onClick(View v) {
        final LoginActivity parent = (LoginActivity) getActivity();

        EditText loginInput = (EditText) parent.findViewById(R.id.editLogin);
        EditText passwordInput = (EditText) parent.findViewById(R.id.editPassword);

        final String login = loginInput.getText().toString().trim();
        if (login.isEmpty()) {
            loginInput.setError(getText(R.string.login_required));
            return;
        }
        final String password = passwordInput.getText().toString().trim();
        if (password.isEmpty()) {
            passwordInput.setError(getText(R.string.password_required));
            return;
        }

        final Button signIn = (Button) parent.findViewById(R.id.signIn);
        signIn.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
        final Tinode tinode = Cache.getTinode();
        try {
            // This is called on the websocket thread.
            tinode.connect(hostName)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    return tinode.loginBasic(
                                            login,
                                            password);
                                }
                            },
                            null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    final Account acc = addAndroidAccount(
                                            tinode.getMyId(),
                                            AuthScheme.basicInstance(login, password).toString(),
                                            tinode.getAuthToken());

                                    // Force immediate sync, otherwise Contacts tab may be unusable.
                                    Bundle bundle = new Bundle();
                                    bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                    bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                    ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, bundle);

                                    UiUtils.onLoginSuccess(parent, signIn);
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                                    parent.reportError(err, signIn, R.string.error_login_failed);
                                    return null;
                                }
                            });
        } catch (Exception err) {
            Log.e(TAG, "Something went wrong", err);
            parent.reportError(err, signIn, R.string.error_login_failed);
        }
    }


    private Account addAndroidAccount(final String uid, final String secret, final String token) {
        final Account acc = Utils.createAccount(uid);
        mAccountManager.addAccountExplicitly(acc, secret, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAccountManager.notifyAccountAuthenticated(acc);
        }
        if (!TextUtils.isEmpty(token)) {
            mAccountManager.setAuthToken(acc, Utils.TOKEN_TYPE, token);
        }
        return acc;
    }
}
