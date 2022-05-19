package co.tinode.tindroid;

import androidx.appcompat.app.AppCompatActivity;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
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
    private static final String TAG = "IncomingCallActivity";
    private boolean mTurnScreenOffWhenDone;

    // Handles accept or decline button click.
    private void scheduleActionAndClose(String action) {
        Intent intent = new Intent(IncomingCallActivity.this, CallService.class);
        intent.setAction(action);
        Intent originalIntent = getIntent();
        intent.putExtra("topic", originalIntent.getStringExtra("topic"));
        intent.putExtra("seq", originalIntent.getIntExtra("seq", -1));

        startService(intent);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

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