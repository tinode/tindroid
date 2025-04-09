package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;
import co.tinode.tinodesdk.Tinode;
import coil.Coil;
import coil.request.ImageRequest;

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
    private static final int MAX_POSTER_BYTES = 1024 * 3; // 3K.
    private static final int MAX_VIDEO_BYTES = 1024 * 4; // 4K.

    private ExoPlayer mExoPlayer;
    // Media source factory for remote videos from Tinode server.
    private MediaSource.Factory mTinodeHttpMediaSourceFactory;

    private ImageView mPosterView;
    private ProgressBar mProgressView;
    private PlayerView mVideoView;

    private int mVideoWidth;
    private int mVideoHeight;

    private MenuItem mDownloadMenuItem;

    @OptIn(markerClass = UnstableApi.class)
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Activity activity = requireActivity();

        View view = inflater.inflate(R.layout.fragment_view_video, container, false);

        DefaultHttpDataSource.Factory httpDataSourceFactory =
                new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setDefaultRequestProperties(Cache.getTinode().getRequestHeaders());
        mTinodeHttpMediaSourceFactory =
                new DefaultMediaSourceFactory(new CacheDataSource.Factory()
                        .setCache(TindroidApp.getVideoCache())
                        .setUpstreamDataSourceFactory(httpDataSourceFactory));
        // Construct ExoPlayer instance.
        mExoPlayer = new ExoPlayer.Builder(activity).build();

        mExoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Player.Listener.super.onPlaybackStateChanged(playbackState);
                switch (playbackState) {
                    case Player.STATE_IDLE:
                    case Player.STATE_BUFFERING:
                        break;
                    case Player.STATE_READY:
                        mProgressView.setVisibility(View.GONE);
                        mPosterView.setVisibility(View.GONE);
                        mVideoView.setVisibility(View.VISIBLE);
                        if (mDownloadMenuItem != null) {
                            // Local video may be ready before menu is ready.
                            mDownloadMenuItem.setEnabled(true);
                        }

                        VideoSize vs = mExoPlayer.getVideoSize();
                        if (vs.width > 0 && vs.height > 0) {
                            mVideoWidth = vs.width;
                            mVideoHeight = vs.height;
                        } else {
                            Log.w(TAG, "Unable to read video dimensions");
                        }
                        break;
                    case Player.STATE_ENDED:
                        mProgressView.setVisibility(View.GONE);
                        break;
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.w(TAG, "Playback error", error);
                mProgressView.setVisibility(View.GONE);
                Bundle args = getArguments();
                if (args != null) {
                    int width = args.getInt(AttachmentHandler.ARG_IMAGE_WIDTH, DEFAULT_WIDTH);
                    int height = args.getInt(AttachmentHandler.ARG_IMAGE_HEIGHT, DEFAULT_HEIGHT);
                    mPosterView.setImageDrawable(UiUtils.getPlaceholder(activity,
                            ResourcesCompat.getDrawable(getResources(),
                                    R.drawable.ic_video_broken, null), null, width, height));
                }
                Toast.makeText(activity, R.string.unable_to_play_video, Toast.LENGTH_LONG).show();
            }
        });

        mPosterView = view.findViewById(R.id.poster);
        mProgressView = view.findViewById(R.id.loading);

        mVideoView = view.findViewById(R.id.video);
        mVideoView.setPlayer(mExoPlayer);

        // Send message on button click.
        view.findViewById(R.id.chatSendButton).setOnClickListener(v -> sendVideo());
        // Send message on Enter.
        ((EditText) view.findViewById(R.id.editMessage))
                .setOnEditorActionListener((v, actionId, event) -> {
            sendVideo();
            return true;
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @OptIn(markerClass = UnstableApi.class)
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
        final Uri localUri = args.getParcelable(AttachmentHandler.ARG_LOCAL_URI);
        if (localUri != null) {
            // Outgoing video preview.
            activity.findViewById(R.id.metaPanel).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.editMessage).requestFocus();
            mVideoView.setControllerAutoShow(true);
            MediaItem mediaItem = MediaItem.fromUri(localUri);
            mExoPlayer.setMediaItem(mediaItem);
            mExoPlayer.prepare();
            initialized = true;
        } else {
            // Viewing received video.
            activity.findViewById(R.id.metaPanel).setVisibility(View.GONE);
            Uri ref = args.getParcelable(AttachmentHandler.ARG_REMOTE_URI);
            if (ref != null) {
                // Remote URL. Check if URL is trusted.
                Tinode tinode = Cache.getTinode();
                boolean trusted = false;
                if (ref.isAbsolute()) {
                    try {
                        trusted = tinode.isTrustedURL(new URL(ref.toString()));
                    } catch (MalformedURLException ignored) {
                        Log.w(TAG, "Invalid video URL: '" + ref + "'");
                    }
                } else {
                    URL url = tinode.toAbsoluteURL(ref.toString());
                    if (url != null) {
                        ref = Uri.parse(url.toString());
                        trusted = true;
                    } else {
                        Log.w(TAG, "Invalid relative video URL: '" + ref + "'");
                    }
                }

                if (trusted) {
                    MediaSource mediaSource =
                            mTinodeHttpMediaSourceFactory.createMediaSource(
                                    new MediaItem.Builder().setUri(ref).build());
                    mExoPlayer.setMediaSource(mediaSource);
                } else {
                    MediaItem mediaItem = MediaItem.fromUri(ref);
                    mExoPlayer.setMediaItem(mediaItem);
                }
                mVideoView.setControllerAutoShow(false);
                mExoPlayer.prepare();
                mExoPlayer.setPlayWhenReady(true);
                initialized = true;
            } else {
                final byte[] bits = args.getByteArray(AttachmentHandler.ARG_SRC_BYTES);
                if (bits != null) {
                    try {
                        File temp = File.createTempFile("VID_" + System.currentTimeMillis(),
                                ".video", activity.getCacheDir());
                        temp.deleteOnExit();
                        OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp.toPath()));
                        out.write(bits);
                        out.close();
                        mVideoView.setControllerAutoShow(false);
                        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(temp));
                        mExoPlayer.setMediaItem(mediaItem);
                        mExoPlayer.prepare();
                        mExoPlayer.setPlayWhenReady(true);
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
        menu.clear();
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

            if (filename != null) {
                filename = filename.trim();
            }
            if (TextUtils.isEmpty(filename)) {
                filename = getResources().getString(R.string.tinode_video);
                filename += Long.toString(System.currentTimeMillis() % 10000);
            }

            Uri ref = args.getParcelable(AttachmentHandler.ARG_REMOTE_URI);
            byte[] bits = args.getByteArray(AttachmentHandler.ARG_SRC_BYTES);
            AttachmentHandler.enqueueDownloadAttachment(activity, ref != null ? ref.toString() : null,
                    bits, filename, mime);
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
        Drawable placeholder = UiUtils.getPlaceholder(activity, ResourcesCompat.getDrawable(getResources(),
                placeholder_id, null), null, width, height);

        // Poster is included as a reference.
        final Uri ref = args.getParcelable(AttachmentHandler.ARG_PRE_URI);
        if (ref != null) {
            Coil.imageLoader(activity).enqueue(
                    new ImageRequest.Builder(activity)
                            .data(ref)
                            .placeholder(placeholder)
                            .error(placeholder)
                            .target(mPosterView)
                            .build());
            return;
        }

        // No poster included at all. Show gray background with an icon in the middle.
        mPosterView.setForegroundGravity(Gravity.CENTER);
        mPosterView.setImageDrawable(placeholder);
    }

    @Override
    public void onPause() {
        super.onPause();
        mExoPlayer.stop();
        mExoPlayer.release();
    }

    private Uri writeToTempFile(Context ctx, byte[] bits, String prefix, String suffix) {
        Uri fileUri = null;
        try {
            File temp = File.createTempFile(prefix, suffix, ctx.getCacheDir());
            temp.deleteOnExit();
            fileUri = Uri.fromFile(temp);
            OutputStream os = Files.newOutputStream(temp.toPath());
            os.write(bits);
            os.close();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to create temp file for video " + prefix, ex);
        }
        return fileUri;
    }

    private void sendVideo() {
        final MessageActivity activity = (MessageActivity) requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (mVideoWidth <= 0 || mVideoHeight <= 0) {
            Log.w(TAG, "sendVideo called for 0x0 video");
            return;
        }

        Bundle inputArgs = getArguments();
        if (inputArgs == null) {
            Log.w(TAG, "sendVideo called with no arguments");
            return;
        }

        Bundle outputArgs = new Bundle();

        outputArgs.putString(AttachmentHandler.ARG_TOPIC_NAME, inputArgs.getString(AttachmentHandler.ARG_TOPIC_NAME));

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
        outputArgs.putInt(AttachmentHandler.ARG_DURATION, (int) mExoPlayer.getDuration());

        // Capture current video frame for use as a poster (video preview).
        videoFrameCapture(bmp -> {
            if (bmp != null) {
                if (mVideoWidth > Const.MAX_POSTER_SIZE || mVideoHeight > Const.MAX_POSTER_SIZE) {
                    bmp = UiUtils.scaleBitmap(bmp, Const.MAX_POSTER_SIZE, Const.MAX_POSTER_SIZE, false);
                }
                byte[] bitmapBits = UiUtils.bitmapToBytes(bmp, "image/jpeg");
                if (bitmapBits.length > MAX_POSTER_BYTES) {
                    Uri fileUri = writeToTempFile(activity, bitmapBits, "PST_", ".jpeg");
                    if (fileUri != null) {
                        outputArgs.putParcelable(AttachmentHandler.ARG_PRE_URI, fileUri);
                    }
                } else {
                    outputArgs.putByteArray(AttachmentHandler.ARG_PREVIEW,
                            UiUtils.bitmapToBytes(bmp, "image/jpeg"));
                }
                outputArgs.putString(AttachmentHandler.ARG_PRE_MIME_TYPE, "image/jpeg");
            }

            AttachmentHandler.enqueueMsgAttachmentUploadRequest(activity,
                    AttachmentHandler.ARG_OPERATION_VIDEO, outputArgs);
            activity.getSupportFragmentManager().popBackStack();
        });
    }

    // Take screenshot of the VideoView to use as poster.
    @OptIn(markerClass = UnstableApi.class)
    private void videoFrameCapture(BitmapReady callback) {
        Bitmap bitmap = Bitmap.createBitmap(mVideoWidth, mVideoHeight, Bitmap.Config.ARGB_8888);
        try {
            HandlerThread handlerThread = new HandlerThread("videoFrameCapture");
            handlerThread.start();
            View surfaceView = mVideoView.getVideoSurfaceView();
            if (surfaceView instanceof SurfaceView) {
                PixelCopy.request((SurfaceView) surfaceView, bitmap, result -> {
                    if (result == PixelCopy.SUCCESS) {
                        callback.done(bitmap);
                    } else {
                        Log.w(TAG, "Failed to capture frame: " + result);
                        callback.done(null);
                    }
                    handlerThread.quitSafely();
                }, new Handler(handlerThread.getLooper()));
            } else {
                callback.done(null);
                Log.w(TAG, "Wrong type of video surface: " +
                        (surfaceView != null ? surfaceView.getClass().getName() : "null"));
            }
        } catch (IllegalArgumentException ex) {
            callback.done(null);
            Log.w(TAG, "Failed to capture frame", ex);
        }
    }

    interface BitmapReady {
        void done(Bitmap bmp);
    }
}
