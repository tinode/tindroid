package co.tinode.tindroid;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.camera.view.PreviewView;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import co.tinode.tindroid.widgets.QRCodeScanner;
import io.nayuki.qrcodegen.QrCode;

public class AutoConfigFragment extends Fragment {
    private static final String TAG = "AutoConfigFragment";
    private static final String URI_PREFIX = "tinode:host/";

    private QRCodeScanner mQrScanner = null;

    private PreviewView mCameraPreview;

    private final ActivityResultLauncher<String> mRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Check if permission is granted.
                if (isGranted) {
                    mQrScanner.startCamera(mCameraPreview);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auto_configure, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        view.findViewById(R.id.confirm).setOnClickListener(confirm -> {
            TextView editor = activity.findViewById(R.id.editId);
            if (editor != null) {
                String id = editor.getText().toString();
                if (TextUtils.isEmpty(id)) {
                    editor.setError(getString(R.string.code_required));
                } else {
                    configIDReceived(id);
                }
            }
        });

        mCameraPreview = view.findViewById(R.id.cameraPreviewView);
        if (mQrScanner == null) {
            mQrScanner = new QRCodeScanner(activity, URI_PREFIX, this::configIDReceived);
        }

        if (!UiUtils.isPermissionGranted(activity, Manifest.permission.CAMERA)) {
            mRequestPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        mQrScanner.startCamera(mCameraPreview);
    }

    private void configIDReceived(String id) {
        Log.i(TAG, "ID: " + id);
    }
}
