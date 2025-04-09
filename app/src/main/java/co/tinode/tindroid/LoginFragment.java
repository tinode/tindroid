package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginFragment extends Fragment implements MenuProvider, View.OnClickListener {
    private static final String TAG = "LoginFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        AppCompatActivity activity = (AppCompatActivity) requireActivity();

        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(false);
            bar.setTitle(R.string.app_name);
        }

        View fragment = inflater.inflate(R.layout.fragment_login, container, false);
        fragment.findViewById(R.id.signIn).setOnClickListener(this);
        fragment.findViewById(R.id.forgotPassword).setOnClickListener(this);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Activity activity = requireActivity();
        ((MenuHost) activity).addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        initBranding(activity);
    }

    @Override
    public void onResume() {
        Activity activity = requireActivity();
        initBranding(activity);
        super.onResume();
    }

    private void initBranding(Activity activity) {
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
        String login = pref.getString(LoginActivity.PREFS_LAST_LOGIN, null);

        if (!TextUtils.isEmpty(login)) {
            TextView loginView = activity.findViewById(R.id.editLogin);
            if (loginView != null) {
                loginView.setText(login);
            }
        } else if (UiUtils.isAppFirstRun(activity)) {
            View branding = activity.findViewById(R.id.brandingSetup);
            branding.setVisibility(View.VISIBLE);
            branding.setOnClickListener(v ->
                    ((LoginActivity) activity).showFragment(LoginActivity.FRAGMENT_BRANDING, null));
        } else {
            BrandingConfig config;
            if ((config = BrandingConfig.getConfig(activity)) != null) {
                Bitmap logo = BrandingConfig.getLargeIcon(activity);
                if (logo != null) {
                    ((AppCompatImageView) activity.findViewById(R.id.imageLogo)).setImageBitmap(logo);
                    ((TextView) activity.findViewById(R.id.appTitle)).setText(config.service_name);

                    View byTinode = activity.findViewById(R.id.byTinode);
                    byTinode.setVisibility(View.VISIBLE);
                    UiUtils.clickToBrowseTinodeURL(byTinode);
                }
            }
        }
    }

    /**
     * Either [Sign in] or [Forgot password] pressed.
     *
     * @param v ignored
     */
    public void onClick(View v) {
        final LoginActivity parent = (LoginActivity) requireActivity();

        if (v.getId() == R.id.forgotPassword) {
            parent.showFragment(LoginActivity.FRAGMENT_RESET, null);
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
        @SuppressLint("UnsafeOptInUsageError")
        final String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
        @SuppressLint("UnsafeOptInUsageError")
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());
        final Tinode tinode = Cache.getTinode();
        // This is called on the websocket thread.
        tinode.connect(hostName, tls, false)
                .thenApply(
                        new PromisedReply.SuccessListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) {
                                return tinode.loginBasic(
                                        login,
                                        password);
                            }
                        })
                .thenApply(
                        new PromisedReply.SuccessListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) {
                                sharedPref.edit().putString(LoginActivity.PREFS_LAST_LOGIN, login).apply();

                                UiUtils.updateAndroidAccount(parent, tinode.getMyId(),
                                        AuthScheme.basicInstance(login, password).toString(),
                                        tinode.getAuthToken(), tinode.getAuthTokenExpiration());

                                // msg could be null if earlier login has succeeded.
                                if (msg != null && msg.ctrl.code >= 300 &&
                                        msg.ctrl.text.contains("validate credentials")) {
                                    parent.runOnUiThread(() -> {
                                        signIn.setEnabled(true);
                                        parent.showFragment(LoginActivity.FRAGMENT_CREDENTIALS, null);
                                    });
                                } else {
                                    tinode.setAutoLoginToken(tinode.getAuthToken());
                                    // Force immediate sync, otherwise Contacts tab may be unusable.
                                    UiUtils.onLoginSuccess(parent, signIn, tinode.getMyId());
                                }
                                return null;
                            }
                        })
                .thenCatch(
                        new PromisedReply.FailureListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onFailure(Exception err) {
                                Log.w(TAG, "Login failed", err);
                                parent.reportError(err, signIn, 0, R.string.error_login_failed);
                                return null;
                            }
                        });
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_login, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }
}
