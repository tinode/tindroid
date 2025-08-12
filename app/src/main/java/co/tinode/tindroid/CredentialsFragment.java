package co.tinode.tindroid;

import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class CredentialsFragment extends Fragment implements MenuProvider, View.OnClickListener {
    private String mMethod = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final LoginActivity parent = (LoginActivity) requireActivity();

        ActionBar bar = parent.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        View fragment = inflater.inflate(R.layout.fragment_validate, container, false);
        fragment.findViewById(R.id.confirm).setOnClickListener(this);
        fragment.findViewById(R.id.cancel).setOnClickListener(v ->
                parent.showFragment(LoginActivity.FRAGMENT_LOGIN, null));

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        super.onResume();

        LoginActivity parent = (LoginActivity) requireActivity();
        View fragment = getView();
        if (fragment == null) {
            return;
        }

        // Get the first credential to be validated.
        mMethod = BaseDb.getInstance().getFirstValidationMethod();
        // TODO: convert method like 'tel' or 'email' to localazable human-readable string.
        // use cred_methods string-array from resource.
        if (TextUtils.isEmpty(mMethod)) {
            parent.showFragment(LoginActivity.FRAGMENT_LOGIN, null);
        } else {
            TextView callToAction = fragment.findViewById(R.id.call_to_validate);
            callToAction.setText(getString(R.string.enter_confirmation_code));
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menu.clear();
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }

    @Override
    public void onClick(View view) {
        final LoginActivity parent = (LoginActivity) requireActivity();

        final Tinode tinode = Cache.getTinode();
        String token = tinode.getAuthToken();
        if (TextUtils.isEmpty(token)) {
            parent.showFragment(LoginActivity.FRAGMENT_LOGIN, null);
            return;
        }

        final String code = ((EditText) parent.findViewById(R.id.response)).getText().toString().trim();
        if (code.isEmpty()) {
            ((EditText) parent.findViewById(R.id.response)).setError(getText(R.string.enter_confirmation_code));
            return;
        }

        final Button confirm = parent.findViewById(R.id.confirm);
        confirm.setEnabled(false);

        Credential[] cred = new Credential[1];
        cred[0] = new Credential(mMethod, null, code, null);

        tinode.loginToken(token, cred).thenApply(
                new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                        if (parent.isFinishing() || parent.isDestroyed()) {
                            return null;
                        }
                        if (msg.ctrl.code >= 300) {
                            // Credential still unconfirmed.
                            parent.reportError(null, confirm, R.id.response, R.string.invalid_confirmation_code);
                        } else {
                            // Login succeeded.
                            tinode.setAutoLoginToken(tinode.getAuthToken());
                            UiUtils.onLoginSuccess(parent, confirm, tinode.getMyId());
                        }
                        return null;
                    }
                },
                new PromisedReply.FailureListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        if (parent.isFinishing() || parent.isDestroyed()) {
                            return null;
                        }
                        parent.reportError(err, confirm, 0, R.string.failed_credential_confirmation);
                        // Something went wrong like a duplicate credential or expired token.
                        // Go back to login, nothing we can do here.
                        parent.showFragment(LoginActivity.FRAGMENT_LOGIN, null);
                        return null;
                    }
                });

    }
}
