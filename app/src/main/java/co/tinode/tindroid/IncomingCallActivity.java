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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.MsgServerInfo;

/**
 * Incoming call view with accept/decline buttons.
 */
public class IncomingCallActivity extends AppCompatActivity
        implements MotionLayout.TransitionListener {
    private static final String TAG = "IncomingCallActivity";

    // Default call timeout in seconds.
    private static final long DEFAULT_CALL_TIMEOUT = 30;

    public static final String INTENT_ACTION_CALL_INCOMING = "tindroidx.intent.action.call.INCOMING";
    public static final String INTENT_ACTION_CALL_ACCEPT = "tindroidx.intent.action.call.ACCEPT";
    public static final String INTENT_ACTION_CALL_CLOSE = "tindroidx.intent.action.call.CLOSE";

    private boolean mTurnScreenOffWhenDone;

    private PreviewView mLocalCameraView;
    private MediaPlayer mMediaPlayer;
    private ProcessCameraProvider mCamera;

    private Tinode mTinode;
    private InfoEventListener mListener;

    private String mTopicName;
    private int mSeq;
    private ComTopic<VxCard> mTopic;

    private Timer mTimer;

    // Receives close requests from CallService (e.g. upon remote hang-up).
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (INTENT_ACTION_CALL_CLOSE.equals(intent.getAction())) {
                String topicName = intent.getStringExtra("topic");
                int seq = intent.getIntExtra("seq", -1);
                if (mTopicName.equals(topicName) && mSeq == seq) {
                    finish();
                } else {
                    Log.d(TAG, "Close intent dismissed: topic '" + topicName + "'!='" + mTopicName +
                            "' or seq " + mSeq + "!=" + seq);
                }
            }
        }
    };

    // Check if we have camera and mic permissions.
    private final ActivityResultLauncher<String[]> mMediaPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    if (!e.getValue()) {
                        declineCall();
                        return;
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Created!");

        final Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        if (!INTENT_ACTION_CALL_INCOMING.equals(intent.getAction())) {
            Log.w(TAG, "Unknown intent action '" + intent.getAction() + "'");
            finish();
            return;
        }

        setContentView(R.layout.activity_incoming_call);
        ((MotionLayout) findViewById(R.id.incomingCallMainLayout)).setTransitionListener(this);

        mTinode = Cache.getTinode();
        mListener = new InfoEventListener();
        mTinode.addListener(mListener);

        mTopicName = intent.getStringExtra("topic");
        // Technically the call is from intent.getStringExtra("from")
        // but it's the same as "topic" for p2p topics;
        mSeq = intent.getIntExtra("seq", -1);

        //noinspection unchecked
        mTopic = (ComTopic<VxCard>) mTinode.getTopic(mTopicName);

        VxCard pub = mTopic.getPub();
        UiUtils.setAvatar(findViewById(R.id.imageAvatar), pub, mTopicName, false);
        String peerName = pub != null ? pub.fn : null;
        if (TextUtils.isEmpty(peerName)) {
            peerName = getResources().getString(R.string.unknown);
        }
        ((TextView) findViewById(R.id.peerName)).setText(peerName);

        mLocalCameraView = findViewById(R.id.cameraPreviewView);

        // Handle external request to close activity.
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(INTENT_ACTION_CALL_CLOSE);
        lbm.registerReceiver(mBroadcastReceiver, mIntentFilter);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOff = !pm.isInteractive();

        mTurnScreenOffWhenDone = isScreenOff;
        if (isScreenOff) {
            Log.i(TAG, "Turning screen ON.");
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
        } else {
            Log.i(TAG, "Screen is already ON");
        }

        long timeout = mTinode.getServerLimit("callTimeout", DEFAULT_CALL_TIMEOUT) * 1_000;
        Log.i(TAG, "Call timeout: " + timeout);
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                declineCall();
            }
        }, timeout + 5_000);

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
        if (mCamera != null) {
            mCamera.unbindAll();
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mTinode.removeListener(mListener);
        mTimer.cancel();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);

        // mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
        if (mTurnScreenOffWhenDone) {
            Log.i(TAG, "Turning screen off.");
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
            acceptCall();
        } else if (currentId == R.id.hangUpActivated) {
            declineCall();
        } else {
            Log.i(TAG,"Unknown transition (normal?)");
        }
    }

    @Override
    public void onTransitionTrigger(MotionLayout motionLayout, int triggerId, boolean positive, float progress) {
        // Do nothing.
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

    private void declineCall() {
        // Send message to server that the call is declined.
        Log.d(TAG, "Call declined: " + mTopicName + ":" + mSeq);
        if (mTopic != null) {
            mTopic.videoCall("hang-up", mSeq, null);
        }
        finish();
    }

    private void acceptCall() {
        // Open MessageActivity with CallFragment activated.

        Log.d(TAG, "Call accepted: " + mTopicName + ":" + mSeq);

        Intent intent = new Intent(this, MessageActivity.class);
        intent.setAction(INTENT_ACTION_CALL_ACCEPT);
        intent.putExtra("topic", mTopicName);
        intent.putExtra("seq", mSeq);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private class InfoEventListener extends Tinode.EventListener {
        @Override
        public void onInfoMessage(MsgServerInfo info) {
            if (mTopicName.equals(info.topic) && mSeq == info.seq) {
                if ("call".equals(info.what) && "hang-up".equals(info.event)) {
                    Log.d(TAG, "Remote hangup: " + info.topic + ":" + info.seq);
                    finish();
                }
            }
        }
    }
}