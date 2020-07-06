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
import android.view.KeyEvent;
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

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import co.tinode.tindroid.widgets.OverlaidImageView;
import co.tinode.tinodesdk.MeTopic;

/**
 * Fragment for expanded display of an image: being attached or received.
 */
public class ImageViewFragment extends Fragment {
    private static final String TAG = "ImageViewFragment";

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
    // Minimum scaling factor
    // private float mMinScalingFactor = 1f;

    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private OverlaidImageView mImageView;
    // This is an avatar preview before upload.
    private boolean mAvatarUpload;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final Activity activity = getActivity();

        View view = inflater.inflate(R.layout.fragment_view_image, container, false);
        mMatrix = new Matrix();
        mImageView = view.findViewById(R.id.image);
        mImageView.setImageMatrix(mMatrix);

        GestureDetector.OnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dX, float dY) {
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
            public boolean onScale(ScaleGestureDetector scaleDetector) {
                float factor = scaleDetector.getScaleFactor();
                mWorkingMatrix.postScale(factor, factor, scaleDetector.getFocusX(), scaleDetector.getFocusY());

                // Make sure it's not too large or too small: not larger than 10x the screen size,
                // and not smaller of either the screen size or the actual image size.
                mWorkingMatrix.mapRect(mWorkingRect, mInitialRect);
                if ((/* max size */ mWorkingRect.width() < mScreenRect.width() * 10f) &&
                        (/* covers cut out area */(mAvatarUpload
                                && mWorkingRect.width() >= mCutOutRect.width()
                                && mWorkingRect.height() >= mCutOutRect.height())
                                || (/* not too small */!mAvatarUpload
                                && (mWorkingRect.width() >= mInitialRect.width()
                                        || mWorkingRect.width() >= mScreenRect.width()
                                        || mWorkingRect.height() >= mScreenRect.height())))) {

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

        view.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View v, MotionEvent event) {
                if (mWorkingMatrix == null) {
                    // The image is invalid. Disable scrolling/panning.
                    return false;
                }

                mGestureDetector.onTouchEvent(event);
                mScaleGestureDetector.onTouchEvent(event);
                return true;
            }
        });

        // Send message on button click.
        view.findViewById(R.id.chatSendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendImage();
            }
        });
        // Upload avatar.
        view.findViewById(R.id.acceptAvatar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                acceptAvatar();
            }
        });
        // Send message on Enter.
        ((EditText) view.findViewById(R.id.editMessage)).setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        sendImage();
                        return true;
                    }
                });

        return view;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        Bundle args = getArguments();
        if (activity == null || args == null) {
            return;
        }

        mAvatarUpload = args.getBoolean(AttachmentHandler.ARG_AVATAR);

        mMatrix.reset();

        Bitmap bmp = null;
        byte[] bits = args.getByteArray(AttachmentHandler.ARG_SRC_BYTES);
        if (bits != null) {
            // View received image.
            bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
        } else {
            // Preview before sending.
            Uri uri = args.getParcelable(AttachmentHandler.ARG_SRC_URI);
            if (uri != null) {
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
            }
        }

        if (bmp != null) {
            String filename = args.getString(AttachmentHandler.ARG_FILE_NAME);
            if (TextUtils.isEmpty(filename)) {
                filename = getResources().getString(R.string.tinode_image);
            }

            mImageView.enableOverlay(mAvatarUpload);

            activity.findViewById(R.id.metaPanel).setVisibility(View.VISIBLE);

            mInitialRect = new RectF(0, 0, bmp.getWidth(), bmp.getHeight());
            mWorkingRect = new RectF(mInitialRect);
            if (bits == null) {
                // The image is being previewed before sending or uploading.
                if (mAvatarUpload) {
                    activity.findViewById(R.id.acceptAvatar).setVisibility(View.VISIBLE);
                    activity.findViewById(R.id.sendImagePanel).setVisibility(View.GONE);
                } else {
                    activity.findViewById(R.id.acceptAvatar).setVisibility(View.GONE);
                    activity.findViewById(R.id.sendImagePanel).setVisibility(View.VISIBLE);
                }
                activity.findViewById(R.id.annotation).setVisibility(View.GONE);
                setHasOptionsMenu(false);
            } else {
                // The received image is viewed.
                String size = ((int) mInitialRect.width()) + " \u00D7 " + ((int) mInitialRect.height()) + "; ";
                activity.findViewById(R.id.sendImagePanel).setVisibility(View.GONE);
                activity.findViewById(R.id.annotation).setVisibility(View.VISIBLE);
                ((TextView) activity.findViewById(R.id.content_type)).setText(args.getString("mime"));
                ((TextView) activity.findViewById(R.id.file_name)).setText(filename);
                ((TextView) activity.findViewById(R.id.image_size)).setText(size + UiUtils.bytesToHumanSize(bits.length));
                setHasOptionsMenu(true);
            }

            mImageView.setImageDrawable(new BitmapDrawable(getResources(), bmp));

            // ImageView size is set later. Must add an observer to get the size.
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
                            mMatrix.postScale(scaling, scaling,0, 0);
                        }

                        // Center scaled image within the mCutOutRect.
                        mMatrix.mapRect(mWorkingRect, mInitialRect);
                        mMatrix.postTranslate(mCutOutRect.left + (mCutOutRect.width() - mWorkingRect.width()) * 0.5f,
                                mCutOutRect.top + (mCutOutRect.height() - mWorkingRect.height()) * 0.5f);
                    } else {
                        mMatrix.setRectToRect(mInitialRect, mScreenRect, Matrix.ScaleToFit.CENTER);
                    }
                    mWorkingMatrix = new Matrix(mMatrix);

                    mImageView.setImageMatrix(mMatrix);
                }
            });
        } else {
            // Show broken image.
            mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_broken_image));
            activity.findViewById(R.id.metaPanel).setVisibility(View.INVISIBLE);

            setHasOptionsMenu(false);
        }
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
            MediaStore.Images.Media.insertImage(activity.getContentResolver(), bmp, filename, null);
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void sendImage() {
        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity == null) {
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

        AttachmentHandler.enqueueUploadRequest(activity, AttachmentHandler.ARG_OPERATION_IMAGE, args);

        activity.getSupportFragmentManager().popBackStack();
    }

    private void acceptAvatar() {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
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

        Bitmap bmp = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
        if (bmp != null) {
            bmp = Bitmap.createBitmap(bmp, (int)cutOut.left, (int)cutOut.top,
                    (int)cutOut.width(), (int)cutOut.height());
        }
        // TODO: upload avatar

        final MeTopic me = Cache.getTinode().getMeTopic();
        // noinspection unchecked
        UiUtils.updateAvatar(activity, me, bmp);

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
        float top =  mWorkingRect.top;
        if (mWorkingRect.height() >= bounds.height()) {
            // Image taller than the viewport.
            top = Math.max(Math.min(bounds.top, top), bounds.top + bounds.height() - mWorkingRect.height());
        } else {
            top = Math.min(Math.max(bounds.top, top), bounds.top + bounds.height() - mWorkingRect.height());
        }
        return top - mWorkingRect.top;
    }
}
