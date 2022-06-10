package co.tinode.tindroid;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.services.CallService;
import co.tinode.tinodesdk.ComTopic;

/**
 * Incoming call view with accept/decline buttons.
 */
public class IncomingCallActivity extends AppCompatActivity implements MotionLayout.TransitionListener {
    private static final String TAG = "IncomingCallActivity";

    public static final String INTENT_ACTION_CALL_INCOMING = "tindroidx.intent.action.call.INCOMING";
    public static final String INTENT_ACTION_CALL_DECLINE = "tindroidx.intent.action.call.DECLINE";
    public static final String INTENT_ACTION_CALL_ACCEPT = "tindroidx.intent.action.call.ACCEPT";
    public static final String INTENT_ACTION_CALL_CLOSE = "tindroidx.intent.action.call.CLOSE";

    private boolean mTurnScreenOffWhenDone;

    private PreviewView mLocalCameraView;
    private MediaPlayer mMediaPlayer;
    private ProcessCameraProvider mCamera;

    private String mTopicName;
    private int mSeq;
    private ComTopic<VxCard> mTopic;

    /*
    // Receives close requests from CallService (e.g. upon remote hang-up).
    LocalBroadcastManager mLocalBroadcastManager;
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (INTENT_FILTER_CALL_CLOSE.equals(intent.getAction())){
                finish();
            }
        }
    };
    */

    // Check if we have camera and mic permissions.
    private final ActivityResultLauncher<String[]> mMediaPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    if (!e.getValue()) {
                        // handleCallClose();
                        return;
                    }
                }
                // All permissions granted.
                // this.startMediaAndSignal();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        if (!INTENT_ACTION_CALL_INCOMING.equals(intent.getAction())) {
            finish();
            return;
        }

        mTopicName = intent.getStringExtra("topic");
        mSeq = intent.getIntExtra("seq", -1);
        /*
        //noinspection unchecked
        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);

        VxCard pub = mTopic.getPub();
        UiUtils.setAvatar(view.findViewById(R.id.imageAvatar), pub, name, false);
        String peerName = pub != null ? pub.fn : null;
        if (TextUtils.isEmpty(peerName)) {
            peerName = getResources().getString(R.string.unknown);
        }
        ((TextView) view.findViewById(R.id.peerName)).setText(peerName);
         */

        setContentView(R.layout.activity_incoming_call);

        mLocalCameraView = findViewById(R.id.cameraPreviewView);

        /*
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(INTENT_FILTER_CALL_CLOSE);
        mLocalBroadcastManager.registerReceiver(mBroadcastReceiver, mIntentFilter);
        */

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

        // Check permissions.
        LinkedList<String> missing = UiUtils.getMissingPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        if (!missing.isEmpty()) {
            mMediaPermissionLauncher.launch(missing.toArray(new String[]{}));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMediaPlayer = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        mMediaPlayer.start();
        startCamera();
    }

    @Override
    public void onPause() {
        mCamera.unbindAll();
        mMediaPlayer.stop();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        // mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
        if (mTurnScreenOffWhenDone) {
            Log.d(TAG, "Turning screen off.");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(false);
                setTurnScreenOn(false);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
            }
        }
        super.onDestroy();
    }

    @Override
    public void onTransitionStarted(MotionLayout motionLayout, int startId, int endId) {
        // Do nothing.
    }

    @Override
    public void onTransitionChange(MotionLayout motionLayout, int startId, int endId, float progress) {
        // Do nothing.
    }

    @Override
    public void onTransitionCompleted(MotionLayout motionLayout, int currentId) {
        if  (currentId == R.id.answerActivated) {
            Log.i(TAG, "Answered");
            scheduleActionAndClose(INTENT_ACTION_CALL_ACCEPT);
        } else if (currentId == R.id.hangUpActivated) {
            Log.i(TAG, "Declined");
            scheduleActionAndClose(INTENT_ACTION_CALL_DECLINE);
        } else {
            Log.i(TAG,"Unknown");
        }
    }

    @Override
    public void onTransitionTrigger(MotionLayout motionLayout, int triggerId, boolean positive, float progress) {
        // Do nothing.
    }

    // Handles accept or decline button click.
    private void scheduleActionAndClose(String action) {
        Intent intent = new Intent(IncomingCallActivity.this, CallService.class);
        intent.setAction(action);
        intent.putExtra("topic", mTopicName);
        intent.putExtra("seq", mSeq);
        startService(intent);
        finish();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            mLocalCameraView.setVisibility(View.VISIBLE);
            // Used to bind the lifecycle of cameras to the lifecycle owner
            try {
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(mLocalCameraView.getSurfaceProvider());

                mCamera = cameraProviderFuture.get();
                // Unbind use cases before rebinding.
                mCamera.unbindAll();
                // Bind use cases to front camera.
                mCamera.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview);
            } catch (ExecutionException | InterruptedException | IllegalStateException ex) {
                Log.e(TAG, "Failed to start camera", ex);
            }
        }, ContextCompat.getMainExecutor(this));
    }
}