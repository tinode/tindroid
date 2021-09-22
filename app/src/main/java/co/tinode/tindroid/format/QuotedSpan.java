package co.tinode.tindroid.format;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;

import androidx.annotation.NonNull;

// Draws a colored rounded rectangle background with a vertical stripe on the start side.
public class QuotedSpan implements LeadingMarginSpan, LineBackgroundSpan {
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
        Paint.Style originalStyle = paint.getStyle();
        int originalColor = paint.getColor();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mStripeColor);
        Path stripe = new Path();
        stripe.addRoundRect(x, top, x + dir * mStripeWidth, bottom,
                dir < 0 ?
                        /* right */ new float[]{0, 0, mCornerRadius, mCornerRadius, mCornerRadius, mCornerRadius, 0, 0} :
                        /* left */ new float[]{mCornerRadius, mCornerRadius, 0, 0, 0, 0, mCornerRadius, mCornerRadius},
                Path.Direction.CW);
        canvas.drawPath(stripe, paint);
        paint.setStyle(originalStyle);
        paint.setColor(originalColor);
    }
    @Override
    public void drawBackground(Canvas canvas, Paint paint, int left, int right, int top, int baseline, int bottom,
                               @NonNull CharSequence text, int start, int end, int lineNumber) {
        int originalColor = paint.getColor();
        paint.setColor(mBackgroundColor);
        canvas.drawRoundRect(left, top, right, bottom, mCornerRadius, mCornerRadius, paint);
        paint.setColor(originalColor);
    }
}
