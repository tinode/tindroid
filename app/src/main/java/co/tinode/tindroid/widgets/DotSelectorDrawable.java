package co.tinode.tindroid.widgets;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import co.tinode.tindroid.R;

/**
 * Drawable showing several gray dots arranged vertically with one of them being different color.
 * Used to indicate currently shown pinned message.
 */
public class DotSelectorDrawable extends Drawable {
    private final Paint mPaintSelected;
    private final Paint mPaintNormal;
    private int mDotCount;
    private int mSelected;

    public DotSelectorDrawable(Resources res, int dots, int selected) {
        super();

        mDotCount = dots;
        mSelected = selected;

        mPaintSelected = new Paint();
        mPaintSelected.setAntiAlias(true);
        mPaintSelected.setDither(true);
        mPaintSelected.setColor(res.getColor(R.color.colorAccent, null));

        mPaintNormal = new Paint();
        mPaintNormal.setAntiAlias(true);
        mPaintNormal.setDither(true);
        mPaintNormal.setColor(res.getColor(R.color.colorGray, null));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float yStep = (float) bounds.height() / (mDotCount + 1);
        float radius = Math.min(bounds.width() / 4.0f, yStep - 4f);
        int x = bounds.centerX();
        int yStart = (int) (bounds.top + yStep);
        int selected = mDotCount - mSelected - 1;
        for (int i = 0; i < mDotCount; i++) {
            canvas.drawCircle(x, yStart + yStep * i,
                    radius + (i == selected ? 1 : 0),
                    i == selected ? mPaintSelected : mPaintNormal);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        boolean changed = false;
        if (mPaintSelected.getAlpha() != alpha) {
            mPaintSelected.setAlpha(alpha);
            changed = true;
        }
        if (mPaintNormal.getAlpha() != alpha) {
            mPaintNormal.setAlpha(alpha);
            changed = true;
        }
        if (changed) {
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaintSelected.setColorFilter(colorFilter);
        mPaintNormal.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void setDotCount(int count) {
        if (mDotCount != count) {
            mDotCount = count;
            invalidateSelf();
        }
    }

    public void setSelected(int index) {
        if (mSelected != index) {
            mSelected = index;
            invalidateSelf();
        }
    }
}
