package co.tinode.tindroid.widgets;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

/**
 * Helper class to draw an online presence indicator over avatar.
 */
public class OnlineDrawable extends Drawable {
    private static final int COLOR_ONLINE = Color.argb(255, 0x40, 0xC0, 0x40);
    private static final int COLOR_OFFLINE = Color.argb(255, 0xC0, 0xC0, 0xC0);

    private int mColorOnline;
    private int mColorOffline;

    private Boolean mOnline;
    private Paint mPaint;

    public OnlineDrawable(boolean online) {
        super();
        mOnline = online;

        mColorOnline = COLOR_ONLINE;
        mColorOffline = COLOR_OFFLINE;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(online ? COLOR_ONLINE : COLOR_OFFLINE);
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
            mPaint.setColor(online ? mColorOnline : mColorOffline);
            invalidateSelf();
        }
    }

    public void setColors(@ColorInt int on, @ColorInt int off) {
        if (mOnline) {
            if (mColorOnline != on) {
                mPaint.setColor(on);
                invalidateSelf();
            }
        } else {
            if (mColorOffline != off) {
                mPaint.setColor(off);
                invalidateSelf();
            }
        }
        mColorOffline = off;
        mColorOnline = on;
    }
}
