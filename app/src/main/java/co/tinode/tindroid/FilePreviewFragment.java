package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

public class FilePreviewFragment extends Fragment {
    // Icon ID for mime type. Add more mime type to icon mappings here.
    private static final Map<String, Integer> sMime2Icon;
    private static final int DEFAULT_ICON_ID = R.drawable.ic_file;
    private static final int INVALID_ICON_ID = R.drawable.ic_file_alert;

    static {
        sMime2Icon = new HashMap<>();
        sMime2Icon.put("image", R.drawable.ic_file_image);
        sMime2Icon.put("text", R.drawable.ic_file_text);
        sMime2Icon.put("video", R.drawable.ic_video);
    }

    private ImageView mImageView;
    private ImageButton mSendButton;

    private final ActivityResultLauncher<String> mRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Check if permission is granted.
                if (isGranted) {
                    Bundle args = getArguments();
                    if (args != null) {
                        updateFormValues(requireActivity(), args, null, true);
                    }
                }
            });

    private static int getIconIdForMimeType(String mime) {
        if (TextUtils.isEmpty(mime)) {
            return DEFAULT_ICON_ID;
        }
        // Try full mim type first.
        Integer id = sMime2Icon.get(mime);
        if (id != null) {
            return id;
        }

        // Try the major component of mime type, e.g. "text/plain" -> "text".
        String[] parts = mime.split("/");
        id = sMime2Icon.get(parts[0]);
        if (id != null) {
            return id;
        }

        // Fallback to default icon.
        return DEFAULT_ICON_ID;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_preview, container, false);
        mImageView = view.findViewById(R.id.image);

        // Send message on button click.
        mSendButton = view.findViewById(R.id.chatSendButton);
        mSendButton.setOnClickListener(v -> sendFile());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = requireActivity();
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.document_preview);
            toolbar.setSubtitle(null);
            toolbar.setLogo(null);
        }

        boolean accessGranted;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                !UiUtils.isPermissionGranted(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)) {
            accessGranted = false;
            mRequestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            accessGranted = true;
        }

        Uri uri = args.getParcelable(AttachmentHandler.ARG_LOCAL_URI);
        if (uri != null) {
            updateFormValues(activity, args, uri, accessGranted);
        } else {
            mImageView.setImageDrawable(ResourcesCompat.getDrawable(activity.getResources(), INVALID_ICON_ID, null));
            ((TextView) activity.findViewById(R.id.content_type)).setText(getString(R.string.invalid_file));
            ((TextView) activity.findViewById(R.id.file_name)).setText(getString(R.string.invalid_file));
            ((TextView) activity.findViewById(R.id.file_size)).setText(UtilsString.bytesToHumanSize(0));
            mSendButton.setEnabled(false);
        }
    }

    private void updateFormValues(@NonNull Activity activity,
                                  @NonNull Bundle args, Uri uri, boolean accessGranted) {
        if (uri == null) {
            uri = args.getParcelable(AttachmentHandler.ARG_LOCAL_URI);
        }

        if (uri == null) {
            return;
        }

        String mimeType = args.getString(AttachmentHandler.ARG_MIME_TYPE);
        String fileName = args.getString(AttachmentHandler.ARG_FILE_NAME);
        long fileSize = args.getLong(AttachmentHandler.ARG_FILE_SIZE);
        if ((mimeType == null || fileName == null || fileSize == 0) && accessGranted) {
            AttachmentHandler.UploadDetails uploadDetails = AttachmentHandler.getFileDetails(activity,
                    uri, args.getString(AttachmentHandler.ARG_FILE_PATH));
            fileName = fileName == null ? uploadDetails.fileName : fileName;
            mimeType = mimeType == null ? uploadDetails.mimeType : mimeType;
            fileSize = fileSize == 0 ? uploadDetails.fileSize : fileSize;
        }
        if (TextUtils.isEmpty(fileName)) {
            fileName = getString(R.string.default_attachment_name);
        }
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "N/A";
        }

        // Show icon for mime type.
        mImageView.setImageDrawable(ResourcesCompat.getDrawable(activity.getResources(),
                getIconIdForMimeType(mimeType), null));
        ((TextView) activity.findViewById(R.id.content_type)).setText(mimeType);
        ((TextView) activity.findViewById(R.id.file_name)).setText(fileName);
        ((TextView) activity.findViewById(R.id.file_size)).setText(UtilsString.bytesToHumanSize(fileSize));

        activity.findViewById(R.id.missingPermission).setVisibility(accessGranted ? View.GONE : View.VISIBLE);
        mSendButton.setEnabled(accessGranted);
    }

    private void sendFile() {
        final MessageActivity activity = (MessageActivity) requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final Bundle args = getArguments();
        if (args == null) {
            return;
        }

        AttachmentHandler.enqueueMsgAttachmentUploadRequest(activity, AttachmentHandler.ARG_OPERATION_FILE, args);

        activity.getSupportFragmentManager().popBackStack();
    }
}
