package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.util.concurrent.Executors;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;
import co.tinode.tindroid.widgets.CircleProgressView;
import co.tinode.tindroid.widgets.QRCodeScanner;

public class BrandingFragment extends Fragment {
    private static final String URI_PREFIX = "tinode:host/";

    private QRCodeScanner mQrScanner = null;

    private PreviewView mCameraPreview;

    private final ActivityResultLauncher<String> mRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Check if permission is granted.
                if (isGranted) {
                    mQrScanner.startCamera(BrandingFragment.this, mCameraPreview);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        return inflater.inflate(R.layout.fragment_branding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        view.findViewById(R.id.confirm).setOnClickListener(confirm -> {
            TextView editor = activity.findViewById(R.id.editId);
            if (editor != null) {
                String brandId = editor.getText().toString();
                if (TextUtils.isEmpty(brandId)) {
                    editor.setError(getString(R.string.code_required));
                } else {
                    configIDReceived(brandId);
                }
            }
        });

        mCameraPreview = view.findViewById(R.id.cameraPreviewView);
        if (mQrScanner == null) {
            mQrScanner = new QRCodeScanner(activity, URI_PREFIX, this::configIDReceived);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        if (!UiUtils.isPermissionGranted(activity, Manifest.permission.CAMERA)) {
            mRequestPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        mQrScanner.startCamera(this, mCameraPreview);
    }

    private void configIDReceived(String brandId) {
        final Activity activity = requireActivity();
        activity.findViewById(R.id.confirm).setEnabled(false);
        mQrScanner.stopCamera();

        // Hide soft keyboard.
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        // Show spinner.
        CircleProgressView spinner = activity.findViewById(R.id.progressCircle);
        // spinner.setVisibility(View.VISIBLE);
        spinner.show();

        Executors.newSingleThreadExecutor().execute(() ->
                BrandingConfig.fetchConfigFromServer(activity, brandId,
                        config -> activity.runOnUiThread(() ->
                                ((LoginActivity) activity).showFragment(LoginActivity.FRAGMENT_LOGIN, null))));
    }
}
