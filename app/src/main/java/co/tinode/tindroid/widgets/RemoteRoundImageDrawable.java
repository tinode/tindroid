package co.tinode.tindroid.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import coil.Coil;
import coil.request.ImageRequest;
import coil.size.Scale;
import coil.target.Target;

/**
 * A Drawable that loads an image from a URL and displays it as a circle.
 * Uses Coil for async image loading.
 */
public class RemoteRoundImageDrawable extends Drawable implements Target {
    private static final String TAG = "RemoteRoundImageDrawable";
    private static final Matrix sMatrix = new Matrix();

    private final WeakReference<View> mParentRef;
    private final Context mContext;
    private final int mSize;
    private final Paint mPaint = new Paint();
    private final Drawable mPlaceholder;

    private Bitmap mBitmap;
    private Rect mBitmapRect;
    private URL mSource;

    /**
     * Create a drawable that loads an image from URL and displays it as a circle.
     *
     * @param context     Context for Coil image loading.
     * @param parent      Parent view to invalidate when image loads.
     * @param size        Size of the drawable (width and height).
     * @param placeholder Placeholder drawable to show while loading.
     */
    public RemoteRoundImageDrawable(@NonNull Context context, @NonNull View parent,
                                    int size, @NonNull Drawable placeholder) {
        mContext = context;
        mParentRef = new WeakReference<>(parent);
        mSize = size;
        mPlaceholder = placeholder;
        mPlaceholder.setBounds(0, 0, size, size);

        mPaint.setAntiAlias(true);
        mPaint.setFilterBitmap(true);
        mPaint.setDither(true);
    }

    /**
     * Start loading the image from the given URL.
     *
     * @param url URL to load the image from.
     */
    public void load(@NonNull URL url) {
        mSource = url;
        ImageRequest.Builder req = new ImageRequest.Builder(mContext)
                .data(Uri.parse(url.toString()))
                .size(mSize, mSize)
                .scale(Scale.FILL)
                .target(this);
        Coil.imageLoader(mContext).enqueue(req.build());
    }

    @Override
    public void onSuccess(@NonNull Drawable drawable) {
        View parent = mParentRef.get();
        if (parent != null) {
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                mBitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                if (mBitmap != null) {
                    mBitmapRect = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
                    mPaint.setShader(new BitmapShader(mBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                }
            }
            parent.postInvalidate();
        }
    }

    @Override
    public void onError(@Nullable Drawable errorDrawable) {
        Log.w(TAG, "Failed to load image: " + mSource);
        View parent = mParentRef.get();
        if (parent != null) {
            parent.postInvalidate();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect dst = getBounds();
        if (mBitmap != null && mBitmapRect != null) {
            // Draw the loaded bitmap as a circle
            BitmapShader shader = (BitmapShader) mPaint.getShader();
            if (shader == null) {
                shader = new BitmapShader(mBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
            // Fit bitmap to the bounds of the drawable.
            sMatrix.reset();
            float scale = Math.max((float) dst.width() / mBitmapRect.width(),
                    (float) dst.height() / mBitmapRect.height());
            sMatrix.postScale(scale, scale);
            // Translate bitmap to dst bounds.
            sMatrix.postTranslate(dst.left, dst.top);
            shader.setLocalMatrix(sMatrix);
            mPaint.setShader(shader);
            canvas.drawCircle(dst.centerX(), dst.centerY(), dst.width() * 0.5f, mPaint);
        } else {
            // Draw placeholder
            mPlaceholder.setBounds(dst);
            mPlaceholder.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (mPaint.getAlpha() != alpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return mSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize;
    }
}

