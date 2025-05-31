package co.tinode.tindroid.widgets;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import co.tinode.tindroid.R;

/**
 * Bottom Sheet Dialog for selecting an attachment type: camera or gallery.
 * This should be expanded to support more attachment types.
 */
public class AttachmentPickerDialog extends BottomSheetDialogFragment {
    private static final String TAG = "AttachmentPickerDialog";

    // Gallery
    private final ActivityResultLauncher<PickVisualMediaRequest> mGalleryLauncher;
    // Still photo
    private final ActivityResultLauncher<Uri> mCameraFullLauncher;
    private final ActivityResultLauncher<Void> mCameraPreviewLauncher;
    private final ActivityResultLauncher<String> mCameraPermissionLauncher;
    private final Uri mDestPhotoUri;
    // Video
    private final ActivityResultLauncher<Uri> mVideoLauncher;
    private final ActivityResultLauncher<String> mVideoPermissionLauncher;
    private final Uri mDestVideoUri;

    protected AttachmentPickerDialog(@Nullable ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher,
                                  @Nullable ActivityResultLauncher<Uri> cameraLauncher,
                                  @Nullable ActivityResultLauncher<Void> cameraPreviewLauncher,
                                  @Nullable ActivityResultLauncher<String> cameraPermissionLauncher,
                                  @Nullable ActivityResultLauncher<Uri> videoLauncher,
                                  @Nullable ActivityResultLauncher<String> videoPermissionLauncher,
                                  @Nullable Uri destPhotoUri, @Nullable Uri destVideoUri) {
        super();
        mGalleryLauncher = galleryLauncher;

        mCameraFullLauncher = cameraLauncher;
        mCameraPreviewLauncher = cameraPreviewLauncher;
        mCameraPermissionLauncher = cameraPermissionLauncher;
        mDestPhotoUri = destPhotoUri;

        mVideoLauncher = videoLauncher;
        mVideoPermissionLauncher = videoPermissionLauncher;
        mDestVideoUri = destVideoUri;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the bottom sheet layout
        View view = inflater.inflate(R.layout.dialog_media_selector, container, false);

        // Check if the device has a camera.
        View cameraButton = view.findViewById(R.id.cameraButton);
        boolean hasCamera = requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        if (mCameraFullLauncher != null && mDestPhotoUri != null && hasCamera) {
            cameraButton.setOnClickListener(v -> {
                try {
                    if (ContextCompat.checkSelfPermission(requireActivity(),
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            // noinspection ConstantConditions
                            mCameraFullLauncher.launch(mDestPhotoUri);
                    } else if (mCameraPermissionLauncher != null) {
                        mCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                    } else {
                        Toast.makeText(requireActivity(), R.string.permission_missing, Toast.LENGTH_LONG).show();
                    }
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(requireActivity(), R.string.camera_not_accessible, Toast.LENGTH_LONG).show();
                }
                dismiss();
            });
        } else if (mCameraPreviewLauncher != null && hasCamera) {
            cameraButton.setOnClickListener(v -> {
                try {
                    if (ContextCompat.checkSelfPermission(requireActivity(),
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        // noinspection ConstantConditions
                        mCameraPreviewLauncher.launch(null);
                    } else if (mCameraPermissionLauncher != null) {
                        mCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                    } else {
                        Toast.makeText(requireActivity(), R.string.permission_missing, Toast.LENGTH_LONG).show();
                    }
                } catch (ActivityNotFoundException ex) {
                    // This was an actual crash.
                    Toast.makeText(requireActivity(), R.string.camera_not_accessible, Toast.LENGTH_LONG).show();
                }
                dismiss();
            });
        } else {
            // Disable the camera button if the device does not have a camera.
            cameraButton.setEnabled(false);
            cameraButton.setAlpha(0.8f);
        }

        View videoButton = view.findViewById(R.id.videoButton);
        if (mVideoLauncher != null && mDestVideoUri != null && hasCamera) {
            videoButton.setOnClickListener(v -> {
                try {
                    if (ContextCompat.checkSelfPermission(requireActivity(),
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        // noinspection ConstantConditions
                        mVideoLauncher.launch(mDestVideoUri);
                    } else if (mVideoPermissionLauncher != null) {
                        mVideoPermissionLauncher.launch(Manifest.permission.CAMERA);
                    } else {
                        Toast.makeText(requireActivity(), R.string.permission_missing, Toast.LENGTH_LONG).show();
                    }
                } catch (ActivityNotFoundException ex) {
                    // This was an actual crash.
                    Toast.makeText(requireActivity(), R.string.camera_not_accessible, Toast.LENGTH_LONG).show();
                }
                dismiss();
            });
        } else {
            view.findViewById(R.id.recordVideo).setVisibility(View.GONE);
        }

        View galleryButton = view.findViewById(R.id.galleryButton);
        if (mGalleryLauncher != null) {
            galleryButton.setOnClickListener(v -> {
                final Fragment parent = getParentFragment();
                if (parent != null) {
                    try {
                        // noinspection ConstantConditions
                        mGalleryLauncher.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                                .build());
                    } catch (ActivityNotFoundException ex) {
                        Toast.makeText(requireActivity(), R.string.unable_to_open_gallery, Toast.LENGTH_LONG).show();
                    }
                }
                dismiss();
            });
        } else {
            galleryButton.setEnabled(false);
            galleryButton.setAlpha(0.8f);
        }
        return view;
    }

    // Show the bottom sheet with full-resolution photo launcher.
    public void show(FragmentManager fm) {
        super.show(fm, TAG);
    }

    public static class Builder {
        private ActivityResultLauncher<PickVisualMediaRequest> mGalleryLauncher;

        private ActivityResultLauncher<Uri> mCameraLauncher;
        private ActivityResultLauncher<Void> mCameraPreviewLauncher;
        private ActivityResultLauncher<String> mCameraPermissionLauncher;
        private Uri mDestPhotoUri;

        private ActivityResultLauncher<Uri> mVideoLauncher;
        private ActivityResultLauncher<String> mVideoPermissionLauncher;
        private Uri mDestVideoUri;

        public Builder setGalleryLauncher(@NonNull ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher) {
            mGalleryLauncher = galleryLauncher;
            return this;
        }

        public Builder setCameraLauncher(@NonNull ActivityResultLauncher<Uri> cameraLauncher,
                                         @Nullable ActivityResultLauncher<String> cameraPermissionLauncher,
                                         @NonNull Uri destinationUri) {
            mCameraLauncher = cameraLauncher;
            mCameraPermissionLauncher = cameraPermissionLauncher;
            mDestPhotoUri = destinationUri;
            return this;
        }

        public Builder setVideoLauncher(@NonNull ActivityResultLauncher<Uri> videoLauncher,
                                        @Nullable ActivityResultLauncher<String> videoPermissionLauncher,
                                        @NonNull Uri destinationUri) {
            mVideoLauncher = videoLauncher;
            mVideoPermissionLauncher = videoPermissionLauncher;
            mDestVideoUri = destinationUri;
            return this;
        }

        public Builder setCameraPreviewLauncher(@NonNull ActivityResultLauncher<Void> cameraLauncherPreview,
                                                @Nullable ActivityResultLauncher<String> cameraPermissionLauncher) {
            mCameraPreviewLauncher = cameraLauncherPreview;
            mCameraPermissionLauncher = cameraPermissionLauncher;
            return this;
        }

        public AttachmentPickerDialog build() {
            if (mCameraPreviewLauncher != null) {
                return new AttachmentPickerDialog(mGalleryLauncher, null,
                        mCameraPreviewLauncher, mCameraPermissionLauncher, null,
                        null, null, null);
            } else if (mCameraLauncher != null && mDestPhotoUri != null) {
                return new AttachmentPickerDialog(mGalleryLauncher, mCameraLauncher, null,
                        mCameraPermissionLauncher, mVideoLauncher, mVideoPermissionLauncher,
                        mDestPhotoUri, mDestVideoUri);
            } else {
                throw new IllegalStateException("Camera launcher and destination URI must be set");
            }
        }
    }
}
