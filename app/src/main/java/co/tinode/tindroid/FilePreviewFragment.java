package co.tinode.tindroid;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Drafty;

public class FilePreviewFragment extends Fragment {
    private static final String TAG = "FilePreviewFragment";

    // Icon ID for mime type. Add more mime type to icon mappings here.
    private static Map<String,Integer> sMime2Icon;
    private static final int DEFAULT_ICON_ID = R.drawable.ic_file;
    private static final int INVALID_ICON_ID = R.drawable.ic_file_alert;

    static {
        sMime2Icon = new HashMap<>();
        sMime2Icon.put("image", R.drawable.ic_image);
        sMime2Icon.put("text", R.drawable.ic_text_file);
        sMime2Icon.put("video", R.drawable.ic_movie);
    }

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

    private ImageView mImageView;
    private Button mSendButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_preview, container, false);
        mImageView = view.findViewById(R.id.image);

        // Send message on button click.
        mSendButton = view.findViewById(R.id.chatSendButton);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendFile();
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        Bundle args = getArguments();
        if (activity == null || args == null) {
            return;
        }

        Uri uri = args.getParcelable(AttachmentUploader.ARG_SRC_URI);
        if (uri != null) {
            AttachmentUploader.FileDetails fileDetails = AttachmentUploader.getFileDetails(activity,
                    uri, args.getString(AttachmentUploader.ARG_FILE_PATH));
            String fileName = fileDetails.fileName;
            if (TextUtils.isEmpty(fileName)) {
                fileName = getString(R.string.tinode_image);
            }

            // Show icon for mime type.
            mImageView.setImageDrawable(getResources().getDrawable(getIconIdForMimeType(fileDetails.mimeType)));
            ((TextView) activity.findViewById(R.id.content_type)).setText(fileDetails.mimeType);
            ((TextView) activity.findViewById(R.id.file_name)).setText(fileName);
            ((TextView) activity.findViewById(R.id.image_size)).setText(UiUtils.bytesToHumanSize(fileDetails.fileSize));
            mSendButton.setEnabled(true);
        } else {
            mImageView.setImageDrawable(getResources().getDrawable(INVALID_ICON_ID));
            ((TextView) activity.findViewById(R.id.content_type)).setText(getString(R.string.invalid_file));
            ((TextView) activity.findViewById(R.id.file_name)).setText(getString(R.string.invalid_file));
            ((TextView) activity.findViewById(R.id.image_size)).setText(UiUtils.bytesToHumanSize(0));
            mSendButton.setEnabled(false);
        }
        setHasOptionsMenu(false);
    }

    private void sendFile() {
        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity == null) {
            return;
        }

        final Bundle args = getArguments();
        if (args == null) {
            return;
        }

        AttachmentUploader.enqueueWorkRequest(activity, "file", args);

        activity.getSupportFragmentManager().popBackStack();
    }
}
