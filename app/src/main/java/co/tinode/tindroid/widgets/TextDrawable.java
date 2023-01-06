package co.tinode.tindroid.widgets;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.TypedValue;

import androidx.annotation.ColorInt;

// Drawable with a single line of text.
public class TextDrawable extends Drawable {
    private static final int DEFAULT_COLOR = Color.WHITE;
    private static final int DEFAULT_TEXT_SIZE = 15;

    private final TextPaint mPaint;
    private CharSequence mText;
    private int mIntrinsicWidth;
    private int mIntrinsicHeight;
    private int mTextSize;

    public TextDrawable(Resources res, CharSequence text) {
        mPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextSize = DEFAULT_TEXT_SIZE;
        mPaint.setColor(DEFAULT_COLOR);
        mPaint.density = res.getDisplayMetrics().density;
        setText(res, text);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.drawText(mText, 0, mText.length(),
                bounds.centerX() - mIntrinsicWidth / 2f,
                bounds.centerY() - mPaint.getFontMetrics().ascent / 2f, // ascent is negative.
                mPaint);
    }

    @Override
    public int getOpacity() {
        return mPaint.getAlpha();
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
    }

    public void setText(Resources res, CharSequence text) {
        mText = text;
        setTextSize(res, mTextSize);
    }

    public void setTextSize(Resources res, int size) {
        mTextSize = size;
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mTextSize, res.getDisplayMetrics());
        mPaint.setTextSize(textSize);
        mIntrinsicWidth = (int) (mPaint.measureText(mText, 0, mText.length()) + .5);
        mIntrinsicHeight = mPaint.getFontMetricsInt(null);
    }

    public void setTextColor(@ColorInt int color) {
        mPaint.setColor(color);
    }
}
