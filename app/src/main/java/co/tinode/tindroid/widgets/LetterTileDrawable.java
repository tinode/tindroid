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
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import co.tinode.tindroid.R;

/**
 * A drawable that encapsulates all the functionality needed to display a letter tile to
 * represent a contact image. Slightly modified from
 * com/android/contacts/common/lettertiles/LetterTileDrawable.java
 */
public class LetterTileDrawable extends Drawable {
    private static final ContactType TYPE_DEFAULT = ContactType.PERSON;
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
    private static int sDefaultColorLight;
    private static int sDefaultColorDark;
    private static int sSelfBackgroundColor;
    private static int sTileFontColorLight;
    private static int sTileFontColorDark;
    private static float sLetterToTileRatio;
    private static Bitmap DEFAULT_PERSON_AVATAR;
    private static Bitmap DEFAULT_GROUP_AVATAR;
    private static Bitmap DEFAULT_SELF_AVATAR;
    private final Paint mPaint;
    private ContactType mContactType = TYPE_DEFAULT;
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
            sDefaultColorLight = res.getColor(R.color.letter_tile_bg_color_light, null);
            sDefaultColorDark = res.getColor(R.color.letter_tile_bg_color_dark, null);
            sSelfBackgroundColor = res.getColor(R.color.letter_tile_self_bg_color, null);
            sTileFontColorLight = res.getColor(R.color.letter_tile_text_color_light, null);
            sTileFontColorDark = res.getColor(R.color.letter_tile_text_color_dark, null);
            sLetterToTileRatio = 0.75f;
            DEFAULT_PERSON_AVATAR = getBitmapFromVectorDrawable(context, R.drawable.ic_person_white);
            DEFAULT_GROUP_AVATAR = getBitmapFromVectorDrawable(context, R.drawable.ic_group_white);
            DEFAULT_SELF_AVATAR = getBitmapFromVectorDrawable(context, R.drawable.ic_bookmark_ol);
            sPaint.setTextAlign(Align.CENTER);
            sPaint.setAntiAlias(true);
        }
        mPaint = new Paint();
        mPaint.setFilterBitmap(true);
        mPaint.setDither(true);
        mColor = res.getColor(R.color.grey, null);
    }

    /**
     * Load vector drawable from the given resource id, then convert drawable to bitmap.
     *
     * @param context    context
     * @param drawableId vector drawable resource id
     * @return bitmap extracted from the drawable.
     */
    private static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) {
            throw new IllegalStateException("getBitmapFromVectorDrawable failed: null drawable");
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private static Bitmap getBitmapForContactType(ContactType contactType) {
        return switch (contactType) {
            case SELF -> DEFAULT_SELF_AVATAR;
            case GROUP -> DEFAULT_GROUP_AVATAR;
            default -> DEFAULT_PERSON_AVATAR;
        };
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

    // Render LTD as a bitmap of the given size.
    public Bitmap getSquareBitmap(final int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        draw(canvas);
        return bmp;
    }

    public int getColor() {
        return mColor;
    }

    public LetterTileDrawable setColor(int color) {
        mColor = color;
        return this;
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

    public LetterTileDrawable setLetterAndColor(final String displayName, final String identifier,
                                                final boolean disabled) {
        if (displayName != null && !displayName.isEmpty()) {
            mLetter = Character.toUpperCase(displayName.charAt(0));
        } else {
            mLetter = null;
        }
        mHashCode = TextUtils.isEmpty(identifier) ? 0 : Math.abs(identifier.hashCode());

        mColor = pickColor(mContactType, disabled ? 0 : mHashCode);
        return this;
    }

    /**
     * Change type of the tile: person or group.
     *
     * @param ct type of icon to use when the tile has no letter.
     * @return this
     */
    public LetterTileDrawable setContactTypeAndColor(ContactType ct, boolean disabled) {
        mContactType = ct;
        mColor = pickColor(ct, disabled ? 0 : mHashCode);
        return this;
    }

    /**
     * Change shape of the tile: circular (default) or rectangular.
     *
     * @param isCircle true to make tile circular, false for rectangular.
     * @return this
     */
    public LetterTileDrawable setIsCircular(boolean isCircle) {
        mIsCircle = isCircle;
        return this;
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

    // Private methods.

    private void drawLetterTile(final Canvas canvas) {
        // Draw background color.
        sPaint.setColor(mColor);
        sPaint.setAlpha(mPaint.getAlpha());

        final Rect bounds = getBounds();
        final int minDimension = Math.min(bounds.width(), bounds.height());

        if (mIsCircle) {
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), minDimension / 2.0f, sPaint);
        } else {
            canvas.drawRect(bounds, sPaint);
        }

        // Draw the first letter/digit
        if (mLetter != null) {
            // Draw letter or digit.
            sFirstChar[0] = mLetter;
            // Scale text by canvas bounds and user selected scaling factor
            sPaint.setTextSize(mScale * sLetterToTileRatio * minDimension);
            //sPaint.setTextSize(sTileLetterFontSize);
            sPaint.getTextBounds(sFirstChar, 0, 1, sRect);
            sPaint.setColor(mContactType == ContactType.PERSON ? sTileFontColorDark : sTileFontColorLight);
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

    /**
     * Returns a deterministic color based on the provided contact identifier string.
     */
    private static int pickColor(ContactType ct, int hashCode) {
        if (ct == ContactType.SELF) {
            return sSelfBackgroundColor;
        }

        int color = ct == ContactType.PERSON ? sDefaultColorDark : sDefaultColorLight;
        if (hashCode == 0) {
            return color;
        }

        TypedArray colors = ct == ContactType.PERSON ? sColorsDark : sColorsLight;
        return colors.getColor(hashCode % colors.length(), color);
    }

    /**
     * Contact type constants
     */
    public enum ContactType {
        PERSON, GROUP, SELF
    }
}
