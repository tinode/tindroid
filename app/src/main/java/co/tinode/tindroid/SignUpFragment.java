package co.tinode.tindroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
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

import java.io.IOException;

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.MetaSetDesc;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for managing registration of a new account.
 */
public class SignUpFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "SignUpFragment";
    private static final int SELECT_PICTURE = 1;
    private static final int BITMAP_SIZE = 128;

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
        getActivity().findViewById(R.id.upload_avatar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadAvatar();
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

        final String login = ((EditText) parent.findViewById(R.id.newLogin)).getText().toString().trim();
        if (login.isEmpty()) {
            ((EditText) parent.findViewById(R.id.newLogin)).setError(getText(R.string.login_required));
            return;
        }
        final String password = ((EditText) parent.findViewById(R.id.newPassword)).getText().toString().trim();
        if (password.isEmpty()) {
            ((EditText) parent.findViewById(R.id.newPassword)).setError(getText(R.string.password_required));
            return;
        }

        String password2 = ((EditText) parent.findViewById(R.id.repeatPassword)).getText().toString();
        // Check if passwords match. If not, report error.
        if (!password.equals(password2)) {
            ((EditText) parent.findViewById(R.id.repeatPassword)).setError(getText(R.string.passwords_dont_match));
            Toast.makeText(parent, getText(R.string.passwords_dont_match), Toast.LENGTH_SHORT).show();
            return;
        }

        final Button signUp = (Button) parent.findViewById(R.id.signUp);
        signUp.setEnabled(false);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
        final String fullName = ((EditText) parent.findViewById(R.id.fullName)).getText().toString().trim();
        final ImageView avatar = (ImageView) parent.findViewById(R.id.imageAvatar);
        final Tinode tinode = Cache.getTinode();
        try {
            // This is called on the websocket thread.
            tinode.connect(hostName)
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
                                    VCard vcard = new VCard(fullName, bmp);
                                    return tinode.createAccountBasic(
                                            login, password, true,
                                            new MetaSetDesc<VCard,String>(vcard, null));
                                }
                            }, null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    // Flip back to login screen on success;
                                    parent.runOnUiThread(new Runnable() {
                                        public void run() {
                                            signUp.setEnabled(true);
                                            FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
                                            trx.replace(R.id.contentFragment, new LoginFragment());
                                            trx.commit();
                                        }
                                    });
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                                    parent.reportError(err, signUp, R.string.error_new_account_failed);
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

    private void uploadAvatar() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                SELECT_PICTURE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_PICTURE && resultCode == RESULT_OK) {
            ImageView avatar = (ImageView) getActivity().findViewById(R.id.imageAvatar);
            try {
                Bitmap bmp = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(),
                        data.getData());
                int width = bmp.getWidth();
                int height = bmp.getHeight();
                if (width > height) {
                    width = width * BITMAP_SIZE / height;
                    height = BITMAP_SIZE;
                    // Sanity check
                    width = width > 1024 ? 1024 : width;
                } else {
                    height = height * BITMAP_SIZE / width;
                    width = BITMAP_SIZE;
                    height = height > 1024 ? 1024 : height;
                }
                // Scale down.
                bmp = Bitmap.createScaledBitmap(bmp, width, height, true);
                // Chop the square from the middle.
                bmp = Bitmap.createBitmap(bmp, width - BITMAP_SIZE, height - BITMAP_SIZE,
                        BITMAP_SIZE, BITMAP_SIZE);
                avatar.setImageBitmap(bmp);
            } catch (IOException ex) {
                Toast.makeText(getActivity(), getString(R.string.image_is_missing),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
