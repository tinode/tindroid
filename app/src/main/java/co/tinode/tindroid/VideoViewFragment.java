package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

/**
 * Fragment for viewing a video: before being attached or received.
 */
public class VideoViewFragment extends Fragment {
    private static final String TAG = "VideoViewFragment";

    private enum RemoteState {
        NONE,
        LOADING,
        SUCCESS,
        FAILED
    }

    private VideoView mVideoView;
    private MediaController mMediaControls;

    // State of the remote image.
    private RemoteState mRemoteState;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        View view = inflater.inflate(R.layout.fragment_view_video, container, false);

        mVideoView = view.findViewById(R.id.video);
        mMediaControls = new MediaController(activity);
        mMediaControls.setAnchorView(mVideoView);
        mVideoView.setMediaController(mMediaControls);

        // Send message on button click.
        view.findViewById(R.id.chatSendButton).setOnClickListener(v -> sendVideo());
        // Send message on Enter.
        ((EditText) view.findViewById(R.id.editMessage)).setOnEditorActionListener(
                (v, actionId, event) -> {
                    sendVideo();
                    return true;
                });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        final Activity activity = getActivity();
        final Bundle args = getArguments();
        if (activity == null || args == null) {
            return;
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.video_preview);
            toolbar.setSubtitle(null);
            toolbar.setLogo(null);
        }

        mRemoteState = RemoteState.NONE;
    }

    private void loadImage(final Activity activity, final Bundle args) {
        // Check if the bitmap is directly attached.
        int length = 0;
        Bitmap bmp = args.getParcelable(AttachmentHandler.ARG_SRC_BITMAP);
        if (bmp == null) {
            // Check if bitmap is attached as an array of bytes (received).
            byte[] bits = args.getByteArray(AttachmentHandler.ARG_SRC_BYTES);
            if (bits != null) {
                bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                length = bits.length;
            }
        }

        if (bmp == null) {
            // Preview large image before sending.
            Uri uri = args.getParcelable(AttachmentHandler.ARG_LOCAL_URI);
            if (uri != null) {
                // Local image.
                final ContentResolver resolver = activity.getContentResolver();
                // Resize image to ensure it's under the maximum in-band size.
                try {
                    InputStream is = resolver.openInputStream(uri);
                    if (is != null) {
                        bmp = BitmapFactory.decodeStream(is, null, null);
                        is.close();
                    }
                    // Make sure the bitmap is properly oriented in preview.
                    is = resolver.openInputStream(uri);
                    if (is != null) {
                        ExifInterface exif = new ExifInterface(is);
                        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_UNDEFINED);
                        if (bmp != null) {
                            bmp = UiUtils.rotateBitmap(bmp, orientation);
                        }
                        is.close();
                    }
                } catch (IOException ex) {
                    Log.i(TAG, "Failed to read image from " + uri, ex);
                }
            } else {
                // Remote image.
                final Uri ref = args.getParcelable(AttachmentHandler.ARG_REMOTE_URI);
                if (ref != null) {
                    mRemoteState = RemoteState.LOADING;
                    Picasso.get().load(ref)
                            .error(R.drawable.ic_broken_image)
                            .into(mImageView, new Callback() {
                                @Override
                                public void onSuccess() {
                                    mRemoteState = RemoteState.SUCCESS;

                                    Activity activity = getActivity();
                                    if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                                        return;
                                    }

                                    final Bitmap bmp = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
                                    mImageView.setScaleType(ImageView.ScaleType.MATRIX);
                                    mImageView.enableOverlay(false);

                                    activity.findViewById(R.id.metaPanel).setVisibility(View.VISIBLE);
                                    setupImagePostview(activity, args, bmp.getByteCount());
                                }

                                @Override
                                public void onError(Exception e) {
                                    mRemoteState = RemoteState.FAILED;
                                    Log.i(TAG, "Failed to fetch image: " + e.getMessage() + " (" + ref + ")");
                                    mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                    setHasOptionsMenu(false);
                                }
                            });
                }
            }
        }

        if (bmp != null) {
            // Must ensure the bitmap is not too big (some cameras can produce
            // bigger bitmaps that the phone can render)

            activity.findViewById(R.id.metaPanel).setVisibility(View.VISIBLE);

            if (length == 0) {
                // The image is being previewed before sending or uploading.
                setupImagePreview(activity);
            } else {
                // The image is downloaded.
                setupImagePostview(activity, args, length);
            }

            mImageView.setImageDrawable(new BitmapDrawable(getResources(), bmp));
        } else if (mRemoteState != RemoteState.SUCCESS) {
            // Show placeholder or a broken image.
                mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                mImageView.setImageDrawable(ResourcesCompat.getDrawable(getResources(),
                        mRemoteState == RemoteState.LOADING ?
                                R.drawable.ic_image :
                                R.drawable.ic_broken_image,
                        null));
            setHasOptionsMenu(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    // Setup fields for image preview.
    private void setupImagePreview(final Activity activity) {
        activity.findViewById(R.id.sendImagePanel).setVisibility(View.VISIBLE);
        setHasOptionsMenu(false);
    }

    // Setup fields for viewing downloaded image
    private void setupImagePostview(final Activity activity, Bundle args, long length) {
        // The received video is viewed.
        activity.findViewById(R.id.sendImagePanel).setVisibility(View.GONE);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_image, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final Activity activity = getActivity();
        if (activity == null) {
            return false;
        }

        if (item.getItemId() == R.id.action_download) {
            // Save image to Gallery.
            Bundle args = getArguments();
            String filename = null;
            if (args != null) {
                filename = args.getString(AttachmentHandler.ARG_FILE_NAME);
            }
            if (TextUtils.isEmpty(filename)) {
                filename = getResources().getString(R.string.tinode_image);
            }
            Bitmap bmp = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
            String savedAt = MediaStore.Images.Media.insertImage(activity.getContentResolver(), bmp,
                    filename, null);
            Toast.makeText(activity, savedAt != null ? R.string.image_download_success :
                    R.string.failed_to_save_download, Toast.LENGTH_LONG).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void sendVideo() {
        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        final EditText inputField = activity.findViewById(R.id.editMessage);
        if (inputField != null) {
            String caption = inputField.getText().toString().trim();
            if (!TextUtils.isEmpty(caption)) {
                args.putString(AttachmentHandler.ARG_IMAGE_CAPTION, caption);
            }
        }

        AttachmentHandler.enqueueMsgAttachmentUploadRequest(activity, AttachmentHandler.ARG_OPERATION_VIDEO, args);

        activity.getSupportFragmentManager().popBackStack();
    }

    private void acceptAvatar() {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        // Get dimensions and position of the scaled image:
        // convert cutOut from screen coordinates to bitmap coordinates.

        String topicName = args.getString(AttachmentHandler.ARG_TOPIC_NAME);
        ((AvatarCompletionHandler) activity).onAcceptAvatar(topicName, bmp);

        activity.getSupportFragmentManager().popBackStack();
    }

    public interface AvatarCompletionHandler {
        void onAcceptAvatar(String topicName, Bitmap avatar);
    }
}
