package co.tinode.tindroid.format;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// ImageSpan with vertical alignment and padding. Only 'vertical-align: middle' is currently supported.
public class StyledImageSpan extends ImageSpan {
    private WeakReference<Drawable> mDrawable;
    private final RectF mPadding;

    public StyledImageSpan(@NonNull Drawable drawable, @Nullable RectF padding) {
        super(drawable);

        mPadding = padding == null ? new RectF() : padding;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        Drawable drawable = getCachedDrawable();
        Rect bounds = drawable.getBounds();

        if (fm != null) {
            fm.descent = bounds.height()/3 + (int) mPadding.bottom;
            fm.ascent = - fm.descent * 2 - (int) mPadding.top;

            fm.top = fm.ascent;
            fm.bottom = fm.descent;
        }

        return bounds.width() + (int) (mPadding.left + mPadding.right);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        Drawable drawable = getCachedDrawable();
        canvas.save();
        float dY = top + (bottom - top) * 0.5f - drawable.getBounds().height() * 0.5f;
        canvas.translate(x + mPadding.left, dY + mPadding.top);
        drawable.draw(canvas);
        canvas.restore();
    }

    private Drawable getCachedDrawable() {
        WeakReference<Drawable> ref = mDrawable;
        Drawable drawable = ref != null ? ref.get() : null;
        if (drawable == null) {
            drawable = getDrawable();
            mDrawable = new WeakReference<>(drawable);
        }
        return drawable;
    }
}
