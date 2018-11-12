package co.tinode.tindroid.media;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.text.style.ReplacementSpan;

public class BorderedSpan extends ReplacementSpan {
    private static final String TAG = "BorderedSpan";

    private static final float RADIUS_CORNER = 4f;
    private static final int MIN_BUTTON_WIDTH = 160;
    private final Paint mPaintBorder, mPaintBackground;
    private int mHeight;
    private int mWidth;
    private int mWidthActual;
    private int mTextColor;

    BorderedSpan(final Context context, final float lineHeight) {
        mPaintBorder = new Paint();
        mPaintBorder.setStyle(Paint.Style.STROKE);
        mPaintBorder.setAntiAlias(true);
        mPaintBorder.setColor(Color.rgb(0xcc, 0xcc, 0xdd));

        mPaintBackground = new Paint();
        mPaintBackground.setStyle(Paint.Style.FILL);
        mPaintBackground.setAntiAlias(true);
        mPaintBackground.setColor(Color.rgb(0xee, 0xee, 0xff));

        int[] attrs = {android.R.attr.textColorLink};
        TypedArray colors = context.obtainStyledAttributes(attrs);
        mTextColor = colors.getColor(0, 0x7bc9c2);
        colors.recycle();

        mHeight = (int) lineHeight;
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
        mWidth = mWidth < MIN_BUTTON_WIDTH ? MIN_BUTTON_WIDTH : mWidth;
        return mWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        int vert = (int) (((float)(bottom - top) - (paint.descent() - paint.ascent())) * 0.5f);
        Rect bounds = new Rect();
        paint.getTextBounds(text.toString(), start, end, bounds);
        canvas.drawRoundRect(new RectF(x, top, x + mWidth, top + mHeight), RADIUS_CORNER, RADIUS_CORNER, mPaintBackground);
        canvas.drawRoundRect(new RectF(x, top, x + mWidth, bottom), RADIUS_CORNER, RADIUS_CORNER, mPaintBorder);
        paint.setUnderlineText(true);
        paint.setColor(mTextColor);
        canvas.drawText(text, start, end,
                x + (mWidth - mWidthActual) * 0.5f,
                y + vert, paint);
    }
}
