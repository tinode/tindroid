package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.AttachmentPickerDialog;
import co.tinode.tindroid.widgets.PhoneEdit;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Fragment for managing registration of a new account.
 */
public class SignUpFragment extends Fragment
        implements View.OnClickListener, UtilsMedia.MediaPreviewer, MenuProvider {

    private static final String TAG ="SignUpFragment";
    private String[] mCredMethods;

    private final ActivityResultLauncher<PickVisualMediaRequest> mRequestAvatarLauncher =
            UtilsMedia.pickMediaLauncher(this, this);

    private final ActivityResultLauncher<Void> mThumbTakePhotoLauncher =
            UtilsMedia.takePreviewPhotoLauncher(this, this);

    private final ActivityResultLauncher<String> mRequestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    mThumbTakePhotoLauncher.launch(null);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();

        ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.sign_up);
        }

        View fragment = inflater.inflate(R.layout.fragment_signup, container, false);

        // Get avatar from the gallery or photo camera.
        fragment.findViewById(R.id.uploadAvatar).setOnClickListener(v ->
                new AttachmentPickerDialog.Builder().
                    setGalleryLauncher(mRequestAvatarLauncher).
                    setCameraPreviewLauncher(mThumbTakePhotoLauncher, mRequestCameraPermissionLauncher).
                    build().
                    show(getChildFragmentManager()));
        // Handle click on the sign up button.
        fragment.findViewById(R.id.signUp).setOnClickListener(this);

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final LoginActivity parent = (LoginActivity) requireActivity();
        if (parent.isFinishing() || parent.isDestroyed()) {
            return;
        }

        parent.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        AvatarViewModel avatarVM = new ViewModelProvider(parent).get(AvatarViewModel.class);
        avatarVM.getAvatar().observe(getViewLifecycleOwner(), bmp ->
            UiUtils.acceptAvatar(parent, parent.findViewById(R.id.imageAvatar), bmp)
        );
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
                List<String> methods = UiUtils.getRequiredCredMethods(tinode, "auth");
                setupCredentials(parent, methods.toArray(new String[]{}));
                return null;
            }
        }).thenCatch(new PromisedReply.FailureListener<>() {
            @Override
            public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) {
                Log.w(TAG, "Failed to connect", err);
                parent.runOnUiThread(() -> {
                    if (parent.isFinishing() || parent.isDestroyed() || !isVisible()) {
                        return;
                    }
                    parent.findViewById(R.id.signUp).setEnabled(false);
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
            // This is called on a network thread. Make sure the activity is still alive.
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            for (final String method : mCredMethods) {
                if (TextUtils.isEmpty(method)) {
                    continue;
                }

                View field = null;
                if (method.equals("tel")) {
                    field = activity.findViewById(R.id.phone);
                } else if (method.equals("email")) {
                    field = activity.findViewById(R.id.emailWrapper);
                } else {
                    // TODO: show generic text prompt for unknown method.
                    Log.w(TAG, "Show generic validation field for " + method);
                }

                if (field != null) {
                    field.setVisibility(View.VISIBLE);
                    activity.findViewById(R.id.newLogin).requestFocus();
                }
            }
        });
    }

    /**
     * Create new account.
     */
    @Override
    public void onClick(View v) {
        final LoginActivity parent = (LoginActivity) requireActivity();
        if (parent.isFinishing() || parent.isDestroyed()) {
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

        if (mCredMethods == null) {
            mCredMethods = new String[]{"email"};
        }

        final ArrayList<Credential> credentials = new ArrayList<>();
        if (Arrays.asList(mCredMethods).contains("email")) {
            final String email = ((EditText) parent.findViewById(R.id.email)).getText().toString().trim();
            if (email.isEmpty()) {
                ((EditText) parent.findViewById(R.id.email)).setError(getText(R.string.email_required));
                return;
            } else {
                credentials.add(new Credential("email", email));
            }
        }

        if (Arrays.asList(mCredMethods).contains("tel")) {
            final PhoneEdit phone = parent.findViewById(R.id.phone);
            if (!phone.isNumberValid()) {
                phone.setError(getText(R.string.phone_number_required));
                return;
            } else {
                credentials.add(new Credential("tel", phone.getPhoneNumberE164()));
            }
        }

        String fn = ((EditText) parent.findViewById(R.id.fullName)).getText().toString().trim();
        if (fn.isEmpty()) {
            ((EditText) parent.findViewById(R.id.fullName)).setError(getText(R.string.full_name_required));
            return;
        }
        // Make sure user name is not too long.
        final String fullName;
        if (fn.length() > Const.MAX_TITLE_LENGTH) {
            fullName = fn.substring(0, Const.MAX_TITLE_LENGTH);
        } else {
            fullName = fn;
        }

        String description = ((EditText) parent.findViewById(R.id.userDescription)).getText().toString().trim();
        if (!TextUtils.isEmpty(description)) {
            if (description.length() > Const.MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, Const.MAX_DESCRIPTION_LENGTH);
            }
        } else {
            description = null;
        }

        final Button signUp = parent.findViewById(R.id.signUp);
        signUp.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        @SuppressLint("UnsafeOptInUsageError")
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
        @SuppressLint("UnsafeOptInUsageError")
        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final ImageView avatar = parent.findViewById(R.id.imageAvatar);
        final Tinode tinode = Cache.getTinode();
        final VxCard theCard = new VxCard(fullName, description);
        Drawable dr = avatar.getDrawable();
        final Bitmap bmp;
        if (dr instanceof BitmapDrawable) {
            bmp = ((BitmapDrawable) dr).getBitmap();
        } else {
            bmp = null;
        }
        // This is called on the websocket thread.
        tinode.connect(hostName, tls, false)
                .thenApply(new PromisedReply.SuccessListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) {
                                return AttachmentHandler.uploadAvatar(theCard, bmp, "newacc");
                            }
                        })
                .thenApply(new PromisedReply.SuccessListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) {
                                // Try to create a new account.
                                MetaSetDesc<VxCard, String> meta = new MetaSetDesc<>(theCard, null);
                                meta.attachments = theCard.getPhotoRefs();
                                return tinode.createAccountBasic(
                                        login, password, true, null, meta,
                                        credentials.toArray(new Credential[]{}));
                            }
                        })
                .thenApply(new PromisedReply.SuccessListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) {
                                UiUtils.updateAndroidAccount(parent, tinode.getMyId(),
                                        AuthScheme.basicInstance(login, password).toString(),
                                        tinode.getAuthToken(), tinode.getAuthTokenExpiration());

                                // Remove used avatar from the view model.
                                new ViewModelProvider(parent).get(AvatarViewModel.class).clear();

                                // Flip back to login screen on success;
                                parent.runOnUiThread(() -> {
                                    if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                        signUp.setEnabled(true);
                                        parent.showFragment(LoginActivity.FRAGMENT_CREDENTIALS, null);
                                    } else {
                                        // We are requesting immediate login with the new account.
                                        // If the action succeeded, assume we have logged in.
                                        tinode.setAutoLoginToken(tinode.getAuthToken());
                                        UiUtils.onLoginSuccess(parent, signUp, tinode.getMyId());
                                    }
                                });
                                return null;
                            }
                        })
                .thenCatch(new PromisedReply.FailureListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onFailure(Exception err) {
                                if (!SignUpFragment.this.isVisible() || parent.isFinishing() || parent.isDestroyed()) {
                                    return null;
                                }
                                parent.runOnUiThread(() -> {
                                    signUp.setEnabled(true);
                                    if (err instanceof ServerResponseException) {
                                        final String cause = ((ServerResponseException) err).getReason();
                                        if (cause != null) {
                                            switch (cause) {
                                                case "auth":
                                                    // Invalid login
                                                    ((EditText) parent.findViewById(R.id.newLogin))
                                                            .setError(getText(R.string.login_rejected));
                                                    break;
                                                case "email":
                                                    // Duplicate email:
                                                    ((EditText) parent.findViewById(R.id.email))
                                                            .setError(getText(R.string.email_rejected));
                                                    break;
                                            }
                                        }
                                    } else {
                                        Log.w(TAG, "Failed create account", err);
                                        Toast.makeText(parent, parent.getString(R.string.action_failed),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                                parent.reportError(err, signUp, 0, R.string.error_new_account_failed);
                                return null;
                            }
                        });
    }

    @Override
    public void handleMedia(Bundle args) {
        final FragmentActivity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        ((LoginActivity) activity).showFragment(LoginActivity.FRAGMENT_AVATAR_PREVIEW, args);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menu.clear();
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }
}
