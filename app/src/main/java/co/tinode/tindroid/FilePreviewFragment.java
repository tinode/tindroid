package co.tinode.tindroid;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;

public class FilePreviewFragment extends Fragment {
    private static final String TAG = "FilePreviewFragment";

    // Icon ID for mime type. Add more mime type to icon mappings here.
    private static Map<String,Integer> sMime2Icon;
    private static final int DEFAULT_ICON_ID = R.drawable.ic_file;
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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_preview, container, false);
        mImageView = view.findViewById(R.id.image);

        // Send message on button click.
        view.findViewById(R.id.chatSendButton).setOnClickListener(new View.OnClickListener() {
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

        Bundle fileDetails = AttachmentUploader.getFileDetails(activity,
                (Uri) args.getParcelable("uri"), args.getString("file"));
        String mimeType = fileDetails.getString("mime");
        String filename = fileDetails.getString("name");
        if (TextUtils.isEmpty(filename)) {
            filename = getResources().getString(R.string.tinode_image);
        }

        // Show icon for mime type.
        mImageView.setImageDrawable(getResources().getDrawable(getIconIdForMimeType(mimeType)));
        ((TextView) activity.findViewById(R.id.content_type)).setText(mimeType);
        ((TextView) activity.findViewById(R.id.file_name)).setText(filename);
        ((TextView) activity.findViewById(R.id.image_size)).setText(UiUtils.bytesToHumanSize(fileDetails.getLong("size")));

        setHasOptionsMenu(false);
    }

    private void sendFile() {
        MessageActivity  activity = (MessageActivity) getActivity();
        if (activity == null) {
            return;
        }

        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        FragmentManager fm = activity.getSupportFragmentManager();
        fm.popBackStack();

        MessagesFragment messages = (MessagesFragment) fm.findFragmentByTag(MessageActivity.FRAGMENT_MESSAGES);
        if (messages != null) {
            // Must use unique ID for each upload. Otherwise trouble.
            LoaderManager.getInstance(activity).initLoader(Cache.getUniqueCounter(), args, messages);
        } else {
            Log.w(TAG, "MessagesFragment not found");
        }
    }

}
