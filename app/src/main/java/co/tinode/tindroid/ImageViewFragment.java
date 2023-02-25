package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import co.tinode.tindroid.widgets.OverlaidImageView;

/**
 * Fragment for expanded display of an image: being attached or received.
 */
public class ImageViewFragment extends Fragment implements MenuProvider {
    private static final String TAG = "ImageViewFragment";

    private enum RemoteState {
        NONE,
        LOADING,
        SUCCESS,
        FAILED
    }

    // Bitmaps coming from the camera could be way too big.
    // 1792 is roughly 3MP for square bitmaps.
    private static final int MAX_BITMAP_DIM = 1792;

    // Maximum count of pixels in a zoomed image: width * height * scale^2.
    private static final int MAX_SCALED_PIXELS = 1024 * 1024 * 12;
    // How much bigger any image dimension is allowed to be compare to the screen size.
    private static final float MAX_SCALE_FACTOR = 8f;

    // The matrix actually used for scaling.
    private Matrix mMatrix = null;
    // Working matrix for pre-testing image bounds.
    private Matrix mWorkingMatrix = null;
    // Initial image bounds before any zooming and scaling.
    private RectF mInitialRect;
    // Screen bounds
    private RectF mScreenRect;
    // Bounds of the square cut out in the middle of the screen.
    private RectF mCutOutRect;

    // Working rectangle for testing image bounds after panning and zooming.
    private RectF mWorkingRect;

    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private OverlaidImageView mImageView;
    // This is an avatar preview before upload.
    private boolean mAvatarUpload;

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

        View view = inflater.inflate(R.layout.fragment_view_image, container, false);
        mMatrix = new Matrix();
        mImageView = view.findViewById(R.id.image);
        mImageView.setImageMatrix(mMatrix);

        GestureDetector.OnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float dX, float dY) {
                if (mWorkingRect == null || mInitialRect == null) {
                    // The image is not initialized yet.
                    return false;
                }

                mWorkingMatrix.postTranslate(-dX, -dY);
                mWorkingMatrix.mapRect(mWorkingRect, mInitialRect);

                // Make sure the image cannot be pushed off the viewport.
                RectF bounds = mAvatarUpload ? mCutOutRect : mScreenRect;
                // Matrix.set* operations are retarded: they *reset* the entire matrix instead of adjusting either
                // translation or scale. Thus using postTranslate instead of setTranslate.
                mWorkingMatrix.postTranslate(translateToBoundsX(bounds), translateToBoundsY(bounds));

                mMatrix.set(mWorkingMatrix);
                mImageView.setImageMatrix(mMatrix);
                return true;
            }
        };
        mGestureDetector = new GestureDetector(activity, listener);

        ScaleGestureDetector.OnScaleGestureListener scaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector scaleDetector) {
                if (mWorkingRect == null || mInitialRect == null) {
                    // The image is not initialized yet.
                    return false;
                }

                float factor = scaleDetector.getScaleFactor();
                mWorkingMatrix.postScale(factor, factor, scaleDetector.getFocusX(), scaleDetector.getFocusY());

                // Make sure it's not too large or too small: not larger than MAX_SCALE_FACTOR the screen size,
                // and not smaller of either the screen size or the actual image size.
                mWorkingMatrix.mapRect(mWorkingRect, mInitialRect);

                final float width = mWorkingRect.width();
                final float height = mWorkingRect.height();

                // Prevent scaling too much: much bigger than the screen size or overall pixel count too high.
                if (width > mScreenRect.width() * MAX_SCALE_FACTOR ||
                        height > mScreenRect.height() * MAX_SCALE_FACTOR ||
                        width * height > MAX_SCALED_PIXELS) {
                    mWorkingMatrix.set(mMatrix);
                    return true;
                }

                if (/* covers cut out area */(mAvatarUpload && width >= mCutOutRect.width() &&
                        height >= mCutOutRect.height())
                        || (/* not too small */!mAvatarUpload && (width >= mInitialRect.width() ||
                        width >= mScreenRect.width() || height >= mScreenRect.height()))) {
                    mMatrix.set(mWorkingMatrix);
                    mImageView.setImageMatrix(mMatrix);

                } else {
                    // Skip the change: the image is too large or too small already.
                    mWorkingMatrix.set(mMatrix);
                }
                return true;
            }
        };
        mScaleGestureDetector = new ScaleGestureDetector(activity, scaleListener);

        view.setOnTouchListener((v, event) -> {
            if (mWorkingMatrix == null) {
                // The image is invalid. Disable scrolling/panning.
                return false;
            }

            mGestureDetector.onTouchEvent(event);
            mScaleGestureDetector.onTouchEvent(event);
            return true;
        });

        // Send message on button click.
        view.findViewById(R.id.chatSendButton).setOnClickListener(v -> sendImage());
        // Upload avatar.
        view.findViewById(R.id.acceptAvatar).setOnClickListener(v -> acceptAvatar());
        // Send message on Enter.
        ((EditText) view.findViewById(R.id.editMessage)).setOnEditorActionListener(
                (v, actionId, event) -> {
                    sendImage();
                    return true;
                });

        return view;
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
            toolbar.setTitle(R.string.image_preview);
            toolbar.setSubtitle(null);
            toolbar.setLogo(null);
        }

        mAvatarUpload = args.getBoolean(AttachmentHandler.ARG_AVATAR);
        mRemoteState = RemoteState.NONE;

        mMatrix.reset();

        // ImageView is not laid out at this time. Must add an observer to get the size of the view.
        mImageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Ensure we call it only once.
                mImageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mScreenRect = new RectF(0, 0, mImageView.getWidth(), mImageView.getHeight());
                mCutOutRect = new RectF();
                if (mScreenRect.width() > mScreenRect.height()) {
                    mCutOutRect.left = (mScreenRect.width() - mScreenRect.height()) * 0.5f;
                    mCutOutRect.right = mCutOutRect.left + mScreenRect.height();
                    mCutOutRect.top = 0f;
                    mCutOutRect.bottom = mScreenRect.height();
                } else {
                    mCutOutRect.top = (mScreenRect.height() - mScreenRect.width()) * 0.5f;
                    mCutOutRect.bottom = mCutOutRect.top + mScreenRect.width();
                    mCutOutRect.left = 0f;
                    mCutOutRect.right = mScreenRect.width();
                }

                // Load bitmap into ImageView.
                loadImage(activity, args);
            }
        });
    }

    private void loadImage(final Activity activity, final Bundle args) {
        // Check if the bitmap is directly attached.
        int length = 0;
        Bitmap preview = args.getParcelable(AttachmentHandler.ARG_SRC_BITMAP);
        if (preview == null) {
            // Check if bitmap is attached as an array of bytes (received).
            byte[] bits = args.getByteArray(AttachmentHandler.ARG_SRC_BYTES);
            if (bits != null) {
                preview = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                length = bits.length;
            }
        }

        // Preview large image before sending.
        Bitmap bmp = null;
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
                mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                RequestCreator rc = Picasso.get().load(ref)
                        .error(R.drawable.ic_broken_image);
                if (preview != null) {
                    rc = rc.placeholder(new BitmapDrawable(getResources(), preview));
                    // No need to show preview separately from Picasso.
                    preview = null;
                } else {
                    rc = rc.placeholder(R.drawable.ic_image);
                }

                rc.into(mImageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        mRemoteState = RemoteState.SUCCESS;
                        Log.i(TAG, "Remote load: success" + ref);
                        Activity activity = getActivity();
                        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }

                        final Bitmap bmp = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
                        mInitialRect = new RectF(0, 0, bmp.getWidth(), bmp.getHeight());
                        mWorkingRect = new RectF(mInitialRect);
                        mMatrix.setRectToRect(mInitialRect, mScreenRect, Matrix.ScaleToFit.CENTER);
                        mWorkingMatrix = new Matrix(mMatrix);
                        mImageView.setImageMatrix(mMatrix);
                        mImageView.setScaleType(ImageView.ScaleType.MATRIX);
                        mImageView.enableOverlay(false);

                        activity.findViewById(R.id.metaPanel).setVisibility(View.VISIBLE);
                        setupImagePostview(activity, args, bmp.getByteCount());
                    }

                    @Override
                    public void onError(Exception e) {
                        mRemoteState = RemoteState.FAILED;
                        Log.w(TAG, "Failed to fetch image: " + e.getMessage() + " (" + ref + ")");
                        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        ((MenuHost) activity).removeMenuProvider(ImageViewFragment.this);
                    }
                });
            }
        }

        if (bmp == null) {
            bmp = preview;
        }

        if (bmp != null) {
            // Must ensure the bitmap is not too big (some cameras can produce
            // bigger bitmaps that the phone can render)
            bmp = UiUtils.scaleBitmap(bmp, MAX_BITMAP_DIM, MAX_BITMAP_DIM, false);

            mImageView.enableOverlay(mAvatarUpload);

            activity.findViewById(R.id.metaPanel).setVisibility(View.VISIBLE);

            mInitialRect = new RectF(0, 0, bmp.getWidth(), bmp.getHeight());
            mWorkingRect = new RectF(mInitialRect);
            if (length == 0) {
                // The image is being previewed before sending or uploading.
                setupImagePreview(activity);
            } else {
                // The image is downloaded.
                setupImagePostview(activity, args, length);
            }

            mImageView.setImageDrawable(new BitmapDrawable(getResources(), bmp));

            // Scale image appropriately.
            if (mAvatarUpload) {
                // Scale to fill mCutOutRect.
                float scaling = 1f;
                if (mInitialRect.width() < mCutOutRect.width()) {
                    scaling = mCutOutRect.width() / mInitialRect.width();
                }
                if (mInitialRect.height() < mCutOutRect.height()) {
                    scaling = Math.max(scaling, mCutOutRect.height() / mInitialRect.height());
                }
                if (scaling > 1f) {
                    mMatrix.postScale(scaling, scaling, 0, 0);
                }

                // Center scaled image within the mCutOutRect.
                mMatrix.mapRect(mWorkingRect, mInitialRect);
                mMatrix.postTranslate(mCutOutRect.left + (mCutOutRect.width() - mWorkingRect.width()) * 0.5f,
                        mCutOutRect.top + (mCutOutRect.height() - mWorkingRect.height()) * 0.5f);
            } else {
                mMatrix.setRectToRect(mInitialRect, mScreenRect, Matrix.ScaleToFit.CENTER);
            }
        } else if (mRemoteState == RemoteState.NONE) {
            // Local broken image.
            mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mImageView.setImageDrawable(ResourcesCompat.getDrawable(getResources(),
                            R.drawable.ic_broken_image, null));
            activity.findViewById(R.id.metaPanel).setVisibility(View.INVISIBLE);
            ((MenuHost) activity).removeMenuProvider(this);
        }

        mWorkingMatrix = new Matrix(mMatrix);
        mImageView.setImageMatrix(mMatrix);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Picasso.get().cancelRequest(mImageView);
    }

    // Setup fields for image preview.
    private void setupImagePreview(final Activity activity) {
        if (mAvatarUpload) {
            activity.findViewById(R.id.acceptAvatar).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.sendImagePanel).setVisibility(View.GONE);
        } else {
            activity.findViewById(R.id.acceptAvatar).setVisibility(View.GONE);
            activity.findViewById(R.id.sendImagePanel).setVisibility(View.VISIBLE);
        }
        activity.findViewById(R.id.annotation).setVisibility(View.GONE);
        ((MenuHost) activity).removeMenuProvider(this);
    }

    // Setup fields for viewing downloaded image
    private void setupImagePostview(final Activity activity, Bundle args, long length) {
        String fileName = args.getString(AttachmentHandler.ARG_FILE_NAME);
        if (TextUtils.isEmpty(fileName)) {
            fileName = getResources().getString(R.string.tinode_image);
        }

        // The received image is viewed.
        String size = ((int) mInitialRect.width()) + " \u00D7 " + ((int) mInitialRect.height()) + "; ";
        activity.findViewById(R.id.sendImagePanel).setVisibility(View.GONE);
        activity.findViewById(R.id.annotation).setVisibility(View.VISIBLE);
        ((TextView) activity.findViewById(R.id.content_type)).setText(args.getString("mime"));
        ((TextView) activity.findViewById(R.id.file_name)).setText(fileName);
        ((TextView) activity.findViewById(R.id.image_size))
                .setText(String.format("%s%s", size, UiUtils.bytesToHumanSize(length)));
        ((MenuHost) activity).addMenuProvider(this, getViewLifecycleOwner(),
                Lifecycle.State.RESUMED);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_download, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        final Activity activity = requireActivity();

        if (item.getItemId() == R.id.action_download) {
            // Save image to Gallery.
            Bundle args = getArguments();
            String filename = null;
            if (args != null) {
                filename = args.getString(AttachmentHandler.ARG_FILE_NAME);
            }
            if (TextUtils.isEmpty(filename)) {
                filename = getResources().getString(R.string.tinode_image);
                filename += "" + (System.currentTimeMillis() % 10000);
            }
            Bitmap bmp = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
            String savedAt = MediaStore.Images.Media.insertImage(activity.getContentResolver(), bmp,
                    filename, null);
            Toast.makeText(activity, savedAt != null ? R.string.image_download_success :
                    R.string.failed_to_save_download, Toast.LENGTH_LONG).show();

            return true;
        }

        return false;
    }

    private void sendImage() {
        final MessageActivity activity = (MessageActivity) requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
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

        AttachmentHandler.enqueueMsgAttachmentUploadRequest(activity, AttachmentHandler.ARG_OPERATION_IMAGE, args);

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

        Matrix inverse = new Matrix();
        mMatrix.invert(inverse);
        RectF cutOut = new RectF(mCutOutRect);
        inverse.mapRect(cutOut);

        if (cutOut.width() < Const.MIN_AVATAR_SIZE || cutOut.height() < Const.MIN_AVATAR_SIZE) {
            // Avatar is too small.
            Toast.makeText(activity, R.string.image_too_small, Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap bmp = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
        if (bmp != null) {
            // Make sure cut out rectangle is fully inside the bitmap.
            cutOut.intersect(0, 0, bmp.getWidth(), bmp.getHeight());
            // Actually make the cut.
            bmp = Bitmap.createBitmap(bmp, (int) cutOut.left, (int) cutOut.top,
                    (int) cutOut.width(), (int) cutOut.height());
        }

        String topicName = args.getString(Const.INTENT_EXTRA_TOPIC);
        ((AvatarCompletionHandler) activity).onAcceptAvatar(topicName, bmp);

        activity.getSupportFragmentManager().popBackStack();
    }

    private float translateToBoundsX(RectF bounds) {
        float left = mWorkingRect.left;
        if (mWorkingRect.width() >= bounds.width()) {
            // Image wider than the viewport.
            left = Math.max(Math.min(bounds.left, left), bounds.left + bounds.width() - mWorkingRect.width());
        } else {
            left = Math.min(Math.max(bounds.left, left), bounds.left + bounds.width() - mWorkingRect.width());
        }
        return left - mWorkingRect.left;
    }

    private float translateToBoundsY(RectF bounds) {
        float top = mWorkingRect.top;
        if (mWorkingRect.height() >= bounds.height()) {
            // Image taller than the viewport.
            top = Math.max(Math.min(bounds.top, top), bounds.top + bounds.height() - mWorkingRect.height());
        } else {
            top = Math.min(Math.max(bounds.top, top), bounds.top + bounds.height() - mWorkingRect.height());
        }
        return top - mWorkingRect.top;
    }

    public interface AvatarCompletionHandler {
        void onAcceptAvatar(String topicName, Bitmap avatar);
    }
}
