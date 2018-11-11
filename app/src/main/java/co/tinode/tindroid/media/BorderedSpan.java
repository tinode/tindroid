package co.tinode.tindroid.media;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.text.style.ReplacementSpan;


public class BorderedSpan extends ReplacementSpan {
    private final Paint mPaintBorder, mPaintBackground;
    private int mWidth;
    private int mTextColor;

    BorderedSpan(Context context) {
        mPaintBorder = new Paint();
        mPaintBorder.setStyle(Paint.Style.STROKE);
        mPaintBorder.setAntiAlias(true);
        mPaintBorder.setColor(Color.rgb(0xdd, 0xdd, 0xee));

        mPaintBackground = new Paint();
        mPaintBackground.setStyle(Paint.Style.FILL);
        mPaintBackground.setAntiAlias(true);
        mPaintBackground.setColor(Color.rgb(0xee, 0xee, 0xff));

        int[] attrs = {android.R.attr.textColorLink};
        TypedArray colors = context.obtainStyledAttributes(attrs);
        mTextColor = colors.getColor(0, 0x7bc9c2);
        colors.recycle();
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        //return text with relative to the Paint
        mWidth = (int) paint.measureText(text, start, end);
        return mWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        canvas.drawRoundRect(new RectF(x, top, x + mWidth, bottom), 4f, 4f, mPaintBackground);
        canvas.drawRoundRect(new RectF(x, top, x + mWidth, bottom), 4f, 4f, mPaintBorder);
        paint.setUnderlineText(true);
        paint.setColor(mTextColor);
        canvas.drawText(text, start, end, x, y, paint);
    }
}
