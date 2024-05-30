package co.tinode.tindroid.format;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.style.ReplacementSpan;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.net.URL;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import coil.Coil;
import coil.request.ImageRequest;
import coil.size.Scale;
import coil.target.Target;

/**
 * Spannable which updates associated image as it's loaded from the given URL.
 * An optional drawable overlay can be shown on top of the loaded bitmap.
 */
public class RemoteImageSpan extends ReplacementSpan implements Target {
    private static final String TAG = "RemoteImageSpan";

    private final WeakReference<View> mParentRef;
    private final Drawable mOnError;
    private final int mWidth;
    private final int mHeight;
    private final boolean mCropCenter;
    private URL mSource = null;
    private Drawable mDrawable;
    private Drawable mOverlay;

    public RemoteImageSpan(View parent, int width, int height, boolean cropCenter,
                           @NonNull Drawable placeholder, @NonNull Drawable errorDrawable) {
        mParentRef = new WeakReference<>(parent);
        mWidth = width;
        mHeight = height;
        mCropCenter = cropCenter;
        mOnError = errorDrawable;
        mOnError.setBounds(0, 0, width, height);
        mDrawable = placeholder;
        mDrawable.setBounds(0, 0, width, height);
        mOverlay = null;
    }

    public void load(URL from) {
        Context context = mParentRef.get().getContext();
        mSource = from;
        ImageRequest.Builder req = new ImageRequest.Builder(context)
                .data(Uri.parse(from.toString()))
                .size(mWidth, mHeight);
        if (mCropCenter) {
            req.scale(Scale.FILL);
        }
        req.target(this);
        Coil.imageLoader(context).enqueue(req.build());
    }

    @Override
    public void onSuccess(@NonNull Drawable drawable) {
        View parent = mParentRef.get();
        if (parent != null) {
            mDrawable = drawable;
            mDrawable.setBounds(0, 0, mWidth, mHeight);
            parent.postInvalidate();
        }
    }

    @Override
    public void onError(Drawable errorDrawable) {
        Log.w(TAG, "Failed to get image: " + mSource);
        View parent = mParentRef.get();
        if (parent != null) {
            mDrawable = mOnError;
            parent.postInvalidate();
        }
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text,
                       int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            fm.descent = mHeight / 3;
            fm.ascent = - fm.descent * 2;

            fm.top = fm.ascent;
            fm.bottom = fm.descent;
        }
        return mWidth;
    }

    @Override
    // This has to be overridden because of brain-damaged design of DynamicDrawableSpan:
    // it caches Drawable and the cache cannot be invalidated.
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     @IntRange(from = 0) int start, @IntRange(from = 0) int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        Drawable b = mDrawable;
        if (b != null) {
            canvas.save();
            canvas.translate(x, bottom - b.getBounds().bottom);
            b.draw(canvas);
            if (mOverlay != null) {
                mOverlay.draw(canvas);
            }
            canvas.restore();
        }
    }

    // Add optional overlay which will be displayed over the loaded bitmap drawable.
    public void setOverlay(@Nullable Drawable overlay) {
        mOverlay = overlay;
        if (mOverlay != null) {
            mOverlay.setBounds(0, 0, mWidth, mHeight);
        }
        View parent = mParentRef.get();
        if (parent != null) {
            parent.postInvalidate();
        }
    }
}
