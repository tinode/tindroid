package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
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

import java.util.Iterator;

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "LoginFragment";

    public LoginFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        setHasOptionsMenu(true);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return null;
        }

        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(false);
        }

        View fragment = inflater.inflate(R.layout.fragment_login, container, false);

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String login = pref.getString(LoginActivity.PREFS_LAST_LOGIN, null);

        if (!TextUtils.isEmpty(login)) {
            TextView loginView = fragment.findViewById(R.id.editLogin);
            if (loginView != null) {
                loginView.setText(login);
            }
        }

        fragment.findViewById(R.id.signIn).setOnClickListener(this);
        fragment.findViewById(R.id.forgotPassword).setOnClickListener(this);

        return fragment;
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_login, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Either [Signin] or [Forgot password] pressed.
     * @param v ignored
     */
    public void onClick(View v) {
        final LoginActivity parent = (LoginActivity) getActivity();
        if (parent == null) {
            return;
        }

        if (v.getId() == R.id.forgotPassword) {
            parent.showFragment(LoginActivity.FRAGMENT_RESET);
            return;
        }

        EditText loginInput = parent.findViewById(R.id.editLogin);
        EditText passwordInput = parent.findViewById(R.id.editPassword);

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

        final Button signIn = parent.findViewById(R.id.signIn);
        signIn.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        final String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, false);
        final Tinode tinode = Cache.getTinode();
        try {
            // This is called on the websocket thread.
            tinode.connect(hostName, tls)
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
                                public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) {
                                    sharedPref.edit().putString(LoginActivity.PREFS_LAST_LOGIN, login).apply();

                                    final Account acc = addAndroidAccount(
                                            tinode.getMyId(),
                                            AuthScheme.basicInstance(login, password).toString(),
                                            tinode.getAuthToken());

                                    if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                        parent.runOnUiThread(new Runnable() {
                                            public void run() {
                                                signIn.setEnabled(true);
                                                FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
                                                CredentialsFragment cf = new CredentialsFragment();
                                                Iterator<String> it = msg.ctrl.getStringIteratorParam("cred");
                                                if (it != null) {
                                                    cf.setMethod(it.next());
                                                }
                                                trx.replace(R.id.contentFragment, cf);
                                                trx.commit();
                                            }
                                        });
                                    } else {
                                        // Force immediate sync, otherwise Contacts tab may be unusable.
                                        Bundle bundle = new Bundle();
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                        ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, bundle);

                                        UiUtils.onLoginSuccess(parent, signIn);
                                    }
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) {
                                    Log.d(TAG, "Login failed", err);
                                    parent.reportError(err, signIn, 0, R.string.error_login_failed);
                                    return null;
                                }
                            });
        } catch (Exception err) {
            Log.e(TAG, "Something went wrong", err);
            parent.reportError(err, signIn, 0, R.string.error_login_failed);
        }
    }


    private Account addAndroidAccount(final String uid, final String secret, final String token) {
        final Activity a = getActivity();
        if (a == null) {
            return null;
        }
        final AccountManager am = AccountManager.get(a.getBaseContext());
        final Account acc = Utils.createAccount(uid);
        am.addAccountExplicitly(acc, secret, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.notifyAccountAuthenticated(acc);
        }
        if (!TextUtils.isEmpty(token)) {
            am.setAuthToken(acc, Utils.TOKEN_TYPE, token);
        }
        return acc;
    }
}
