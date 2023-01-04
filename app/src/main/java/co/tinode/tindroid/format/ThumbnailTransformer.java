package co.tinode.tindroid.format;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.LinkedList;
import java.util.List;

import co.tinode.tindroid.Const;
import co.tinode.tindroid.UiUtils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.Drafty;

// Convert images to thumbnails.
public class ThumbnailTransformer implements Drafty.Transformer {
    protected List<PromisedReply<Void>> components = null;

    public PromisedReply<Void[]> completionPromise() {
        if (components == null) {
            return new PromisedReply<>((Void[]) null);
        }

        // noinspection unchecked
        return PromisedReply.allOf(components.toArray(new PromisedReply[]{}));
    }

    @Override
    public Drafty.Node transform(Drafty.Node node) {
        if (!node.isStyle("IM")) {
            return node;
        }

        Object val;

        node.putData("width", Const.REPLY_THUMBNAIL_DIM);
        node.putData("height", Const.REPLY_THUMBNAIL_DIM);

        // Trying to use in-band image first: we don't need the full image at "ref" to generate a tiny thumbnail.
        if ((val = node.getData("val")) != null) {
            // Inline image.
            try {
                byte[] bits = Base64.decode((String) val, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                bmp = UiUtils.scaleSquareBitmap(bmp, Const.REPLY_THUMBNAIL_DIM);
                bits = UiUtils.bitmapToBytes(bmp, "image/jpeg");
                node.putData("val", Base64.encodeToString(bits, Base64.NO_WRAP));
                node.putData("size", bits.length);
                node.putData("mime", "image/jpeg");
            } catch (Exception ex) {
                node.clearData("val");
                node.clearData("size");
            }
        } else if ((val = node.getData("ref")) instanceof String) {
            node.clearData("ref");
            final PromisedReply<Void> done = new PromisedReply<>();
            if (components == null) {
                components = new LinkedList<>();
            }
            components.add(done);
            Picasso.get().load((String) val).into(new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bmp, Picasso.LoadedFrom from) {
                    bmp = UiUtils.scaleSquareBitmap(bmp, Const.REPLY_THUMBNAIL_DIM);
                    byte[] bits = UiUtils.bitmapToBytes(bmp, "image/jpeg");
                    node.putData("val", Base64.encodeToString(bits, Base64.NO_WRAP));
                    node.putData("size", bits.length);
                    node.putData("mime", "image/jpeg");
                    try {
                        done.resolve(null);
                    } catch (Exception ignored) {}
                }

                @Override
                public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                    node.clearData("size");
                    node.clearData("mime");
                    try {
                        done.resolve(null);
                    } catch (Exception ignored) {}
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) { /* do nothing */ }
            });
        }

        return node;
    }
}
