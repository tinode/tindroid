package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import com.squareup.picasso.Picasso;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

/**
 * Fragment for viewing a video: before being attached or received.
 */
public class VideoViewFragment extends Fragment implements MenuProvider {
    private static final String TAG = "VideoViewFragment";

    // Placeholder dimensions when the sender has not provided dimensions.
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    // Max size of the video and poster bitmap to be sent as byte array.
    // Otherwise write to temp file.
    private static final int MAX_POSTER_BYTES = 4096; // 4K.
    private static final int MAX_VIDEO_BYTES = 6144;

    private ImageView mPosterView;
    private ProgressBar mProgressView;
    private VideoView mVideoView;

    private int mVideoWidth;
    private int mVideoHeight;

    private MenuItem mDownloadMenuItem;
    private boolean isPreview = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final Activity activity = requireActivity();

        View view = inflater.inflate(R.layout.fragment_view_video, container, false);
        mPosterView = view.findViewById(R.id.poster);
        mProgressView = view.findViewById(R.id.loading);

        mVideoView = view.findViewById(R.id.video);
        MediaController mediaControls = new MediaController(activity);
        mediaControls.setAnchorView(mVideoView);
        mediaControls.setMediaPlayer(mVideoView);
        mVideoView.setMediaController(mediaControls);
        mVideoView.setOnPreparedListener(mp -> {
            mProgressView.setVisibility(View.GONE);
            mPosterView.setVisibility(View.GONE);
            mVideoView.setVisibility(View.VISIBLE);
            if (mDownloadMenuItem != null) {
                // Local video may be ready before menu is ready.
                mDownloadMenuItem.setEnabled(true);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
            if (isPreview) {
                mVideoView.pause();
            }
        });
        mVideoView.setOnInfoListener((mp, what, extra) -> {
            switch(what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    mProgressView.setVisibility(View.VISIBLE);
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    mProgressView.setVisibility(View.GONE);
            }
            return false;
        });
        mVideoView.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "Playback error " + what + "/" + extra);
            mProgressView.setVisibility(View.GONE);
            Bundle args = getArguments();
            if (args != null) {
                int width = args.getInt(AttachmentHandler.ARG_IMAGE_WIDTH, DEFAULT_WIDTH);
                int height = args.getInt(AttachmentHandler.ARG_IMAGE_HEIGHT, DEFAULT_HEIGHT);
                mPosterView.setImageDrawable(UiUtils.getPlaceholder(activity,
                        ResourcesCompat.getDrawable(getResources(), R.drawable.ic_video_broken, null),
                        null, width, height));
            }
            Toast.makeText(activity, R.string.unable_to_play_video, Toast.LENGTH_LONG).show();
            return true;
        });

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
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((MenuHost) requireActivity()).addMenuProvider(this,
                getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        super.onResume();

        final Activity activity = requireActivity();
        final Bundle args = getArguments();
        if (args == null) {
            return;
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.video_preview);
            toolbar.setSubtitle(null);
            toolbar.setLogo(null);
        }

        boolean initialized = false;
        isPreview = false;
        final Uri localUri = args.getParcelable(AttachmentHandler.ARG_LOCAL_URI);
        if (localUri != null) {
            isPreview = true;
            // Outgoing video preview.
            activity.findViewById(R.id.metaPanel).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.editMessage).requestFocus();
            mVideoView.setVideoURI(localUri);
            mVideoView.start();
            initialized = true;
        } else {
            // Viewing received video.
            activity.findViewById(R.id.metaPanel).setVisibility(View.GONE);
            final Uri ref = args.getParcelable(AttachmentHandler.ARG_REMOTE_URI);
            if (ref != null) {
                mVideoView.setVideoURI(ref, Cache.getTinode().getLargeFileHelper().headers());
                mVideoView.start();
                initialized = true;
            } else {
                final byte[] bits = args.getByteArray(AttachmentHandler.ARG_SRC_BYTES);
                if (bits != null) {
                    try {
                        File temp = File.createTempFile("VID_" + System.currentTimeMillis(),
                                ".video", activity.getCacheDir());
                        temp.deleteOnExit();
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(temp));
                        out.write(bits);
                        out.close();
                        mVideoView.setVideoURI(Uri.fromFile(temp));
                        mVideoView.start();
                        initialized = true;
                    } catch (IOException ex) {
                        Log.w(TAG, "Failed to save video to temp file", ex);
                    }
                }
            }
        }

        if (!initialized) {
            mProgressView.setVisibility(View.GONE);
        }

        loadPoster(activity, args, initialized);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        mDownloadMenuItem = menu.getItem(0);
        mDownloadMenuItem.setEnabled(false);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_download, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final Bundle args = getArguments();
        if (activity == null || args == null) {
            return false;
        }

        if (item.getItemId() == R.id.action_download) {
            String filename = args.getString(AttachmentHandler.ARG_FILE_NAME);
            String mime = args.getString(AttachmentHandler.ARG_MIME_TYPE);

            if (TextUtils.isEmpty(filename)) {
                filename = getResources().getString(R.string.tinode_video);
                filename += "" + (System.currentTimeMillis() % 10000);
            }

            Uri ref = args.getParcelable(AttachmentHandler.ARG_REMOTE_URI);
            byte[] bits = args.getByteArray(AttachmentHandler.ARG_SRC_BYTES);
            AttachmentHandler.enqueueDownloadAttachment(activity,
                    ref != null ? ref.toString() : null, bits, filename, mime);
            return true;
        }

        return false;
    }

    private void loadPoster(Activity activity, final Bundle args, boolean initialized) {
        // Check if bitmap is attached as an array of bytes (received).
        byte[] bits = args.getByteArray(AttachmentHandler.ARG_PREVIEW);
        if (bits != null) {
            Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
            mPosterView.setImageDrawable(new BitmapDrawable(getResources(), bmp));
            return;
        }

        int width = args.getInt(AttachmentHandler.ARG_IMAGE_WIDTH, DEFAULT_WIDTH);
        int height = args.getInt(AttachmentHandler.ARG_IMAGE_HEIGHT, DEFAULT_HEIGHT);

        int placeholder_id = initialized ? R.drawable.ic_video : R.drawable.ic_video_broken;
        Drawable placeholder = UiUtils.getPlaceholder(activity,
                ResourcesCompat.getDrawable(getResources(), placeholder_id, null),
                null, width, height);

        // Poster is included as a reference.
        final Uri ref = args.getParcelable(AttachmentHandler.ARG_PRE_URI);
        if (ref != null) {
            Picasso.get().load(ref)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .into(mPosterView);
            return;
        }

        // No poster included at all. Show gray background with an icon in the middle.
        mPosterView.setForegroundGravity(Gravity.CENTER);
        mPosterView.setImageDrawable(placeholder);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Picasso.get().cancelRequest(mPosterView);
    }

    private Uri writeToTempFile(Context ctx, byte[] bits, String prefix, String suffix) {
        Uri fileUri = null;
        try {
            File temp = File.createTempFile(prefix, suffix, ctx.getCacheDir());
            temp.deleteOnExit();
            fileUri = Uri.fromFile(temp);
            OutputStream os = new FileOutputStream(temp);
            os.write(bits);
            os.close();
        } catch (IOException ex) {
            Log.i(TAG, "Unable to create temp file for video " + prefix, ex);
        }
        return fileUri;
    }

    private void sendVideo() {
        final MessageActivity activity = (MessageActivity) requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Bundle inputArgs = getArguments();
        if (inputArgs == null) {
            Log.w(TAG, "sendVideo called with no arguments");
            return;
        }

        Bundle outputArgs = new Bundle();

        outputArgs.putString(AttachmentHandler.ARG_TOPIC_NAME,
                inputArgs.getString(AttachmentHandler.ARG_TOPIC_NAME));

        String mimeType = inputArgs.getString(AttachmentHandler.ARG_MIME_TYPE);
        outputArgs.putString(AttachmentHandler.ARG_MIME_TYPE, mimeType);

        outputArgs.putParcelable(AttachmentHandler.ARG_REMOTE_URI,
                inputArgs.getParcelable(AttachmentHandler.ARG_REMOTE_URI));

        outputArgs.putParcelable(AttachmentHandler.ARG_LOCAL_URI,
                inputArgs.getParcelable(AttachmentHandler.ARG_LOCAL_URI));

        final byte[] videoBits = inputArgs.getByteArray(AttachmentHandler.ARG_SRC_BYTES);
        if (videoBits != null) {
            if (videoBits.length > MAX_VIDEO_BYTES) {
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                String ext = mime.getExtensionFromMimeType(mimeType);
                Uri fileUri = writeToTempFile(activity, videoBits, "VID_",
                        TextUtils.isEmpty(ext) ? ".video" : ("." + ext));
                if (fileUri != null) {
                    outputArgs.putParcelable(AttachmentHandler.ARG_LOCAL_URI, fileUri);
                } else {
                    Toast.makeText(activity, R.string.unable_to_attach_file, Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                outputArgs.putByteArray(AttachmentHandler.ARG_SRC_BYTES, videoBits);
            }
        }

        final EditText inputField = activity.findViewById(R.id.editMessage);
        if (inputField != null) {
            String caption = inputField.getText().toString().trim();
            if (!TextUtils.isEmpty(caption)) {
                outputArgs.putString(AttachmentHandler.ARG_IMAGE_CAPTION, caption);
            }
        }

        outputArgs.putInt(AttachmentHandler.ARG_IMAGE_WIDTH, mVideoWidth);
        outputArgs.putInt(AttachmentHandler.ARG_IMAGE_HEIGHT, mVideoHeight);
        outputArgs.putInt(AttachmentHandler.ARG_DURATION, mVideoView.getDuration());

        // Capture current video frame for use as a poster (video preview).
        videoFrameCapture(bmp -> {
            if (bmp != null) {
                if (mVideoWidth > Const.MAX_POSTER_SIZE ||  mVideoHeight > Const.MAX_POSTER_SIZE) {
                    bmp = UiUtils.scaleBitmap(bmp, Const.MAX_POSTER_SIZE, Const.MAX_POSTER_SIZE, false);
                }
                byte[] bitmapBits = UiUtils.bitmapToBytes(bmp, "image/jpeg");
                if (bitmapBits.length > MAX_POSTER_BYTES) {
                    Uri fileUri = writeToTempFile(activity, bitmapBits, "PST_", ".jpeg");
                    if (fileUri != null) {
                        outputArgs.putParcelable(AttachmentHandler.ARG_PRE_URI, fileUri);
                    }
                } else {
                    outputArgs.putByteArray(AttachmentHandler.ARG_PREVIEW, UiUtils.bitmapToBytes(bmp, "image/jpeg"));
                }
                outputArgs.putString(AttachmentHandler.ARG_PRE_MIME_TYPE, "image/jpeg");
            }

            AttachmentHandler.enqueueMsgAttachmentUploadRequest(activity, AttachmentHandler.ARG_OPERATION_VIDEO, outputArgs);
            activity.getSupportFragmentManager().popBackStack();
        });
    }

    interface BitmapReady {
        void done(Bitmap bmp);
    }

    // Take screenshot of the VideoView to use as poster.
    private void videoFrameCapture(BitmapReady callback) {
        Bitmap bitmap  = Bitmap.createBitmap(mVideoWidth, mVideoHeight, Bitmap.Config.ARGB_8888);
        try {
            HandlerThread handlerThread = new HandlerThread("videoFrameCapture");
            handlerThread.start();
            PixelCopy.request(mVideoView, bitmap, result -> {
                if (result == PixelCopy.SUCCESS) {
                    callback.done(bitmap);
                } else {
                    Log.w(TAG, "Failed to capture frame: " + result);
                    callback.done(null);
                }
                handlerThread.quitSafely();
            }, new Handler(handlerThread.getLooper()));
        } catch (IllegalArgumentException ex) {
            callback.done(null);
            Log.w(TAG, "Failed to capture frame", ex);
        }
    }
}
