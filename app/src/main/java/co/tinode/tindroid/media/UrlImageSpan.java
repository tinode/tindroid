package co.tinode.tindroid.media;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.style.DynamicDrawableSpan;
import android.util.Log;
import android.view.View;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.lang.ref.WeakReference;
import java.net.URL;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

/* Spannable which updates associated image as it's loaded from the given URL */
public class UrlImageSpan extends DynamicDrawableSpan implements Target {
    private static final String TAG = "UrlImageSpan";

    private final WeakReference<View> mParentRef;
    private Drawable mDrawable;
    private final Drawable mOnError;
    private final int mWidth;
    private final int mHeight;

    public UrlImageSpan(View parent, int width, int height, Drawable placeholder, Drawable onError) {
        mParentRef = new WeakReference<>(parent);
        mWidth = width;
        mHeight = height;
        mOnError = onError;
        mDrawable = SpanFormatter.getPlaceholder(parent.getContext(), placeholder, mWidth, mHeight);
    }

    public void load(URL from) {
        Picasso.get().load(Uri.parse(from.toString())).resize(mWidth, mHeight).into(this);
    }

    @Override
    public Drawable getDrawable() {
        return mDrawable;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        View parent = mParentRef.get();
        if (parent != null) {
            mDrawable = new BitmapDrawable(parent.getResources(), bitmap);
            mDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            parent.postInvalidate();
        }
    }

    @Override
    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
        View parent = mParentRef.get();
        if (parent != null) {
            mDrawable = SpanFormatter.getPlaceholder(parent.getContext(), mOnError, mWidth, mHeight);
            Log.i(TAG, "Failed to load bitmap", e);
            parent.postInvalidate();
        }
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text,
                       int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            fm.ascent = -mHeight;
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return mWidth;
    }

    @Override
    // This has to be overridden because of brain-damaged design of DynamicDrawableSpan:
    // it caches Drawable and the cache cannot be invalidated.
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     @IntRange(from = 0) int start, @IntRange(from = 0) int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        Drawable b = getDrawable();
        canvas.save();
        int transY = bottom - b.getBounds().bottom;
        if (mVerticalAlignment == ALIGN_BASELINE) {
            transY -= paint.getFontMetricsInt().descent;
        } else if (mVerticalAlignment == ALIGN_CENTER) {
            transY = top + (bottom - top) / 2 - b.getBounds().height() / 2;
        }
        canvas.translate(x, transY);
        b.draw(canvas);
        canvas.restore();
    }
}
