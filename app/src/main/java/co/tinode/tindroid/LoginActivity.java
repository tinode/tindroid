package co.tinode.tindroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import co.tinode.tinodesdk.Connection;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.ServerMessage;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    NetworkService mNetwork;
    boolean mBound = false;

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService.
        bindService(new Intent(getApplicationContext(), NetworkService.class),
                mConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //getSupportActionBar().hide();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onLogin(View v) {
        final String login = ((EditText) findViewById(R.id.editLogin)).getText().toString();
        final String password = ((EditText) findViewById(R.id.editPassword)).getText().toString();

        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                try {
                    mNetwork.getTinode().connect()
                            .thenApply(
                                    new PromisedReply.SuccessListener<ServerMessage>() {
                                        @Override
                                        public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                            return mNetwork.getTinode().loginBasic(
                                                    login,
                                                    password);
                                        }
                                    },
                                    null)
                            .thenApply(
                                    new PromisedReply.SuccessListener<ServerMessage>() {
                                        @Override
                                        public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                            Intent intent = new Intent(getApplicationContext(), ContactsActivity.class);
                                            startActivity(intent);
                                            return null;
                                        }
                                    },
                                    new PromisedReply.FailureListener<ServerMessage>() {
                                        @Override
                                        public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                                            Log.i(TAG, "connection failed :( " + err.getMessage());
                                            Toast.makeText(getApplicationContext(),
                                                    "Failed to login", Toast.LENGTH_LONG).show();
                                            return null;
                                        }
                                    });
                } catch (Exception e) {
                    Log.e(TAG, "Something went wrong", e);
                }
                return null;
            }
        }.execute();
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            NetworkService.LocalBinder binder = (NetworkService.LocalBinder) service;
            mNetwork = binder.getService();
            mBound = true;
            Log.d(TAG, "Activity bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            Log.d(TAG, "Activity unbound");
        }
    };
}
