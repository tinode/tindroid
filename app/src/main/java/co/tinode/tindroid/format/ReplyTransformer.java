package co.tinode.tindroid.format;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.squareup.picasso.Picasso;

import co.tinode.tindroid.Cache;
import co.tinode.tindroid.UiUtils;
import co.tinode.tinodesdk.model.Drafty;

// Transformer which creates a Drafty reply.
// Don't use this transformer on the main thread.
public class ReplyTransformer implements Drafty.Transformer {
    private static final String TAG = "ReplyTransformer";

    // Inline image.
    private Bitmap getImageFromBits(String val) {
        try {
            byte[] bits = Base64.decode((String) val, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bits, 0, bits.length);
        } catch (Exception ex) {
            Log.w(TAG, "Invalid image bits", ex);
        }

        return null;
    }

    // Out of band image: fetch and shrink.
    private Bitmap getImageFromUrl(String url) {
        try {
            return Picasso.get().load(Uri.parse(Cache.getTinode().toAbsoluteURL(url).toString())).get();
        }  catch (Exception ex) {
            Log.w(TAG, "Failed to load image from URL", ex);
        }

        return null;
    }


    @Override
    public <T extends Drafty.Node> Drafty.Node transform(T node) {
        if (node.isStyle("IM")) {
            // Shrink image.
            Object val;
            byte[] bits = null;
            Bitmap bmp = null;
            if ((val = node.getData("val")) != null && val instanceof String) {
                bmp = getImageFromBits((String) val);
            } else if ((val = node.getData("ref")) != null && val instanceof String) {
                bmp = getImageFromUrl((String) val);
            }

            if (bmp != null) {
                bmp = UiUtils.scaleSquareBitmap(bmp, UiUtils.IMAGE_PREVIEW_DIM);
                bits = UiUtils.bitmapToBytes(bmp, "image/jpeg");
            }

            node.clearData("ref");
            node.clearData("val");
            node.putData("val", bits);
            node.putData("size", bits == null ? 0 : bits.length);
            node.putData("mime", "image/jpeg");

        } else if (node.isStyle("MN")) {
            // Shorten mention to one symbol.
            CharSequence text = node.getText();
            if (text != null && text.length() > 1 && text.charAt(0) == '➦') {
                node.setText("➦");
            }
        }
        return node;
    }
}

