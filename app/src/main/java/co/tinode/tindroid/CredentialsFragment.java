package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class CredentialsFragment extends Fragment implements View.OnClickListener{
    private static final String TAG = "CredentialsFragment";

    private static final String ARG_KEY = "method";

    public CredentialsFragment() {
    }

    public void setMethod(String method) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, method);
        setArguments(args);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        View fragment = inflater.inflate(R.layout.fragment_validate, container, false);
        fragment.findViewById(R.id.confirm).setOnClickListener(this);
        fragment.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction trx = getActivity().getSupportFragmentManager().beginTransaction();
                trx.replace(R.id.contentFragment, new LoginFragment());
                trx.commit();
            }
        });

        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle unused) {
        super.onActivityCreated(unused);

        Bundle args = getArguments();
        String method = args.getString(ARG_KEY);
        TextView callToAction = getActivity().findViewById(R.id.call_to_validate);
        callToAction.setText(getString(R.string.validate_cred, method));
    }

    @Override
    public void onClick(View view) {
        final LoginActivity parent = (LoginActivity) getActivity();

        final Tinode tinode = Cache.getTinode();
        String token = tinode.getAuthToken();
        if (TextUtils.isEmpty(token)) {
            FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
            trx.replace(R.id.contentFragment, new LoginFragment());
            trx.commit();
            return;
        }

        final String code = ((EditText) parent.findViewById(R.id.response)).getText().toString().trim();
        if (code.isEmpty()) {
            ((EditText) parent.findViewById(R.id.response)).setError(getText(R.string.enter_confirmation_code));
            return;
        }

        final Button confirm = parent.findViewById(R.id.confirm);
        confirm.setEnabled(false);

        try {
            Bundle args = this.getArguments();
            String method = args.getString(ARG_KEY);

            Credential[] cred = new Credential[1];
            cred[0] = new Credential(method, null, code, null);

            tinode.loginToken(token, cred).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                        if (msg.ctrl.code >= 300) {
                            // Credential still unconfirmed.
                            parent.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    confirm.setEnabled(true);
                                    ((EditText) parent.findViewById(R.id.response))
                                            .setError(getText(R.string.invalid_confirmation_code));
                                }
                            });
                        } else {
                            // Login succeeded.
                            UiUtils.onLoginSuccess(parent, confirm);
                        }
                        return null;
                    }
                },
                new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        parent.reportError(err, confirm, R.string.failed_credential_confirmation);
                        // Something went wrong like a duplicate credential or expired token.
                        // Go back to login, nothing we can do here.
                        FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
                        trx.replace(R.id.contentFragment, new LoginFragment());
                        trx.commit();
                        return null;
                    }
                });

        } catch (Exception e) {
            Log.e(TAG, "Something went wrong", e);
            confirm.setEnabled(true);
        }
    }
}
