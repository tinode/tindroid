package co.tinode.tindroid.format;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;

import androidx.annotation.NonNull;

/**
 * A span that places an icon on the left margin and provides consistent
 * indentation for multiple lines of text.
 */
public class IconMarginSpan implements LeadingMarginSpan.LeadingMarginSpan2 {
    private final Drawable mIcon;
    private final int mPadding;
    private final int mLines;

    /**
     * @param icon    The drawable to place in the margin.
     * @param padding Extra padding between the icon and text.
     * @param lines   Number of lines the icon should span.
     */
    public IconMarginSpan(@NonNull Drawable icon, int padding, int lines) {
        mIcon = icon;
        mPadding = padding;
        mLines = lines;
    }

    @Override
    public int getLeadingMarginLineCount() {
        return mLines;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return mIcon.getIntrinsicWidth() + mPadding;
    }

    @Override
    public void drawLeadingMargin(@NonNull Canvas canvas, @NonNull Paint paint, int x, int dir,
                                  int top, int baseline, int bottom, @NonNull CharSequence text,
                                  int start, int end, boolean first, Layout layout) {
        // Only draw on the very first line of this span
        // Check if start matches the span start position
        if (text instanceof Spanned spanned) {
            int spanStart = spanned.getSpanStart(this);
            if (start == spanStart) {
                int iconWidth = mIcon.getIntrinsicWidth();
                int iconHeight = mIcon.getIntrinsicHeight();

                // Position icon at top-left, aligned with text top
                int left = x + (dir > 0 ? 0 : -iconWidth);
                mIcon.setBounds(left, top, left + iconWidth, top + iconHeight);
                mIcon.draw(canvas);
            }
        }
    }
}

