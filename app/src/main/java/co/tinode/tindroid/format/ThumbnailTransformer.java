package co.tinode.tindroid.format;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import co.tinode.tindroid.Const;
import co.tinode.tindroid.TindroidApp;
import co.tinode.tindroid.UiUtils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.Drafty;

import coil.Coil;
import coil.request.ImageRequest;
import coil.target.Target;

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
            @SuppressLint("UnsafeOptInUsageError")
            Context context = TindroidApp.getAppContext();
            Coil.imageLoader(context).enqueue(
                    new ImageRequest.Builder(context)
                            .data(val)
                            .target(new Target() {
                                @Override
                                public void onSuccess(@NonNull Drawable drawable) {
                                    Bitmap bmp = UiUtils.bitmapFromDrawable(drawable);
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
                                public void onError(Drawable errorDrawable) {
                                    node.clearData("size");
                                    node.clearData("mime");
                                    try {
                                        done.resolve(null);
                                    } catch (Exception ignored) {}
                                }
                            }).build());
        }

        return node;
    }
}
