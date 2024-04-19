package co.tinode.tindroid.format;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import co.tinode.tindroid.R;

// Fills background and draws a rounded border around text.
public class LabelSpan extends ReplacementSpan {
    // All sizes are in DIPs.
    private static final float RADIUS_CORNER = 1.5f;
    private static final float PADDING_TOP = 2.0f;
    private final float mDipSize;
    private final float mCharWidth;
    private final Paint mPaintFrame;
    private final Paint mPaintBackground;
    // Width of the label with padding added, in DIPs.
    private int mWidth;
    // Actual width of the text in DIPs.
    private int mWidthActual;

    /**
     * Create formatter for text which appears as a label with background and a border.
     *
     * @param ctx      Context (activity) which uses this formatter.
     * @param fontSize font size in device (unscaled) pixels as returned by view.getTextSize().
     * @param dipSize  size of the DIP unit in unscaled pixels.
     */
    LabelSpan(final Context ctx, final float fontSize, float dipSize) {
        mDipSize = dipSize;

        // Approximate width of a '0' char.
        mCharWidth = 0.6f * fontSize / dipSize;

        mPaintFrame = new Paint();
        mPaintFrame.setStyle(Paint.Style.STROKE);
        mPaintFrame.setAntiAlias(true);
        mPaintFrame.setColor(ctx.getResources().getColor(R.color.colorChipBorder, null));

        mPaintBackground = new Paint();
        mPaintBackground.setStyle(Paint.Style.FILL);
        mPaintBackground.setAntiAlias(true);
        mPaintBackground.setColor(ctx.getResources().getColor(R.color.colorChipBackground, null));
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        // Actual text width in DIPs.
        mWidthActual = (int) (paint.measureText(text, start, end) / mDipSize);
        // Ensure minimum width of the button: actual width + 2 characters on each side.
        mWidth = (int) (mWidthActual + mCharWidth * 2);
        // The result must be in pixels.
        return (int) (mWidth * mDipSize);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        RectF outline = new RectF(x, top + PADDING_TOP * mDipSize, x + mWidth * mDipSize - 1, bottom - 1);
        // Draw background.
        canvas.drawRoundRect(outline, RADIUS_CORNER * mDipSize, RADIUS_CORNER * mDipSize, mPaintBackground);
        // Draw frame.
        canvas.drawRoundRect(outline, RADIUS_CORNER * mDipSize, RADIUS_CORNER * mDipSize, mPaintFrame);
        // Vertical padding between the button boundary and text.
        float padding = (outline.height() - paint.descent() + paint.ascent()) / 2f;
        canvas.drawText(text, start, end,
                x + (mWidth - mWidthActual - 1) * mDipSize * 0.5f,
                top + PADDING_TOP * mDipSize + padding - paint.ascent(),
                paint);
    }
}
