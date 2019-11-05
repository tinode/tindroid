package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Fragment for expanded display of an inline image.
 */
public class ImageViewFragment extends Fragment {
    private static final String TAG = "ImageViewFragment";

    // The matrix actually used for scaling.
    private Matrix mMatrix;
    // Working matrix for pre-testing image bounds.
    private Matrix mWorkingMatrix;
    // Initial image bounds before any zooming and scaling.
    private RectF mInitialRect;
    // Screen bounds
    private RectF mScreenRect;
    // Working rectangle for testing image bounds after pannig and zooming.
    private RectF mWorkingRect;
    // Center of the screen.
    private PointF mScreenCenter;

    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private ImageView mImageView;

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
                // Ignore pan if the image is too small. It should be pinned to the center of the screen.
                // If it's large, make sure it covers the entire screen.
                mWorkingMatrix.mapRect(mWorkingRect, mInitialRect);
                if (mWorkingRect.width() > mScreenRect.width()) {
                    // Matrix.set* operations are retarded: they *reset* the entire matrix instead of adjusting either translation or scale.
                    // Thus using postTranslate instead of setTranslate.
                    float left = Math.max(Math.min(0f, mWorkingRect.left), mScreenRect.width() - mWorkingRect.width());
                    float top = Math.max(Math.min(0f, mWorkingRect.top), mScreenRect.height() - mWorkingRect.height());
                    mWorkingMatrix.postTranslate(left - mWorkingRect.left, top - mWorkingRect.top);

                    mMatrix.set(mWorkingMatrix);
                    mImageView.setImageMatrix(mMatrix);
                } else {
                    // Skip change because the image is too small and fits the screen.
                    mWorkingMatrix.set(mMatrix);
                }
                return true;
            }
        };
        mGestureDetector = new GestureDetector(activity, listener);

        ScaleGestureDetector.OnScaleGestureListener scaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector scaleDetector) {
                float factor = scaleDetector.getScaleFactor();
                mWorkingMatrix.postScale(factor, factor, mScreenCenter.x, mScreenCenter.y);
                //Make sure it's not too large or too small: not larger than 10x the screen size,
                // and not smaller of either the screen size or actual image size.
                mWorkingMatrix.mapRect(mWorkingRect, mInitialRect);
                if (mWorkingRect.width() < mScreenRect.width() * 10f &&
                        (mWorkingRect.width() >= mInitialRect.width() || mWorkingRect.width() > mScreenRect.width())) {

                    float left = mScreenCenter.x - mWorkingRect.width() * 0.5f;
                    float top = mScreenCenter.y - mWorkingRect.height() * 0.5f;
                    mWorkingMatrix.postTranslate(left - mWorkingRect.left, top - mWorkingRect.top);

                    mMatrix.set(mWorkingMatrix);
                    mImageView.setImageMatrix(mMatrix);
                } else {
                    // Skip change: the image is too large or too small already.
                    mWorkingMatrix.set(mMatrix);
                }

                return true;
            }
        };
        mScaleGestureDetector = new ScaleGestureDetector(activity, scaleListener);

        view.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                mGestureDetector.onTouchEvent(event);
                mScaleGestureDetector.onTouchEvent(event);
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

        mMatrix.reset();

        byte[] bits = args.getByteArray("image");
        if (bits != null) {
            Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
            mInitialRect = new RectF(0, 0, bmp.getWidth(), bmp.getHeight());
            mWorkingRect = new RectF(mInitialRect);
            String size = ((int) mInitialRect.width()) + " \u00D7 " + ((int) mInitialRect.height()) + "; ";

            mImageView.setImageDrawable(new BitmapDrawable(getResources(), bmp));
            ((TextView) activity.findViewById(R.id.content_type)).setText(args.getString("mime"));
            ((TextView) activity.findViewById(R.id.file_name)).setText(args.getString("name"));
            ((TextView) activity.findViewById(R.id.image_size)).setText(size + UiUtils.bytesToHumanSize(bits.length));

            // ImageView size is set later. Must add an observer to get the size.
            mImageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // Ensure we call it only once.
                    mImageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mScreenCenter = new PointF(mImageView.getWidth() * 0.5f, mImageView.getHeight() * 0.5f);
                    mScreenRect = new RectF(0, 0, mImageView.getWidth(), mImageView.getHeight());
                    mMatrix.setRectToRect(mInitialRect, mScreenRect, Matrix.ScaleToFit.CENTER);
                    mWorkingMatrix = new Matrix(mMatrix);

                    mImageView.setImageMatrix(mMatrix);
                }
            });
        } // TODO: show broken image here.
    }
}
