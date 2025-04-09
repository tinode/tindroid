package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.widgets.PhoneEdit;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.ServerMessage;

public class PasswordResetFragment extends Fragment implements MenuProvider {
    private static final String TAG = "PasswordResetFragment";

    private String[] mCredMethods = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final LoginActivity parent = (LoginActivity) requireActivity();

        ActionBar bar = parent.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.request_pass_reset_title);
        }

        View fragment = inflater.inflate(R.layout.fragment_pass_reset, container, false);

        fragment.findViewById(R.id.confirm).setOnClickListener(this::clickConfirm);
        fragment.findViewById(R.id.requestCode).setOnClickListener(this::clickRequest);
        fragment.findViewById(R.id.haveCode).setOnClickListener(this::clickHaveCode);

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final LoginActivity parent = (LoginActivity) requireActivity();
        parent.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        super.onResume();

        final LoginActivity parent = (LoginActivity) requireActivity();

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        @SuppressLint("UnsafeOptInUsageError")
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
        @SuppressLint("UnsafeOptInUsageError")
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final Tinode tinode = Cache.getTinode();
        tinode.connect(hostName, tls, false).thenApply(new PromisedReply.SuccessListener<>() {
            @Override
            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                if (parent.isFinishing() || parent.isDestroyed()) {
                    return null;
                }
                List<String> methods = UiUtils.getRequiredCredMethods(tinode, "auth");
                setupCredentials(parent, methods.toArray(new String[]{}));
                return null;
            }
        }).thenCatch(new PromisedReply.FailureListener<>() {
            @Override
            public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) {
                parent.runOnUiThread(() -> {
                    if (parent.isFinishing() || parent.isDestroyed() || !isVisible()) {
                        return;
                    }

                    parent.findViewById(R.id.requestCode).setEnabled(false);
                    parent.findViewById(R.id.haveCode).setEnabled(false);
                    Toast.makeText(parent, R.string.unable_to_use_service, Toast.LENGTH_LONG).show();
                });
                return null;
            }
        });
    }

    // Configure email or phone field.
    private void setupCredentials(Activity activity, String[] methods) {
        if (methods == null || methods.length == 0) {
            mCredMethods = new String[]{"email"};
        } else {
            mCredMethods = methods;
        }

        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            // Get the fragment's root view.
            View fragmentView = getView();
            if (fragmentView == null || !isVisible()) {
                return;
            }

            String method = mCredMethods[0];
            View emailWrapper = fragmentView.findViewById(R.id.emailWrapper);
            if (emailWrapper != null) {
                View willSendEmail = fragmentView.findViewById(R.id.will_send_email);
                View phone = fragmentView.findViewById(R.id.phone);
                View willSendSMS = fragmentView.findViewById(R.id.will_send_sms);

                if (method.equals("tel")) {
                    emailWrapper.setVisibility(View.GONE);
                    willSendEmail.setVisibility(View.GONE);
                    phone.setVisibility(View.VISIBLE);
                    willSendSMS.setVisibility(View.VISIBLE);
                } else if (method.equals("email")) {
                    emailWrapper.setVisibility(View.VISIBLE);
                    willSendEmail.setVisibility(View.VISIBLE);
                    phone.setVisibility(View.GONE);
                    willSendSMS.setVisibility(View.GONE);
                }
                // TODO: show generic text prompt for unknown method.
            }
        });
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menu.clear();
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }

    private String validateCredential(LoginActivity parent, @Nullable String method) {
        String value = null;
        if ("tel".equals(method)) {
            final PhoneEdit phone = parent.findViewById(R.id.phone);
            if (!phone.isNumberValid()) {
                phone.setError(getText(R.string.phone_number_required));
            } else {
                value = phone.getPhoneNumberE164();
            }
        } else if ("email".equals(method)) {
            value = ((EditText) parent.findViewById(R.id.email)).getText().toString().trim().toLowerCase();
            if (value.isEmpty()) {
                ((EditText) parent.findViewById(R.id.email)).setError(getString(R.string.email_required));
            }
        } else {
            Log.w(TAG, "Unknown validation method " + method);
        }
        return value;
    }

    // Email or phone number entered.
    private void clickRequest(View button) {
        final LoginActivity parent = (LoginActivity) requireActivity();

        String method = mCredMethods != null && mCredMethods.length > 0 ? mCredMethods[0] : null;
        final String value = validateCredential(parent, method);
        if (TextUtils.isEmpty(value)) {
            return;
        }

        Cache.getTinode().requestResetSecret("basic", method, value)
                .thenApply(
                        new PromisedReply.SuccessListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                                parent.runOnUiThread(() -> {
                                    if (parent.isFinishing() || parent.isDestroyed() || !isVisible()) {
                                        return;
                                    }

                                    readyToEnterCode();
                                    Toast.makeText(parent, R.string.confirmation_code_sent, Toast.LENGTH_SHORT).show();
                                });
                                return null;
                            }
                        })
                .thenCatch(
                        new PromisedReply.FailureListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onFailure(Exception err) {
                                if (parent.isFinishing() || parent.isDestroyed() || !isVisible()) {
                                    return null;
                                }

                                // Something went wrong.
                                parent.reportError(err, (Button) button, 0, R.string.invalid_or_unknown_credential);
                                return null;
                            }
                        });
    }

    // Nothing entered.
    private void clickHaveCode(View button) {
        button.setEnabled(false);
        readyToEnterCode();
    }

    // Email/phone, code, and password entered.
    private void clickConfirm(View button) {
        final LoginActivity parent = (LoginActivity) requireActivity();

        String method = mCredMethods != null && mCredMethods.length > 0 ? mCredMethods[0] : null;
        final String value = validateCredential(parent, method);
        if (TextUtils.isEmpty(value)) {
            return;
        }

        String code = ((EditText) parent.findViewById(R.id.confirmationCode)).getText().toString().trim().toLowerCase();
        TextInputLayout wrapper = parent.findViewById(R.id.codeWrapper);
        wrapper.setError(code.isEmpty() ? getString(R.string.confirmation_code_required) : null);

        String password = ((EditText) parent.findViewById(R.id.editPassword)).getText().toString().trim();
        wrapper = parent.findViewById(R.id.editPasswordWrapper);
        wrapper.setError(password.isEmpty() ? getString(R.string.password_required) : null);

        Cache.getTinode().updateAccountBasic(AuthScheme.codeInstance(code, method, value), null, password)
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        if (parent.isFinishing() || parent.isDestroyed() || !isVisible()) {
                            return null;
                        }

                        parent.runOnUiThread(() -> {
                            Toast.makeText(parent, R.string.password_changed, Toast.LENGTH_LONG).show();
                            parent.getSupportFragmentManager().popBackStack();
                        });
                        return null;
                    }
                })
                .thenCatch(new PromisedReply.FailureListener<>() {
                    @Override
                    public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) {
                        if (parent.isFinishing() || parent.isDestroyed() || !isVisible()) {
                            return null;
                        }

                        parent.reportError(err, (Button) button, 0, R.string.action_failed);
                        return null;
                    }
                });
    }

    private void readyToEnterCode() {
        final LoginActivity parent = (LoginActivity) requireActivity();

        if (parent.isFinishing() || parent.isDestroyed()) {
            return;
        }

        parent.findViewById(R.id.requestCode).setVisibility(View.GONE);
        parent.findViewById(R.id.editPasswordWrapper).setVisibility(View.VISIBLE);
        parent.findViewById(R.id.codeWrapper).setVisibility(View.VISIBLE);
        parent.findViewById(R.id.confirm).setVisibility(View.VISIBLE);
        parent.findViewById(R.id.will_send_sms).setVisibility(View.GONE);
        parent.findViewById(R.id.will_send_email).setVisibility(View.GONE);
    }
}

