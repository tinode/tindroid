package co.tinode.tinui.format;

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
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import androidx.appcompat.content.res.AppCompatResources;

import java.util.List;
import java.util.Map;

import co.tinode.tinui.ImageUtils;
import co.tinode.tinui.R;
import co.tinode.tinui.TinodeClient;

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
            sTextColor = res.getColor(R.color.colorReplyText);
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

    @Override
    protected SpannableStringBuilder handleImage(Context ctx, List<SpannableStringBuilder> content,
                                                 Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Resources res = ctx.getResources();

        // Using fixed dimensions for the image.
        DisplayMetrics metrics = res.getDisplayMetrics();
        int size = (int) (ImageUtils.REPLY_THUMBNAIL_DIM * metrics.density);

        Object filename = data.get("name");
        if (filename instanceof String) {
            filename = shortenFileName((String) filename);
        } else {
            filename = res.getString(R.string.picture);
        }

        // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
        Drawable broken = AppCompatResources.getDrawable(ctx, R.drawable.tinui_ic_broken_image);
        //noinspection ConstantConditions
        broken.setBounds(0, 0, broken.getIntrinsicWidth(), broken.getIntrinsicHeight());
        broken = ImageUtils.getPlaceholder(ctx, broken, null, size, size);

        SpannableStringBuilder node = new SpannableStringBuilder();
        CharacterStyle span = null;

        Object val;

        // Trying to use in-band image first: we don't need the full image at "ref" to generate a tiny preview.
        if ((val = data.get("val")) != null) {
            // Inline image.
            Drawable thumbnail = null;
            try {
                // If the message is not yet sent, the bits could be raw byte[] as opposed to
                // base64-encoded.
                byte[] bits = (val instanceof String) ?
                        Base64.decode((String) val, Base64.DEFAULT) : (byte[]) val;
                Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                if (bmp != null) {
                    thumbnail = new BitmapDrawable(res, ImageUtils.scaleSquareBitmap(bmp, size));
                    thumbnail.setBounds(0, 0, size, size);
                    span = new StyledImageSpan(thumbnail,
                            new RectF(IMAGE_PADDING * metrics.density,
                                    IMAGE_PADDING * metrics.density,
                                    IMAGE_PADDING * metrics.density,
                                    IMAGE_PADDING * metrics.density));
                }
            } catch (Exception ex) {
                Log.w(TAG, "Broken image preview", ex);
            }
        } else if ((val = data.get("ref")) instanceof String) {
            // If small in-band image is not available, get the large one and shrink.
            Log.i(TAG, "Out-of-band " + val);

            span = new RemoteImageSpan(mParent, size, size, true,
                    AppCompatResources.getDrawable(ctx, R.drawable.tinui_ic_image), broken);
            ((RemoteImageSpan) span).load(TinodeClient.getInstance().toAbsoluteURL((String) val));
        }

        if (span == null) {
            span = new StyledImageSpan(broken,
                    new RectF(IMAGE_PADDING * metrics.density,
                            IMAGE_PADDING * metrics.density,
                            IMAGE_PADDING * metrics.density,
                            IMAGE_PADDING * metrics.density));
        }

        node.append(" ", span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        node.append(" ").append((String) filename);

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
