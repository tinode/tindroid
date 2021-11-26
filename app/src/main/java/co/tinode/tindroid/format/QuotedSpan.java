package co.tinode.tindroid.format;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.Spanned;
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

    public QuotedSpan(int backgroundColor, float cornerRadius, int stripeColor,
                      float stripeWidth, float gap) {
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
    public void drawBackground(@NonNull Canvas canvas, @NonNull Paint paint,
                               int left, int right, int top, int baseline, int bottom,
                               @NonNull CharSequence text, int start, int end, int lineNumber) {
        // Start and end of the current span within the text string.
        int myStart = -1, myEnd = -1;
        if (text instanceof Spanned) {
            myStart = ((Spanned) text).getSpanStart(this);
            myEnd = ((Spanned) text).getSpanEnd(this);
        }

        int originalColor = paint.getColor();
        paint.setColor(mBackgroundColor);
        if (start > myStart && end < myEnd) {
            // Lines in the middle.
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setColor(mStripeColor);
            canvas.drawRect(left, top, left + mStripeWidth, bottom, paint);
        } else {
            Path background = new Path();
            Path stripe = new Path();
            if (start == myStart) {
                // Fist line.
                background.addRoundRect(left, top, right, bottom,
                        new float[]{mCornerRadius, mCornerRadius, mCornerRadius, mCornerRadius, 0, 0, 0, 0},
                        Path.Direction.CW);
                stripe.addRoundRect(left, top, left + mStripeWidth, bottom,
                        new float[]{mCornerRadius, mCornerRadius, 0, 0, 0, 0, 0, 0},
                        Path.Direction.CW);
            } else {
                // Last line
                background.addRoundRect(left, top, right, bottom,
                        new float[]{0, 0, 0, 0, mCornerRadius, mCornerRadius, mCornerRadius, mCornerRadius},
                        Path.Direction.CW);
                stripe.addRoundRect(left, top, left + mStripeWidth, bottom,
                        new float[]{0, 0, 0, 0, 0, 0, mCornerRadius, mCornerRadius},
                        Path.Direction.CW);
            }
            canvas.drawPath(background, paint);
            paint.setColor(mStripeColor);
            canvas.drawPath(stripe, paint);
        }
        paint.setColor(originalColor);
    }
}
