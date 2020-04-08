package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.ServerMessage;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for managing registration of a new account.
 */
public class SignUpFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "SignUpFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return null;
        }

        ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.sign_up);
        }

        View fragment = inflater.inflate(R.layout.fragment_signup, container, false);

        fragment.findViewById(R.id.signUp).setOnClickListener(this);

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Get avatar from the gallery
        // TODO(gene): add support for taking a picture
        view.findViewById(R.id.uploadAvatar).setOnClickListener(new View.OnClickListener() {
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
    @Override
    public void onClick(View v) {
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

        String fn = ((EditText) parent.findViewById(R.id.fullName)).getText().toString().trim();
        if (fn.isEmpty()) {
            ((EditText) parent.findViewById(R.id.fullName)).setError(getText(R.string.full_name_required));
            return;
        }
        // Make sure user name is not too long.
        final String fullName;
        if (fn.length() > UiUtils.MAX_TITLE_LENGTH) {
            fullName = fn.substring(0, UiUtils.MAX_TITLE_LENGTH);
        } else {
            fullName = fn;
        }

        final Button signUp = parent.findViewById(R.id.signUp);
        signUp.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName(parent));
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final ImageView avatar = parent.findViewById(R.id.imageAvatar);
        final Tinode tinode = Cache.getTinode();
        // This is called on the websocket thread.
        tinode.connect(hostName, tls)
                .thenApply(
                        new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) {
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
                        })
                .thenApply(
                        new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) {
                                UiUtils.updateAndroidAccount(parent, tinode.getMyId(),
                                        AuthScheme.basicInstance(login, password).toString(),
                                        tinode.getAuthToken(), tinode.getAuthTokenExpiration());

                                // Flip back to login screen on success;
                                parent.runOnUiThread(new Runnable() {
                                    public void run() {
                                        if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                            signUp.setEnabled(true);
                                            parent.showFragment(LoginActivity.FRAGMENT_CREDENTIALS);
                                        } else {
                                            // We are requesting immediate login with the new account.
                                            // If the action succeeded, assume we have logged in.
                                            tinode.setAutoLoginToken(tinode.getAuthToken());
                                            UiUtils.onLoginSuccess(parent, signUp, tinode.getMyId());
                                        }
                                    }
                                });
                                return null;
                            }
                        })
                .thenCatch(
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
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (requestCode == UiUtils.ACTIVITY_RESULT_SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.acceptAvatar(activity, (ImageView) activity.findViewById(R.id.imageAvatar), data);
        }
    }
}
