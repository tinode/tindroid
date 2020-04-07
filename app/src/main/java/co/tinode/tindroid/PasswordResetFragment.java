package co.tinode.tindroid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.ServerMessage;

public class PasswordResetFragment extends Fragment  implements View.OnClickListener{
    private static final String TAG = "PasswordResetFragment";

    private static final String ARG_KEY = "method";

    public PasswordResetFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);
        final LoginActivity parent = (LoginActivity) getActivity();
        if (parent == null) {
            return null;
        }

        ActionBar bar = parent.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.request_pass_reset_title);
        }

        View fragment = inflater.inflate(R.layout.fragment_pass_reset, container, false);
        fragment.findViewById(R.id.confirm).setOnClickListener(this);
        fragment.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parent.showFragment(LoginActivity.FRAGMENT_LOGIN);
            }
        });

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        Bundle args = getArguments();
        String method = args != null ? args.getString(ARG_KEY) : "email";
        TextView text = view.findViewById(R.id.callToReset);
        text.setText(getString(R.string.request_pass_reset, method));
        TextInputLayout hint = view.findViewById(R.id.responseHint);
        hint.setHint(getString(R.string.validated_address_to_use, method));
    }

    @Override
    public void onClick(View view) {
        final LoginActivity parent = (LoginActivity) getActivity();
        if (parent == null) {
            return;
        }

        Bundle args = getArguments();
        final String method = args != null ? args.getString(ARG_KEY) : "email";

        final String value = ((EditText) parent.findViewById(R.id.response)).getText().toString().trim().toLowerCase();
        if (value.isEmpty()) {
            ((EditText) parent.findViewById(R.id.response)).setError(getString(R.string.enter_valid_address, method));
            return;
        }

        final Button confirm = parent.findViewById(R.id.confirm);
        confirm.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        final String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName(parent));
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final Tinode tinode = Cache.getTinode();
        Cache.getTinode().connect(hostName, tls)
                .thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            return tinode.requestResetSecret("basic", method, value);
                        }
                    })
                .thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                            parent.reportError(null, confirm, 0, R.string.password_reset_message_sent);
                            parent.showFragment(LoginActivity.FRAGMENT_LOGIN);
                            return null;
                        }
                    })
                .thenCatch(
                    new PromisedReply.FailureListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onFailure(Exception err) {
                            // Something went wrong.
                            parent.reportError(err, confirm, R.id.response, R.string.invalid_or_unknown_credential);
                            return null;
                        }
                    });
    }
}

