package co.tinode.tindroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.Iterator;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.ServerMessage;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for managing registration of a new account.
 */
public class SignUpFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "SignUpFragment";

    public SignUpFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        View fragment = inflater.inflate(R.layout.fragment_signup, container, false);

        fragment.findViewById(R.id.signUp).setOnClickListener(this);
        fragment.findViewById(R.id.continueFb).setOnClickListener(this);
        fragment.findViewById(R.id.continueGoog).setOnClickListener(this);

        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        // Get avatar from the gallery
        // TODO(gene): add support for taking a picture
        getActivity().findViewById(R.id.uploadAvatar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.requestAvatar(SignUpFragment.this);
            }
        });
    }

    /**
     * Create new account with various methods
     *
     * @param v button pressed
     */
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.signUp:
                onSignUp();
                break;
            case R.id.continueFb:
                onFacebookUp();
                break;
            case R.id.continueGoog:
                onGoogleUp();
                break;
            default:
        }
    }

    public void onSignUp() {
        final LoginActivity parent = (LoginActivity) getActivity();
        if (parent == null) {
            return;
        }

        final String login = ((EditText) parent.findViewById(R.id.newLogin)).getText().toString().trim();
        if (login.isEmpty()) {
            ((EditText) parent.findViewById(R.id.newLogin)).setError(getText(R.string.login_required));
            return;
        }
        if (login.contains(":")) {
            ((EditText) parent.findViewById(R.id.newLogin)).setError(getText(R.string.invalid_login));
            return;
        }

        final String password = ((EditText) parent.findViewById(R.id.newPassword)).getText().toString().trim();
        if (password.isEmpty()) {
            ((EditText) parent.findViewById(R.id.newPassword)).setError(getText(R.string.password_required));
            return;
        }

        final String email = ((EditText) parent.findViewById(R.id.email)).getText().toString().trim();
        if (email.isEmpty()) {
            ((EditText) parent.findViewById(R.id.email)).setError(getText(R.string.email_required));
            return;
        }

        final Button signUp = parent.findViewById(R.id.signUp);
        signUp.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, false);
        final String fullName = ((EditText) parent.findViewById(R.id.fullName)).getText().toString().trim();
        final ImageView avatar = parent.findViewById(R.id.imageAvatar);
        final Tinode tinode = Cache.getTinode();
        try {
            // This is called on the websocket thread.
            tinode.connect(hostName, tls)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) throws Exception {
                                    // Try to create a new account.
                                    Bitmap bmp = null;
                                    try {
                                        bmp = ((BitmapDrawable) avatar.getDrawable()).getBitmap();
                                    } catch (ClassCastException ignored) {
                                        // If image is not loaded, the drawable is a vector.
                                        // Ignore it.
                                    }
                                    VxCard vcard = new VxCard(fullName, bmp);
                                    return tinode.createAccountBasic(
                                            login, password, true, null,
                                            new MetaSetDesc<VxCard,String>(vcard, null),
                                            Credential.append(null, new Credential("email", email)));
                                }
                            }, null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) {
                                    // Flip back to login screen on success;
                                    parent.runOnUiThread(new Runnable() {
                                        public void run() {
                                            FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
                                            if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                                signUp.setEnabled(true);
                                                CredentialsFragment cf = new CredentialsFragment();
                                                Iterator<String> it = msg.ctrl.getStringIteratorParam("cred");
                                                if (it != null) {
                                                    cf.setMethod(it.next());
                                                }
                                                trx.replace(R.id.contentFragment, cf);
                                            } else {
                                                // We are requesting immediate login with the new account.
                                                // If the action succeeded, assume we have logged in.
                                                UiUtils.onLoginSuccess(parent, signUp);
                                            }
                                            trx.commit();
                                        }
                                    });
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) {
                                    final String cause = ((ServerResponseException)err).getReason();
                                    if (cause != null) {
                                        parent.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                signUp.setEnabled(true);
                                                switch (cause) {
                                                    case "auth":
                                                        // Invalid login
                                                        ((EditText) parent.findViewById(R.id.newLogin)).setError(getText(R.string.login_rejected));
                                                        break;
                                                    case "email":
                                                        // Duplicate email:
                                                        ((EditText) parent.findViewById(R.id.email)).setError(getText(R.string.email_rejected));
                                                        break;
                                                }
                                            }
                                        });
                                    }
                                    parent.reportError(err, signUp, 0, R.string.error_new_account_failed);
                                    return null;
                                }
                            });

        } catch (Exception e) {
            Log.e(TAG, "Something went wrong", e);
            signUp.setEnabled(true);
        }
    }

    public void onFacebookUp() {
        Toast.makeText(getActivity(), "Facebook: not implemented", Toast.LENGTH_SHORT).show();
    }

    public void onGoogleUp() {
        Toast.makeText(getActivity(), "Google: Not implemented", Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UiUtils.SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.acceptAvatar(getActivity(), (ImageView) getActivity().findViewById(R.id.imageAvatar), data);
        }
    }
}
