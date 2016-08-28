package co.tinode.tindroid;


import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.SetDesc;

/**
 * LoginActivity is a FrameLayout which switches between three fragments:
 *  - LoginFragment
 *  - NewAccountFragment
 *  - LoginSettingsFragment
 */

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        PreferenceManager.setDefaultValues(this, R.xml.login_preferences, false);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Handle clicks on the <- arrow
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
                trx.replace(R.id.contentFragment, new LoginFragment());
                trx.commit();
            }
        });

        if (InmemoryCache.getTinode().isAuthenticated()) {
            startActivity(new Intent(getApplicationContext(), ContactsActivity.class));
            finish();
        } else {
            FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
            trx.replace(R.id.contentFragment, new LoginFragment());
            trx.commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
            trx.replace(R.id.contentFragment, new LoginSettingsFragment());
            trx.commit();
            return true;
        } else if (id == R.id.action_signup) {
            FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
            trx.replace(R.id.contentFragment, new NewAccountFragment());
            trx.commit();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onLogin(View v) {
        final String login = ((EditText) findViewById(R.id.editLogin)).getText().toString().trim();
        if (login.isEmpty()) {
            ((EditText) findViewById(R.id.editLogin)).setError(getText(R.string.login_required));
            return;
        }
        final String password = ((EditText) findViewById(R.id.editPassword)).getText().toString().trim();
        if (password.isEmpty()) {
            ((EditText) findViewById(R.id.editLogin)).setError(getText(R.string.password_required));
            return;
        }
        final Button signIn = (Button) findViewById(R.id.singnIn);
        signIn.setEnabled(false);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String hostName = sharedPref.getString("pref_hostName", "");

        try {
            // This is called on the websocket thread.
            InmemoryCache.getTinode().connect(hostName)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    return InmemoryCache.getTinode().loginBasic(
                                            login,
                                            password);
                                }
                            },
                            null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {

                                    LoginActivity.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            signIn.setEnabled(true);
                                        }
                                    });
                                    startActivity(new Intent(getApplicationContext(),
                                            ContactsActivity.class));
                                    finish();
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                                    reportError(err, signIn, R.string.error_login_failed);
                                    return null;
                                }
                            });
        } catch (Exception e) {
            Log.e(TAG, "Something went wrong", e);
            signIn.setEnabled(true);
        }
    }

    private void reportError(Exception err, final Button button, final int errId) {
        final String message = err.getMessage();
        Log.i(TAG, "connection failed :( " + message);
        LoginActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                button.setEnabled(true);
                Toast.makeText(getApplicationContext(),
                        getText(errId) + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Create new account
     * @param v button pressed
     */
    public void onSignUp(View v) {
        final String login = ((EditText) findViewById(R.id.newLogin)).getText().toString().trim();
        if (login.isEmpty()) {
            ((EditText) findViewById(R.id.newLogin)).setError(getText(R.string.login_required));
            return;
        }
        final String password = ((EditText) findViewById(R.id.newPassword)).getText().toString().trim();
        if (password.isEmpty()) {
            ((EditText) findViewById(R.id.newPassword)).setError(getText(R.string.password_required));
            return;
        }

        String password2 = ((EditText) findViewById(R.id.repeatPassword)).getText().toString();
        // Check if passwords match. If not, report error.
        if (!password.equals(password2)) {
            ((EditText) findViewById(R.id.repeatPassword)).setError(getText(R.string.passwords_dont_match));
            Toast.makeText(this, getText(R.string.passwords_dont_match), Toast.LENGTH_SHORT).show();
            return;
        }

        final Button signUp = (Button) findViewById(R.id.singnUp);
        signUp.setEnabled(false);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String hostName = sharedPref.getString("pref_hostName", "");
        final String fullName = ((EditText) findViewById(R.id.fullName)).getText().toString().trim();
        final ImageView avatar = (ImageView) findViewById(R.id.imageAvatar);
        try {
            // This is called on the websocket thread.
            InmemoryCache.getTinode().connect(hostName)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) throws Exception {
                                    // Try to create a new account.
                                    Bitmap bmp = null;
                                    try {
                                        bmp = ((BitmapDrawable)avatar.getDrawable()).getBitmap();
                                    } catch (ClassCastException ignored) {
                                        // If image is not loaded, the drawable is a vector.
                                        // Ignore it.
                                    }
                                    VCard vcard = new VCard(fullName, bmp);
                                    return InmemoryCache.getTinode().createAccountBasic(
                                            login, password, true,
                                            new SetDesc<VCard,String>(vcard, null));
                                }
                            }, null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    // Flip back to login screen on success;
                                    LoginActivity.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            signUp.setEnabled(true);
                                            FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
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
                                    reportError(err, signUp, R.string.error_new_account_failed);
                                    return null;
                                }
                            });

        } catch (Exception e) {
            Log.e(TAG, "Something went wrong", e);
            signUp.setEnabled(true);
        }
    }

    public void onFacebookUp(View v) {
        Toast.makeText(getApplicationContext(), "Facebook: not implemented", Toast.LENGTH_SHORT).show();
    }

    public void onGoogleUp(View v) {
        Toast.makeText(getApplicationContext(), "Google: Not implemented", Toast.LENGTH_SHORT).show();
    }
}
