package co.tinode.tindroid;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

/**
 * Helper class to draw an online presence indicator over avatar.
 */
public class OnlineDrawable extends Drawable {
    private boolean mOnline;
    private Paint mPaint;

    public OnlineDrawable(boolean online) {
        super();
        mOnline = online;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(online ? UiUtils.COLOR_ONLINE : UiUtils.COLOR_OFFLINE);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float radius = bounds.width() / 8.0f;
        canvas.drawCircle(bounds.right - radius, bounds.bottom - radius, radius, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        if (mPaint.getAlpha() != alpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void setOnline(boolean online) {
        if (mOnline != online) {
            mOnline = online;
            mPaint.setColor(online ? UiUtils.COLOR_ONLINE : UiUtils.COLOR_OFFLINE);
            invalidateSelf();
        }
    }
}
