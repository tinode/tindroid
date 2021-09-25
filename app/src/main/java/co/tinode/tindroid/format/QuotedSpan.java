package co.tinode.tindroid.format;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.LineHeightSpan;
import android.util.Log;

import androidx.annotation.NonNull;

// Draws a colored rounded rectangle background with a vertical stripe on the start side.
public class QuotedSpan implements LeadingMarginSpan, LineBackgroundSpan, LineHeightSpan.WithDensity {
    private final static String TAG = "QuotedSpan";

    private final static int LINE_HEIGHT = 18;

    private final int mBackgroundColor;
    private final float mCornerRadius;
    private final int mStripeColor;
    private final float mStripeWidth;
    private final float mGapWidth;

    public QuotedSpan(int backgroundColor, float cornerRadius, int stripeColor, float stripeWidth, float gap) {
        mBackgroundColor = backgroundColor;
        mCornerRadius = cornerRadius;
        mStripeColor = stripeColor;
        mStripeWidth = stripeWidth;
        mGapWidth = gap;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return (int) (mStripeWidth + mGapWidth);
    }

    @Override
    public void drawLeadingMargin(Canvas canvas, Paint paint, int x, int dir, int top, int baseline, int bottom,
                                  CharSequence text, int start, int end, boolean first, Layout layout) {
        /* do nothing here */
    }

    @Override
    public void drawBackground(@NonNull Canvas canvas, Paint paint,
                               int left, int right, int top, int baseline, int bottom,
                               @NonNull CharSequence text, int start, int end, int lineNumber) {
        int originalColor = paint.getColor();
        paint.setColor(mBackgroundColor);
        if (lineNumber == 0) {
            Path path = new Path();
            path.addRoundRect(left, top, right, bottom,
                    new float[]{mCornerRadius, mCornerRadius, mCornerRadius, mCornerRadius, 0, 0, 0, 0},
                    Path.Direction.CW);
            canvas.drawPath(path, paint);
            path = new Path();
            paint.setColor(mStripeColor);
            path.addRoundRect(left, top, left + mStripeWidth, bottom,
                    new float[]{mCornerRadius, mCornerRadius, 0, 0, 0, 0, 0, 0},
                    Path.Direction.CW);
            canvas.drawPath(path, paint);
        } else {
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setColor(mStripeColor);
            canvas.drawRect(left, top, left + mStripeWidth, bottom, paint);
        }
        paint.setColor(originalColor);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv,
                             int lineHeight, Paint.FontMetricsInt fm) {
        chooseHeight(text, start, end, spanstartv, lineHeight, fm, null);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv,
                             int lineHeight, Paint.FontMetricsInt fm, TextPaint paint) {
        final int originHeight = fm.descent - fm.ascent;
        if (originHeight <= 0) {
            return;
        }

        int minHeight = LINE_HEIGHT;
        if (paint != null) {
            minHeight *= paint.density;
        }

        if (fm.bottom - fm.top < minHeight) {
            fm.top = fm.bottom - minHeight;
            fm.ascent = fm.ascent - minHeight;
        }

        Log.i(TAG, text + "; origin height=" + originHeight +
                "; lineHeight=" + lineHeight + "; fm.height=" + (fm.bottom - fm.top) +
                "; minHeight=" + minHeight);
    }
}
