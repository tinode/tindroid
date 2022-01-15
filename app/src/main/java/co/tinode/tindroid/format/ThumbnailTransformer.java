package co.tinode.tindroid.format;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.util.Base64;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.Drafty;

// Convert images to thumbnails.
public class ThumbnailTransformer implements Drafty.Transformer {
    public PromisedReply<Drafty> completion = null;

    @Override
    public Drafty.Node transform(Drafty.Node node) {
        if (!node.isStyle("IM")) {
            return node;
        }

        Object val;

        // Trying to use in-band image first: we don't need the full image at "ref" to generate a tiny thumbnail.
        if ((val = node.getData("val")) != null) {
            // Inline image.
            try {
                byte[] bits = Base64.decode((String) val, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                bmp = UiUtils.scaleSquareBitmap(bmp, UiUtils.IMAGE_THUMBNAIL_DIM);
                bits = UiUtils.bitmapToBytes(bmp, "image/jpeg");
                node.putData("val", Base64.encode(bits, Base64.DEFAULT));
                node.putData("size", bits.length);
                node.putData("mime", "image/jpeg");
            } catch (Exception ex) {
                node.clearData("val");
                node.clearData("size");
            }
            node.putData("width", UiUtils.IMAGE_THUMBNAIL_DIM);
            node.putData("height", UiUtils.IMAGE_THUMBNAIL_DIM);
        } else if ((val = node.getData("ref")) instanceof String) {
            node.clearData("ref");
            node.putData("width", UiUtils.IMAGE_THUMBNAIL_DIM);
            node.putData("height", UiUtils.IMAGE_THUMBNAIL_DIM);
            Picasso.get().load(Cache.getTinode().toAbsoluteURL((String) val).toString()).into(new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bmp, Picasso.LoadedFrom from) {
                    bmp = UiUtils.scaleSquareBitmap(bmp, UiUtils.IMAGE_THUMBNAIL_DIM);
                    byte[] bits = UiUtils.bitmapToBytes(bmp, "image/jpeg");
                    node.putData("val", Base64.encode(bits, Base64.DEFAULT));
                    node.putData("size", bits.length);
                    node.putData("mime", "image/jpeg");
                }

                @Override
                public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                    node.clearData("size");
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) { /* do nothing */ }
            });
        }

        return node;
    }
}
