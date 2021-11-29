package co.tinode.tindroid.format;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import java.net.URL;
import java.util.Map;

import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.AttachmentHandler;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;

public class QuoteFormatter extends PreviewFormatter {
    private static final String TAG = "QuoteFormatter";

    private static final int IMAGE_THUMBNAIL_DIM = 32; // dip
    private static final int IMAGE_PADDING = 2; //dip
    private static final int MAX_FILE_NAME_LENGTH = 16;

    private static TypedArray sColorsDark;
    private static int sTextColor;

    private final View mParent;

    public QuoteFormatter(final View parent, float fontSize, int maxLength) {
        super(parent.getContext(), fontSize, maxLength);

        mParent = parent;
        Resources res = parent.getResources();
        if (sColorsDark == null) {
            sColorsDark = res.obtainTypedArray(R.array.letter_tile_colors_dark);
            sTextColor = res.getColor(R.color.colorReplyText);
        }
    }

    @Override
    protected MeasuredTreeNode handleLineBreak() {
        return new MeasuredTreeNode("\n", mMaxLength);
    }

    @Override
    protected MeasuredTreeNode handleMention(Context ctx, Object content, Map<String, Object> data) {
        StyledTreeNode node = SpanFormatter.handleMention_Impl(shortenForwardedMention(content), data);
        return new MeasuredTreeNode(node, mMaxLength);
    }

    @Override
    protected MeasuredTreeNode handleImage(Context ctx, Object content, Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Resources res = ctx.getResources();

        // Using fixed dimensions for the image.
        DisplayMetrics metrics = res.getDisplayMetrics();
        int size = (int) (IMAGE_THUMBNAIL_DIM * metrics.density);

        Object filename = data.get("name");
        if (filename instanceof String) {
            filename = shortenFileName((String) filename);
        } else {
            filename = res.getString(R.string.picture);
        }

        // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
        Drawable broken = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
        //noinspection ConstantConditions
        broken.setBounds(0, 0, broken.getIntrinsicWidth(), broken.getIntrinsicHeight());
        broken = UiUtils.getPlaceholder(ctx, broken, null, size, size);

        MeasuredTreeNode node = new MeasuredTreeNode(mMaxLength);

        Object val;

        // Trying to use in-band image first: we don't need the full image at "ref" to generate a tiny preview.
        if ((val = data.get("val")) != null) {
            // Inline image.
            Drawable thumbnail = broken;
            try {
                // If the message is not yet sent, the bits could be raw byte[] as opposed to
                // base64-encoded.
                byte[] bits = (val instanceof String) ?
                        Base64.decode((String) val, Base64.DEFAULT) : (byte[]) val;
                Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                if (bmp != null) {
                    thumbnail = new BitmapDrawable(res, UiUtils.scaleSquareBitmap(bmp, size));
                    thumbnail.setBounds(0, 0, size, size);
                }
            } catch (Exception ex) {
                Log.w(TAG, "Broken image preview", ex);
            }

            node.addNode(new MeasuredTreeNode(new StyledImageSpan(thumbnail,
                    new RectF(IMAGE_PADDING * metrics.density,
                            IMAGE_PADDING * metrics.density,
                            IMAGE_PADDING * metrics.density,
                            IMAGE_PADDING * metrics.density)),
                    " ", mMaxLength));

        } else if ((val = data.get("ref")) instanceof String) {
            // If small in-band image is not available, get the large one and shrink.
            Log.i(TAG, "Out-of-band " + val);

            UrlImageSpan span = new UrlImageSpan(mParent, size, size, true,
                    AppCompatResources.getDrawable(ctx, R.drawable.ic_image), broken);
            span.load(Cache.getTinode().toAbsoluteURL((String) val));
            node.addNode(new MeasuredTreeNode(span, " ", mMaxLength));
        }

        node.addNode(new MeasuredTreeNode(" " + filename, mMaxLength));

        return node;
    }

    @Override
    protected MeasuredTreeNode handleQuote(Context ctx, Map<String, Object> data, Object content) {
        // Quote within quote is not supported;
        return null;
    }

    @Override
    protected MeasuredTreeNode handlePlain(Object content) {
        if (content instanceof CharSequence) {
            return new MeasuredTreeNode(new ForegroundColorSpan(sTextColor), content, mMaxLength);
        }
        return new MeasuredTreeNode(content, mMaxLength);
    }

    private static String shortenFileName(String filename) {
        int len = filename.length();
        return len > MAX_FILE_NAME_LENGTH ?
                filename.substring(0, MAX_FILE_NAME_LENGTH/2 - 1) + '…'
                        + filename.substring(len - MAX_FILE_NAME_LENGTH/2 + 1) : filename;
    }
}
