package co.tinode.tindroid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import co.tinode.tindroid.services.CallService;

/**
 * Incoming call view with accept/decline buttons.
 */
public class IncomingCallActivity extends AppCompatActivity {
    public static final String INCOMING_CALL_FULL_SCREEN_CLOSE = "tindroidx.incoming_call.close";
    private static final String TAG = "IncomingCallActivity";
    private boolean mTurnScreenOffWhenDone;

    // Receives close requests from CallService (e.g. upon remote hang-up).
    LocalBroadcastManager mLocalBroadcastManager;
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (INCOMING_CALL_FULL_SCREEN_CLOSE.equals(intent.getAction())){
                finish();
            }
        }
    };

    // Handles accept or decline button click.
    private void scheduleActionAndClose(String action) {
        Intent intent = new Intent(IncomingCallActivity.this, CallService.class);
        intent.setAction(action);
        Intent originalIntent = getIntent();
        if (originalIntent != null) {
            intent.putExtra("topic", originalIntent.getStringExtra("topic"));
            intent.putExtra("seq", originalIntent.getIntExtra("seq", -1));
            startService(intent);
        } else {
            Log.e(TAG, "No original intent used to start the activity. Closing.");
        }
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(INCOMING_CALL_FULL_SCREEN_CLOSE);
        mLocalBroadcastManager.registerReceiver(mBroadcastReceiver, mIntentFilter);

        final Button acceptButton = findViewById(R.id.btnAccept);
        acceptButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                scheduleActionAndClose("accept");
            }
        });

        final Button declineButton = findViewById(R.id.btnDecline);
        declineButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                scheduleActionAndClose("decline");
            }
        });

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOff = !pm.isInteractive();

        mTurnScreenOffWhenDone = isScreenOff;
        if (isScreenOff) {
            // Turn screen on and unlock.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            } else {
                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                );
            }

            KeyguardManager mgr = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mgr.requestDismissKeyguard(this, null);
            }
        }
    }

    @Override
    public void onDestroy() {
        mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
        if (mTurnScreenOffWhenDone) {
            Log.d(TAG, "Turning screen off.");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(false);
                setTurnScreenOn(false);
            } else {
                getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                );
            }
        }
        super.onDestroy();
    }
}