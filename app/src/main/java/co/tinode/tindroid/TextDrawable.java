package co.tinode.tindroid;


import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;


/**
 * Drawable to create rounded images with initials inside.
 */
public class TextDrawable extends ShapeDrawable {
    private String mText;
    // Text paint
    private Paint mPaint = null;

    // private int mIntrinsicWidth;
    // private int mIntrinsicHeight;

    private static final float FONT_SCALE = 0.6f;
    private static final int MIN_FONT_SIZE = 10;

    public TextDrawable(String text, int colorBg, int colorText) {
        super(new OvalShape());

        mText = text;
        // Background color
        getPaint().setColor(colorBg);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Text color
        mPaint.setColor(colorText);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }

        super.draw(canvas);

        canvas.drawText(mText, 0, mText.length(),
                bounds.centerX(), bounds.centerY(), mPaint);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        int height = bounds.height();
        if (height < MIN_FONT_SIZE) {
            height = MIN_FONT_SIZE;
        }

        mPaint.setTextSize(height * FONT_SCALE);

        // mIntrinsicWidth = (int) (mPaint.measureText(mText, 0, mText.length()) + .5);
        // mIntrinsicHeight = mPaint.getFontMetricsInt(null);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }
    @Override
    public int getIntrinsicHeight() {
        return -1;
    }
    @Override
    public void setAlpha(int alpha) {
        super.setAlpha(alpha);
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
    }
}
