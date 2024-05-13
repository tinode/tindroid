package co.tinode.tindroid.format;

import android.graphics.RectF;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.widget.TextView;

import co.tinode.tindroid.R;

/**
 * Fixes two bugs in LinkMovementMethod:
 * <p>
 * Highlights clicked URLSpans only.
 * LinkMovementMethod tries to highlight any ClickableSpan resulting in
 * clickable images jumping left.
 * <p>
 * Correctly identifies URL bounds.
 * LinkMovementMethod registers a click made outside of the URL's bounds
 * if there is no more text in that direction.
 */
public class StableLinkMovementMethod extends LinkMovementMethod {

    private static StableLinkMovementMethod sSharedInstance;

    private final RectF mTouchedLineBounds = new RectF();
    private boolean mIsUrlHighlighted;
    private ClickableSpan mClickableSpanUnderTouchOnActionDown;
    private int mActiveTextViewHashcode;

    /**
     * Get a shared instance of StableLinkMovementMethod.
     */
    public static StableLinkMovementMethod getInstance() {
        if (sSharedInstance == null) {
            sSharedInstance = new StableLinkMovementMethod();
        }
        return sSharedInstance;
    }

    protected StableLinkMovementMethod() {
    }

    @Override
    public boolean onTouchEvent(final TextView textView, Spannable text, MotionEvent event) {
        if (mActiveTextViewHashcode != textView.hashCode()) {
            // Bug workaround: TextView stops calling onTouchEvent() once any URL is highlighted.
            // A hacky solution is to reset any "autoLink" property set in XML. But we also want
            // to do this once per TextView.
            mActiveTextViewHashcode = textView.hashCode();
            textView.setAutoLinkMask(0);
        }

        final ClickableSpan clickableSpanUnderTouch = findClickableSpanUnderTouch(textView, text, event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mClickableSpanUnderTouchOnActionDown = clickableSpanUnderTouch;
        }
        final boolean touchStartedOverAClickableSpan = mClickableSpanUnderTouchOnActionDown != null;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (clickableSpanUnderTouch instanceof URLSpan) {
                    highlightUrl(textView, clickableSpanUnderTouch, text);
                }

                return touchStartedOverAClickableSpan;

            case MotionEvent.ACTION_UP:
                // Register a click only if the touch started and ended on the same URL.
                if (touchStartedOverAClickableSpan &&
                        clickableSpanUnderTouch == mClickableSpanUnderTouchOnActionDown) {
                    clickableSpanUnderTouch.onClick(textView);
                }
                cleanupOnTouchUp(textView);

                // Consume this event even if we could not find any spans to avoid letting Android handle this event.
                // Android's TextView implementation has a bug where links get clicked even when there is no more text
                // next to the link and the touch lies outside its bounds in the same direction.
                return touchStartedOverAClickableSpan;

            case MotionEvent.ACTION_CANCEL:
                cleanupOnTouchUp(textView);
                return false;

            case MotionEvent.ACTION_MOVE:
                // Toggle highlight.
                if (clickableSpanUnderTouch != null) {
                    highlightUrl(textView, clickableSpanUnderTouch, text);
                } else {
                    removeUrlHighlightColor(textView);
                }

                return touchStartedOverAClickableSpan;

            default:
                return false;
        }
    }

    private void cleanupOnTouchUp(TextView textView) {
        mClickableSpanUnderTouchOnActionDown = null;
        removeUrlHighlightColor(textView);
    }

    /**
     * Determines the touched location inside the TextView's text and returns the ClickableSpan found under it (if any).
     *
     * @return The touched ClickableSpan or null.
     */
    protected ClickableSpan findClickableSpanUnderTouch(TextView textView, Spannable text, MotionEvent event) {
        // Find the location in text where touch was made, regardless of whether the TextView
        // has scrollable text. That is, not the entire text is currently visible.
        int touchX = (int) event.getX();
        int touchY = (int) event.getY();

        // Ignore padding.
        touchX -= textView.getTotalPaddingLeft();
        touchY -= textView.getTotalPaddingTop();

        // Account for scrollable text.
        touchX += textView.getScrollX();
        touchY += textView.getScrollY();

        final Layout layout = textView.getLayout();
        final int touchedLine = layout.getLineForVertical(touchY);
        final int touchOffset = layout.getOffsetForHorizontal(touchedLine, touchX);

        mTouchedLineBounds.left = layout.getLineLeft(touchedLine);
        mTouchedLineBounds.top = layout.getLineTop(touchedLine);
        mTouchedLineBounds.right = layout.getLineWidth(touchedLine) + mTouchedLineBounds.left;
        mTouchedLineBounds.bottom = layout.getLineBottom(touchedLine);

        if (mTouchedLineBounds.contains(touchX, touchY)) {
            // Find a ClickableSpan that lies under the touched area.
            final Object[] spans = text.getSpans(touchOffset, touchOffset, ClickableSpan.class);
            for (final Object span : spans) {
                if (span instanceof ClickableSpan) {
                    return (ClickableSpan) span;
                }
            }
        }

        // No ClickableSpan found under.
        return null;
    }

    /**
     * Adds a background color span at <var>clickableSpan</var>'s location.
     */
    protected void highlightUrl(TextView textView, ClickableSpan clickableSpan, Spannable text) {
        if (mIsUrlHighlighted) {
            return;
        }
        mIsUrlHighlighted = true;

        int spanStart = text.getSpanStart(clickableSpan);
        int spanEnd = text.getSpanEnd(clickableSpan);
        BackgroundColorSpan highlightSpan = new BackgroundColorSpan(textView.getHighlightColor());
        text.setSpan(highlightSpan, spanStart, spanEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

        textView.setTag(R.id.highlight_background_span, highlightSpan);

        Selection.setSelection(text, spanStart, spanEnd);
    }

    /**
     * Removes the highlight color under the Url.
     */
    protected void removeUrlHighlightColor(TextView textView) {
        if (!mIsUrlHighlighted) {
            return;
        }
        mIsUrlHighlighted = false;

        Spannable text = (Spannable) textView.getText();
        BackgroundColorSpan highlightSpan = (BackgroundColorSpan) textView.getTag(R.id.highlight_background_span);
        text.removeSpan(highlightSpan);

        Selection.removeSelection(text);
    }
}