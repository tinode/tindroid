package co.tinode.tindroid.format;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import java.net.URL;
import java.util.List;
import java.util.Map;

import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.Const;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;

// Display quoted content.
public class QuoteFormatter extends PreviewFormatter {
    private static final String TAG = "QuoteFormatter";

    private static final int IMAGE_PADDING = 2; //dip
    private static final int MAX_FILE_NAME_LENGTH = 16;

    private static TypedArray sColorsDark;
    private static int sTextColor;

    private final View mParent;

    public QuoteFormatter(final View parent, float fontSize) {
        super(parent.getContext(), fontSize);

        mParent = parent;
        Resources res = parent.getResources();
        if (sColorsDark == null) {
            sColorsDark = res.obtainTypedArray(R.array.letter_tile_colors_dark);
            sTextColor = res.getColor(R.color.colorReplyText, null);
        }
    }

    @Override
    protected SpannableStringBuilder handleLineBreak() {
        return new SpannableStringBuilder("\n");
    }

    @Override
    protected SpannableStringBuilder handleMention(Context ctx, List<SpannableStringBuilder> content,
                                                   Map<String, Object> data) {
        return FullFormatter.handleMention_Impl(content, data);
    }

    private static class ImageDim {
        boolean square;
        int squareSize;
        int width;
        int height;
    }

    private CharacterStyle createImageSpan(Context ctx, Object val, String ref,
                                           ImageDim dim, float density,
                                           @DrawableRes int id_placeholder, @DrawableRes int id_error) {

        Resources res = ctx.getResources();

        // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
        Drawable broken = AppCompatResources.getDrawable(ctx, id_error);
        //noinspection ConstantConditions
        broken.setBounds(0, 0, broken.getIntrinsicWidth(), broken.getIntrinsicHeight());
        broken = UiUtils.getPlaceholder(ctx, broken, null,
                (int) (dim.squareSize * density),
                (int) (dim.squareSize * density));

        CharacterStyle span = null;

        // Trying to use in-band image first: we don't need the full image at "ref" to generate a tiny preview.
        if (val != null) {
            // Inline image.
            BitmapDrawable thumbnail;
            try {
                // If the message is not yet sent, the bits could be raw byte[] as opposed to
                // base64-encoded.
                byte[] bits = UiUtils.decodeByteArray(val);
                Bitmap bmp = null;
                if (bits != null) {
                    bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                }
                if (bmp != null) {
                    if (dim.square) {
                        thumbnail = new BitmapDrawable(res,
                                UiUtils.scaleSquareBitmap(bmp, (int) (dim.squareSize * density)));
                        thumbnail.setBounds(0, 0,
                                (int) (dim.squareSize * density), (int) (dim.squareSize * density));
                    } else {
                        thumbnail = new BitmapDrawable(res, UiUtils.scaleBitmap(bmp,
                                        (int) (dim.width * density), (int) (dim.height * density), true));
                        thumbnail.setBounds(0, 0, thumbnail.getBitmap().getWidth(),
                                thumbnail.getBitmap().getHeight());
                    }
                    span = new StyledImageSpan(thumbnail,
                            new RectF(IMAGE_PADDING * density,
                                    IMAGE_PADDING * density,
                                    IMAGE_PADDING * density,
                                    IMAGE_PADDING * density));
                }
            } catch (Exception ex) {
                Log.w(TAG, "Broken image preview", ex);
            }
        } else if (ref != null) {
            int width = dim.width;
            int height  = dim.height;
            if (dim.square) {
                width = dim.squareSize;
                height  = dim.squareSize;
            }
            URL url = Cache.getTinode().toAbsoluteURL(ref);
            // If small in-band image is not available, get the large one and shrink.
            span = new RemoteImageSpan(mParent, (int) (width * density), (int) (height * density), true,
                    AppCompatResources.getDrawable(ctx, id_placeholder), broken);
            if (url != null) {
                ((RemoteImageSpan) span).load(url);
            }
        }

        if (span == null) {
            span = new StyledImageSpan(broken,
                    new RectF(IMAGE_PADDING * density,
                            IMAGE_PADDING * density,
                            IMAGE_PADDING * density,
                            IMAGE_PADDING * density));
        }

        return span;
    }

    @Override
    protected SpannableStringBuilder handleImage(Context ctx, List<SpannableStringBuilder> content,
                                                 Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Resources res = ctx.getResources();
        DisplayMetrics metrics = res.getDisplayMetrics();

        ImageDim dim = new ImageDim();
        dim.square = true;
        dim.squareSize = Const.REPLY_THUMBNAIL_DIM;

        CharacterStyle span = createImageSpan(ctx, data.get("val"), getStringVal("ref", data, null),
                dim, metrics.density, R.drawable.ic_image, R.drawable.ic_broken_image);

        SpannableStringBuilder node = new SpannableStringBuilder();
        node.append(" ", span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String filename = getStringVal("name", data, res.getString(R.string.picture));
        node.append(" ").append(shortenFileName(filename));

        return node;
    }

    @Override
    protected SpannableStringBuilder handleVideo(Context ctx, List<SpannableStringBuilder> content,
                                                 Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Resources res = ctx.getResources();
        DisplayMetrics metrics = res.getDisplayMetrics();

        ImageDim dim = new ImageDim();
        dim.square = false;
        dim.squareSize = Const.REPLY_THUMBNAIL_DIM;
        dim.width = Const.REPLY_VIDEO_WIDTH;
        dim.height = Const.REPLY_THUMBNAIL_DIM;

        CharacterStyle span = createImageSpan(ctx, data.get("preview"), getStringVal("preref", data, null),
                dim, metrics.density, R.drawable.ic_video, R.drawable.ic_video);

        SpannableStringBuilder node = new SpannableStringBuilder();
        node.append(" ", span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String filename = getStringVal("name", data, res.getString(R.string.video));
        node.append(" ").append(shortenFileName(filename));

        return node;
    }

    @Override
    protected SpannableStringBuilder handleQuote(Context ctx, List<SpannableStringBuilder> content,
                                                 Map<String, Object> data) {
        // Quote within quote is not displayed;
        return null;
    }

    @Override
    protected SpannableStringBuilder handlePlain(List<SpannableStringBuilder> content) {
        SpannableStringBuilder node = join(content);
        if (node != null && node.getSpans(0, node.length(), Object.class).length == 0) {
            // Use default text color for plain text strings.
            node.setSpan(new ForegroundColorSpan(sTextColor), 0, node.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return node;
    }

    private static String shortenFileName(String filename) {
        int len = filename.length();
        return len > MAX_FILE_NAME_LENGTH ?
                filename.substring(0, MAX_FILE_NAME_LENGTH/2 - 1) + '…'
                        + filename.substring(len - MAX_FILE_NAME_LENGTH/2 + 1) : filename;
    }
}
