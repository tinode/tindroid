package co.tinode.tindroid;


import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.ServerMessage;

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
        final String login = ((EditText) findViewById(R.id.editLogin)).getText().toString();
        final String password = ((EditText) findViewById(R.id.editPassword)).getText().toString();

        final Button signIn = (Button) findViewById(R.id.singnIn);
        signIn.setEnabled(false);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String hostName = sharedPref.getString("pref_hostName", "");

        try {
            // This is called on websocket thread.
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
                                    final String message = err.getMessage();
                                    Log.i(TAG, "connection failed :( " + err.getMessage());
                                    LoginActivity.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            signIn.setEnabled(true);
                                            Toast.makeText(getApplicationContext(),
                                                    "Login failed: " + message, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    return null;
                                }
                            });
        } catch (Exception e) {
            Log.e(TAG, "Something went wrong", e);
            signIn.setEnabled(true);
        }
    }


    public void onSignUp(View v) {
        Toast.makeText(getApplicationContext(), "Not implemented", Toast.LENGTH_SHORT).show();
    }

    public void onFacebookUp(View v) {
        Toast.makeText(getApplicationContext(), "Facebook: not implemented", Toast.LENGTH_SHORT).show();
    }

    public void onGoogleUp(View v) {
        Toast.makeText(getApplicationContext(), "Google: Not implemented", Toast.LENGTH_SHORT).show();
    }
}
