package co.tinode.tindroid.format;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;

// ImageSpan with vertical alignment.
public class AlignedImageSpan extends ImageSpan {
    private WeakReference<Drawable> mDrawable;

    public AlignedImageSpan(@NonNull Drawable drawable) {
        super(drawable);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        Drawable drawable = getCachedDrawable();
        Rect bounds = drawable.getBounds();

        if (fm != null) {
            fm.descent = bounds.height()/3;
            fm.ascent = -fm.descent * 2;

            fm.top = fm.ascent;
            fm.bottom = fm.descent;
        }

        return bounds.width();
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        Drawable drawable = getCachedDrawable();
        canvas.save();
        float dY = top + (bottom - top) * 0.5f - drawable.getBounds().height() * 0.5f;
        canvas.translate(x, dY);
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
