package co.tinode.tindroid.format;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.LineHeightSpan;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.StyleableRes;

// Span used to represent clickable buttons in Drafty forms.
public class ButtonSpan extends ReplacementSpan implements LineHeightSpan {
    // Size in DIPs.
    private static final float RADIUS_CORNER = 2.5f;
    private static final float SHADOW_SIZE = 2.5f;

    // Minimum button width in '0' characters.
    private static final int MIN_BUTTON_WIDTH = 8;
    // Scale of the button compare to font size.
    private static final float BUTTON_HEIGHT_SCALE = 2.0f;

    // Horizontal button padding in '0' characters.
    private static final int H_PADDING = 2;
    private final Paint mPaintBackground;
    private final int mTextColor;
    // Minimum button width in DIPs.
    private final int mMinButtonWidth;
    // Button height in DIP
    private final int mButtonHeight;
    // Size of DIP in pixels.
    private final float mDipSize;
    // Width of the button with padding added and minimum applied in DIPs.
    private int mWidth;
    // Actual width of the text in DIPs.
    private int mWidthActual;

    /**
     * Create formatter for text which appears as clickable buttons.
     *
     * @param context  Context (activity) which uses this formatter.
     * @param fontSize font size in device (unscaled) pixels as returned by view.getTextSize().
     * @param dipSize  size of the DIP unit in unscaled pixels.
     */
    ButtonSpan(final Context context, final float fontSize, float dipSize) {
        mDipSize = dipSize;

        @SuppressLint("ResourceType") @StyleableRes int[] attrs =
                {android.R.attr.textColorPrimary, android.R.attr.colorButtonNormal};
        TypedArray colors = context.obtainStyledAttributes(attrs);
        mTextColor = colors.getColor(0, 0x7bc9c2);
        @SuppressLint("ResourceType")
        int background = colors.getColor(1, 0xeeeeff);
        colors.recycle();

        mPaintBackground = new Paint();
        mPaintBackground.setStyle(Paint.Style.FILL);
        mPaintBackground.setAntiAlias(true);
        mPaintBackground.setColor(background);
        mPaintBackground.setShadowLayer(SHADOW_SIZE * dipSize,
                SHADOW_SIZE * 0.5f * dipSize,
                SHADOW_SIZE * 0.5f * dipSize,
                Color.argb(0x80, 0, 0, 0));

        // Char width is ~60% of the height. In DIPs.
        mMinButtonWidth = (int) (MIN_BUTTON_WIDTH * 0.6f * fontSize / dipSize);
        // Button height in DIPs.
        mButtonHeight = (int) (BUTTON_HEIGHT_SCALE * fontSize / dipSize);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        // Actual text width in DIPs.
        mWidthActual = (int) (paint.measureText(text, start, end) / mDipSize);
        // Ensure minimum width of the button: actual width + 2 characters on each side.
        mWidth = Math.max(mWidthActual + (mMinButtonWidth / MIN_BUTTON_WIDTH) * H_PADDING * 2, mMinButtonWidth);
        // The result must be in pixels.
        return (int) (mWidth * mDipSize);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                             Paint.FontMetricsInt fm) {
        float diff = mButtonHeight * mDipSize - (fm.bottom - fm.top);

        // Adjust height.
        fm.descent += (int) diff;
        fm.bottom += (int) diff;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        RectF outline = new RectF(x, top, x + mWidth * mDipSize, top + mButtonHeight * mDipSize);
        outline.inset(SHADOW_SIZE * mDipSize, SHADOW_SIZE * mDipSize);
        // Draw colored background
        canvas.drawRoundRect(outline, RADIUS_CORNER * mDipSize, RADIUS_CORNER * mDipSize, mPaintBackground);

        // Don't underline the text.
        paint.setUnderlineText(false);
        paint.setColor(mTextColor);
        canvas.drawText(text, start, end,
                x + (mWidth - mWidthActual) * mDipSize * 0.5f,
                top + (mButtonHeight * mDipSize - paint.ascent() - paint.descent()) * 0.5f,
                paint);
    }
}
