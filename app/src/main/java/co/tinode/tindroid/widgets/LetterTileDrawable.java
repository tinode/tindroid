package co.tinode.tindroid.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.TextUtils;

import co.tinode.tindroid.R;

/**
 * A drawable that encapsulates all the functionality needed to display a letter tile to
 * represent a contact image. Slightly modified from
 * com/android/contacts/common/lettertiles/LetterTileDrawable.java
 */
public class LetterTileDrawable extends Drawable {
    private final String TAG = "LetterTileDrawable";

    /**
     * Contact type constants
     */
    public static final int TYPE_PERSON = 1;
    public static final int TYPE_GROUP = 2;
    public static final int TYPE_DEFAULT = TYPE_PERSON;

    /**
     * Reusable components to avoid new allocations
     */
    private static final Paint sPaint = new Paint();
    private static final Rect sRect = new Rect();
    private static final char[] sFirstChar = new char[1];

    private static final int INTRINSIC_SIZE = 128;
    /**
     * Letter tile
     */
    private static TypedArray sColorsLight;
    private static TypedArray sColorsDark;
    private static int sDefaultColor;
    private static int sTileFontColorLight;
    private static int sTileFontColorDark;
    private static float sLetterToTileRatio;
    private static Bitmap DEFAULT_PERSON_AVATAR;
    private static Bitmap DEFAULT_GROUP_AVATAR;

    private final Paint mPaint;
    private int mContactType = TYPE_DEFAULT;
    private float mScale = 0.7f;
    private float mOffset = 0.0f;
    private boolean mIsCircle = true;

    private int mColor;
    private Character mLetter = null;
    private int mHashCode = 0;

    public LetterTileDrawable(final Context context) {
        Resources res = context.getResources();
        if (sColorsLight == null) {
            sColorsLight = res.obtainTypedArray(R.array.letter_tile_colors_light);
            sColorsDark = res.obtainTypedArray(R.array.letter_tile_colors_dark);
            sDefaultColor = res.getColor(R.color.grey);
            sTileFontColorLight = res.getColor(R.color.letter_tile_text_color_light);
            sTileFontColorDark = res.getColor(R.color.letter_tile_text_color_dark);
            sLetterToTileRatio = 0.75f;
            DEFAULT_PERSON_AVATAR = getBitmapFromVectorDrawable(context, R.drawable.ic_person_white);
            DEFAULT_GROUP_AVATAR = getBitmapFromVectorDrawable(context, R.drawable.ic_group_white);
            // sPaint.setTypeface(Typeface.create(
            //         res.getString(R.string.letter_tile_letter_font_family), Typeface.NORMAL));
            sPaint.setTextAlign(Align.CENTER);
            sPaint.setAntiAlias(true);
        }
        mPaint = new Paint();
        mPaint.setFilterBitmap(true);
        mPaint.setDither(true);
        mColor = sDefaultColor;
    }

    /**
     * Attempt to create a drawable from the given vector drawable resource id, and the convert drawable
     * to bitmap
     * @param context
     * @param drawableId vector drawable resource id
     * @return
     */
    private static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = (DrawableCompat.wrap(drawable)).mutate();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private static Bitmap getBitmapForContactType(int contactType) {
        switch (contactType) {
            case TYPE_PERSON:
                return DEFAULT_PERSON_AVATAR;
            case TYPE_GROUP:
                return DEFAULT_GROUP_AVATAR;
            default:
                return DEFAULT_PERSON_AVATAR;
        }
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }
        // Draw letter tile.
        drawLetterTile(canvas);
    }

    /**
     * Draw the bitmap onto the canvas at the current bounds taking into account the current scale.
     */
    private void drawBitmap(final Bitmap bitmap, final int width, final int height,
                            final Canvas canvas) {
        // The bitmap should be drawn in the middle of the canvas without changing its width to
        // height ratio.
        final Rect destRect = copyBounds();
        // Crop the destination bounds into a square, scaled and offset as appropriate
        final int halfLength = (int) (mScale * Math.min(destRect.width(), destRect.height()) / 2);
        destRect.set(destRect.centerX() - halfLength,
                (int) (destRect.centerY() - halfLength + mOffset * destRect.height()),
                destRect.centerX() + halfLength,
                (int) (destRect.centerY() + halfLength + mOffset * destRect.height()));
        // Source rectangle remains the entire bounds of the source bitmap.

        sRect.set(0, 0, width, height);

        canvas.drawBitmap(bitmap, sRect, destRect, mPaint);
    }

    private void drawLetterTile(final Canvas canvas) {
        // Draw background color.
        sPaint.setColor(mColor);
        sPaint.setAlpha(mPaint.getAlpha());

        final Rect bounds = getBounds();
        final int minDimension = Math.min(bounds.width(), bounds.height());

        if (mIsCircle) {
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), minDimension / 2, sPaint);
        } else {
            canvas.drawRect(bounds, sPaint);
        }

        // Draw letter/digit only if the first character is an english letter or there's a override
        if (mLetter != null) {
            // Draw letter or digit.
            sFirstChar[0] = mLetter;
            // Scale text by canvas bounds and user selected scaling factor
            sPaint.setTextSize(mScale * sLetterToTileRatio * minDimension);
            //sPaint.setTextSize(sTileLetterFontSize);
            sPaint.getTextBounds(sFirstChar, 0, 1, sRect);
            sPaint.setColor(mContactType == TYPE_PERSON ? sTileFontColorDark : sTileFontColorLight);
            // Draw the letter in the canvas, vertically shifted up or down by the user-defined
            // offset
            canvas.drawText(sFirstChar, 0, 1, bounds.centerX(),
                    bounds.centerY() + mOffset * bounds.height() - sRect.exactCenterY(),
                    sPaint);
        } else {
            // Draw the default image if there is no letter/digit to be drawn
            final Bitmap bitmap = getBitmapForContactType(mContactType);

            drawBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(),
                    canvas);
        }
    }

    public int getColor() {
        return mColor;
    }

    public LetterTileDrawable setColor(int color) {
        mColor = color;
        return this;
    }

    /**
     * Returns a deterministic color based on the provided contact identifier string.
     */
    private int pickColor() {
        if (mHashCode == 0) {
            return sDefaultColor;
        }

        TypedArray colors = mContactType == TYPE_PERSON ? sColorsDark : sColorsLight;
        final int color = mHashCode % colors.length();
        return colors.getColor(color, sDefaultColor);
    }

    @Override
    public void setAlpha(final int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(final ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.OPAQUE;
    }

    @Override
    public int getIntrinsicWidth() {
        // This has to be set otherwise it does not show in toolbar
        return INTRINSIC_SIZE;
    }

    @Override
    public int getIntrinsicHeight() {
        return INTRINSIC_SIZE;
    }

    /**
     * Scale the drawn letter tile to a ratio of its default size
     *
     * @param scale The ratio the letter tile should be scaled to as a percentage of its default
     *              size, from a scale of 0 to 2.0f. The default is 1.0f.
     */
    public LetterTileDrawable setScale(float scale) {
        mScale = scale;
        return this;
    }

    /**
     * Assigns the vertical offset of the position of the letter tile to the ContactDrawable
     *
     * @param offset The provided offset must be within the range of -0.5f to 0.5f.
     *               If set to -0.5f, the letter will be shifted upwards by 0.5 times the height of the canvas
     *               it is being drawn on, which means it will be drawn with the center of the letter starting
     *               at the top edge of the canvas.
     *               If set to 0.5f, the letter will be shifted downwards by 0.5 times the height of the canvas
     *               it is being drawn on, which means it will be drawn with the center of the letter starting
     *               at the bottom edge of the canvas.
     *               The default is 0.0f.
     */
    public LetterTileDrawable setOffset(float offset) {
        mOffset = Math.min(Math.max(offset, -0.5f), 0.5f);
        return this;
    }

    public LetterTileDrawable setLetter(Character letter) {
        mLetter = letter;
        return this;
    }

    public LetterTileDrawable setLetterAndColor(final String displayName, final String identifier) {
        if (displayName != null && displayName.length() > 0) {
            mLetter = Character.toUpperCase(displayName.charAt(0));
        } else {
            mLetter = null;
        }
        mHashCode = TextUtils.isEmpty(identifier) ? 0 : Math.abs(identifier.hashCode());

        mColor = pickColor();
        return this;
    }

    public LetterTileDrawable setContactTypeAndColor(int contactType) {
        mContactType = contactType;
        mColor = pickColor();
        return this;
    }

    public LetterTileDrawable setIsCircular(boolean isCircle) {
        mIsCircle = isCircle;
        return this;
    }
}
