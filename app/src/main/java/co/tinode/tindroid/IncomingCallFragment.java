package co.tinode.tindroid;

import android.Manifest;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import org.webrtc.SurfaceViewRenderer;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;

public class IncomingCallFragment extends Fragment implements MotionLayout.TransitionListener {
    private static final String TAG = "IncomingCallFragment";

    private PreviewView mLocalCameraView;

    private ComTopic<VxCard> mTopic;

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

    public IncomingCallFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        MotionLayout v = (MotionLayout) inflater.inflate(R.layout.fragment_incoming_call, container, false);
        v.addTransitionListener(this);
        mLocalCameraView = v.findViewById(R.id.cameraPreviewView);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final Bundle args = getArguments();
        //if (args == null || activity == null) {
            // Reject the call.
            // handleCallClose();
            // return;
        // }
        /*
        String name = args.getString("topic");
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

        // Check permissions.
        LinkedList<String> missing = UiUtils.getMissingPermissions(activity,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        if (!missing.isEmpty()) {
            mMediaPermissionLauncher.launch(missing.toArray(new String[]{}));
            return;
        }

        startCamera();
    }

    @Override
    public void onTransitionStarted(MotionLayout motionLayout, int startId, int endId) {
        // Do nothing.
        Log.i(TAG, "onTransitionStarted");
    }

    @Override
    public void onTransitionChange(MotionLayout motionLayout, int startId, int endId, float progress) {
        // Do nothing.
    }

    @Override
    public void onTransitionCompleted(MotionLayout motionLayout, int currentId) {
        if  (currentId == R.id.answerActivated) {
            Log.i(TAG, "Answered");
        } else if (currentId == R.id.hangUpActivated) {
            Log.i(TAG, "Declined");
        } else {
            Log.i(TAG,"Unknown");
        }
    }

    @Override
    public void onTransitionTrigger(MotionLayout motionLayout, int triggerId, boolean positive, float progress) {
        // Do nothing.
        Log.i(TAG, "onTransitionTrigger");
    }

    private void startCamera() {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null ||activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
        cameraProviderFuture.addListener(() -> {
            mLocalCameraView.setVisibility(View.VISIBLE);
            // Used to bind the lifecycle of cameras to the lifecycle owner
            try {
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(mLocalCameraView.getSurfaceProvider());

                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                // Unbind use cases before rebinding.
                cameraProvider.unbindAll();
                // Bind use cases to front camera.
                cameraProvider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, preview);
            } catch (ExecutionException | InterruptedException | IllegalStateException ex) {
                Log.e(TAG, "Failed to start camera", ex);
            }
        }, ContextCompat.getMainExecutor(activity));
    }
}
