package co.tinode.tindroid.media;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.annotation.NonNull;
import android.text.style.ReplacementSpan;

public class BorderedSpan extends ReplacementSpan {
    private static final String TAG = "BorderedSpan";

    private static final float RADIUS_CORNER = 4f;
    private static final float SHADOW_SIZE = 3f;
    private static final float BORDER_WIDTH = 3f;

    // Minimum button width in '0' characters.
    private static final int MIN_BUTTON_WIDTH = 10;
    private final Paint mPaintBorder, mPaintBackground;
    private float mHeight;
    private int mWidth;
    private int mWidthActual;
    private int mTextColor;
    private int mMinButtonWidth;

    BorderedSpan(final Context context, final float lineHeight, final float charWidth) {
        mPaintBorder = new Paint();
        mPaintBorder.setStyle(Paint.Style.STROKE);
        mPaintBorder.setStrokeWidth(BORDER_WIDTH);
        mPaintBorder.setAntiAlias(true);
        mPaintBorder.setColor(Color.argb(0xFF, 0xCC, 0xCC, 0xEE));

        mPaintBackground = new Paint();
        mPaintBackground.setStyle(Paint.Style.FILL);
        mPaintBackground.setAntiAlias(true);
        mPaintBackground.setColor(Color.rgb(0xEE, 0xEE, 0xFF));
        mPaintBackground.setShadowLayer(SHADOW_SIZE, SHADOW_SIZE * 0.5f, SHADOW_SIZE * 0.5f,
                Color.argb(0x80, 0, 0, 0));

        int[] attrs = {android.R.attr.textColorLink};
        TypedArray colors = context.obtainStyledAttributes(attrs);
        mTextColor = colors.getColor(0, 0x7bc9c2);
        colors.recycle();

        mHeight = lineHeight - charWidth * 0.1f;
        mMinButtonWidth = (int) (MIN_BUTTON_WIDTH * charWidth);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        // Actual text width;
        mWidthActual = (int) paint.measureText(text, start, end);

        // Width of the button.
        int len = text.length();
        // Add ~2 char padding left and right.
        mWidth = mWidthActual * (len+4) / len;
        // Ensure minimum width of the button.
        mWidth = mWidth < mMinButtonWidth ? mMinButtonWidth : mWidth;
        return mWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        float textHeight = paint.descent() - paint.ascent();
        RectF outline = new RectF(x, top, x + mWidth, top + mHeight);
        outline.inset(SHADOW_SIZE, SHADOW_SIZE);

        // Draw colored background
        canvas.drawRoundRect(outline, RADIUS_CORNER, RADIUS_CORNER, mPaintBackground);
        // Draw border
        canvas.drawRoundRect(outline, RADIUS_CORNER, RADIUS_CORNER, mPaintBorder);
        // Don't underline the text.
        paint.setUnderlineText(false);
        paint.setColor(mTextColor);
        canvas.drawText(text, start, end,
                x + (mWidth - mWidthActual) * 0.5f,
                y + (mHeight - textHeight) * 0.5f, paint);
    }
}
