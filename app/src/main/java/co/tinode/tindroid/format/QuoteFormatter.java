package co.tinode.tindroid.format;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;

import java.util.Map;

import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;

public class QuoteFormatter extends PreviewFormatter {
    private static final String TAG = "QuoteFormatter";

    private static final int IMAGE_THUMBNAIL_DIM = 32; // dip
    private static final int IMAGE_PADDING = 2; //dip
    private static final int MAX_FILE_NAME_LENGTH = 16;

    private static TypedArray sColorsDark;
    private static int sTextColor;


    public QuoteFormatter(final Context context, float fontSize, int maxLength ) {
        super(context, fontSize, maxLength);

        Resources res = context.getResources();
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
        StyledTreeNode node = SpanFormatter.handleMention_Impl(content, data);
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
        int width = (int) (IMAGE_THUMBNAIL_DIM * metrics.density);
        int height = (int) (IMAGE_THUMBNAIL_DIM * metrics.density);

        Object filename = data.get("name");
        if (filename instanceof String) {
            filename = shortenFileName((String) filename);
        } else {
            filename = res.getString(R.string.picture);
        }

        Drawable thumbnail = null;

        // Inline image only.
        Object val = data.get("val");
        if (val != null) {
            try {
                // If the message is not yet sent, the bits could be raw byte[] as opposed to
                // base64-encoded.
                byte[] bits = (val instanceof String) ?
                        Base64.decode((String) val, Base64.DEFAULT) : (byte[]) val;
                Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                if (bmp != null) {
                    thumbnail = new BitmapDrawable(res, bmp);
                    thumbnail.setBounds(0, 0, width, height);
                }
            } catch (Exception ex) {
                Log.w(TAG, "Broken image preview", ex);
            }
        }

        if (thumbnail == null) {
            // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
            Drawable broken = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
            broken.setBounds(0, 0, broken.getIntrinsicWidth(), broken.getIntrinsicHeight());
            thumbnail = UiUtils.getPlaceholder(ctx, broken, null, width, height);
        }

        MeasuredTreeNode node = new MeasuredTreeNode(mMaxLength);
        node.addNode(new MeasuredTreeNode(new StyledImageSpan(thumbnail,
                new RectF(IMAGE_PADDING * metrics.density,
                        IMAGE_PADDING * metrics.density,
                        IMAGE_PADDING * metrics.density,
                        IMAGE_PADDING * metrics.density)),
                " ", mMaxLength));
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
