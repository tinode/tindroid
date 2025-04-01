package co.tinode.tindroid.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;
import co.tinode.tindroid.R;

/**
 * ImageView with a circular cutout for previewing avatars.
 */
public class OverlaidImageView extends AppCompatImageView {
    private final Paint mBackgroundPaint;
    private final Path mClipPath;
    private boolean mShowOverlay = false;

    public OverlaidImageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setColor(getResources().getColor(R.color.colorImagePreviewBg, null));
        mBackgroundPaint.setAlpha(0xCC);

        mClipPath = new Path();
    }

    /**
     * Show or hide circular image overlay.
     *
     * @param on true to show, false to hide
     */
    public void enableOverlay(boolean on) {
        mShowOverlay = on;
    }

    @Override
    public void onDraw(Canvas canvas) {
        // Draw image.
        super.onDraw(canvas);

        // Draw background with circular cutout.
        if (mShowOverlay) {
            final int width = getWidth();
            final int height = getHeight();
            final int minDimension = Math.min(width, height);

            mClipPath.reset();
            mClipPath.addCircle(width * 0.5f, height * 0.5f, minDimension * 0.5f, Path.Direction.CW);
            canvas.clipPath(mClipPath);
            canvas.drawPaint(mBackgroundPaint);
        }
    }
}
