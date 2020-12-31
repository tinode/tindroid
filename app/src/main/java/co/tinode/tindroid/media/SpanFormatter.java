package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.net.URL;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tinodesdk.model.Drafty;

/**
 * Convert Drafty object into a Spanned object with full support for all features.
 */
public class SpanFormatter extends AbstractDraftyFormatter<StyledTreeNode> {
    private static final String TAG = "SpanFormatter";

    private static final float FORM_LINE_SPACING = 1.2f;
    // Additional horizontal padding otherwise images sometimes fail to render.
    private static final int IMAGE_H_PADDING = 8;
    // Size of Download and Error icons in DP.
    private static final int ICON_SIZE_DP = 16;

    // Maximum width of the container TextView. Max height is maxWidth * 0.75.
    private final int mViewport;
    private final float mFontSize;
    private final ClickListener mClicker;

    protected SpanFormatter(final TextView container, final ClickListener clicker) {
        super(container);

        mViewport = container.getMaxWidth();
        mFontSize = container.getTextSize();
        mClicker = clicker;
    }

    public static Spanned toSpanned(final TextView container, final Drafty content,
                                    final ClickListener clicker) {
        if (content == null) {
            return new SpannedString("");
        }
        if (content.isPlain()) {
            return new SpannedString(content.toString());
        }

        AbstractDraftyFormatter.TreeNode result = content.format(new SpanFormatter(container, clicker));
        if (result instanceof StyledTreeNode) {
            return ((StyledTreeNode)result).toSpanned();
        }

        return new SpannedString("");
    }

    public static boolean hasClickableSpans(final Drafty content) {
        if (content != null) {
            Drafty.Entity[] entities = content.getEntities();
            if (entities == null) {
                return false;
            }

            for (Drafty.Entity ent : entities) {
                if (ent == null || ent.tp == null) {
                    continue;
                }
                switch (ent.tp) {
                    case "BN":
                    case "LN":
                    case "MN":
                    case "HT":
                    case "IM":
                    case "EX":
                        return true;
                    default:
                }
            }
        }
        return false;
    }

    // Scale image dimensions to fit under the given viewport size.
    protected static float scaleBitmap(int srcWidth, int srcHeight, int viewportWidth, float density) {
        if (srcWidth == 0 || srcHeight == 0) {
            return 0f;
        }

        // Convert DP to pixels.
        float width = srcWidth * density;
        float height = srcHeight * density;
        float maxWidth = viewportWidth - IMAGE_H_PADDING * density;

        // Make sure the scaled bitmap is no bigger than the viewport size;
        float scaleX = Math.min(width, maxWidth) / width;
        float scaleY = Math.min(height, maxWidth * 0.75f) / height;
        return Math.min(scaleX, scaleY);
    }

    @Override
    protected StyledTreeNode handleStrong(Object content) {
        return new StyledTreeNode(new StyleSpan(Typeface.BOLD), content);
    }

    @Override
    protected StyledTreeNode handleEmphasized(Object content) {
        return new StyledTreeNode(new StyleSpan(Typeface.ITALIC), content);
    }

    @Override
    protected StyledTreeNode handleDeleted(Object content) {
        return new StyledTreeNode(new StrikethroughSpan(), content);
    }

    @Override
    protected StyledTreeNode handleCode(Object content) {
        return new StyledTreeNode(new TypefaceSpan("monospace"), content);
    }

    @Override
    protected StyledTreeNode handleHidden(Object content) {
        return null;
    }

    @Override
    protected StyledTreeNode handleLineBreak() {
        return new StyledTreeNode("\n");
    }

    @Override
    protected StyledTreeNode handleLink(Context ctx, Object content, Map<String, Object> data) {
        try {
            // We don't need to specify an URL for URLSpan
            // as it's not going to be used.
            return new StyledTreeNode(new URLSpan("") {
                @Override
                public void onClick(View widget) {
                    if (mClicker != null) {
                        mClicker.onClick("LN", data);
                    }
                }
            }, content);
        } catch (ClassCastException | NullPointerException ignored) {
        }
        return null;
    }

    @Override
    protected StyledTreeNode handleMention(Context ctx, Object content, Map<String, Object> data) {
        return null;
    }

    @Override
    protected StyledTreeNode handleHashtag(Context ctx, Object content, Map<String, Object> data) {
        return null;
    }

    @Override
    protected StyledTreeNode handleImage(final Context ctx, Object content, final Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        StyledTreeNode result = null;
        // Bitmap dimensions specified by the sender.
        int width = 0, height = 0;
        Object tmp;
        if ((tmp = data.get("width")) instanceof Number) {
            width = ((Number) tmp).intValue();
        }
        if ((tmp = data.get("height")) instanceof Number) {
            height = ((Number) tmp).intValue();
        }

        // Calculate scaling factor for images to fit into the viewport.
        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        float scale = scaleBitmap(width, height, mViewport, metrics.density);
        // Bitmap dimensions specified by the sender converted to viewport size in display pixels.
        int scaledWidth = 0, scaledHeight = 0;
        if (scale > 0) {
            scaledWidth = (int) (width * scale * metrics.density);
            scaledHeight = (int) (height * scale * metrics.density);
        }

        CharacterStyle span = null;
        Bitmap bmpPreview = null;

        // Inline image.
        Object val = data.get("val");
        if (val != null) {
            try {
                // True if inline image is only a preview: try to use out of band image (default).
                boolean isPreviewOnly = true;
                // If the message is not yet sent, the bits could be raw byte[] as opposed to
                // base64-encoded.
                byte[] bits = (val instanceof String) ?
                        Base64.decode((String) val, Base64.DEFAULT) : (byte[]) val;
                bmpPreview = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                if (bmpPreview != null) {
                    // Check if the inline bitmap is big enough to be used as primary image.
                    int previewWidth = bmpPreview.getWidth();
                    int previewHeight = bmpPreview.getHeight();
                    if (scale == 0) {
                        // If dimensions are not specified in the attachment metadata, try to use bitmap dimensions.
                        scale = scaleBitmap(previewWidth, previewHeight, mViewport, metrics.density);
                        if (scale != 0) {
                            // Because sender-provided dimensions are unknown or invalid we have to use
                            // this inline image as the primary one (out of band image is ignored).
                            isPreviewOnly = false;
                            scaledWidth = (int) (previewWidth * scale * metrics.density);
                            scaledHeight = (int) (previewHeight * scale * metrics.density);
                        }
                    }

                    Bitmap oldBmp = bmpPreview;
                    if (scale == 0) {
                        // Can't scale the image. There must be something wrong with it.
                        bmpPreview = null;
                    } else {
                        bmpPreview = Bitmap.createScaledBitmap(bmpPreview, scaledWidth, scaledHeight, true);
                        // Check if the image is big enough to use as the primary one (ignoring possible full-size
                        // out-of-band image). If it's not already suitable for preview don't bother.
                        isPreviewOnly = isPreviewOnly && previewWidth * metrics.density < scaledWidth * 0.35f;

                    }
                    oldBmp.recycle();
                }

                if (bmpPreview != null && !isPreviewOnly) {
                    span = new ImageSpan(ctx, bmpPreview);
                }
            } catch (Exception ex) {
                Log.w(TAG, "Broken image preview", ex);
            }
        }

        // Out of band image.
        if (span == null && (val = data.get("ref")) instanceof String) {
            String ref = (String) val;
            URL url = Cache.getTinode().toAbsoluteURL(ref);
            if (scale > 0 && url != null) {
                Drawable fg, bg = null;

                // "Image loading" placeholder.
                Drawable placeholder;
                if (bmpPreview != null) {
                    placeholder = new BitmapDrawable(ctx.getResources(), bmpPreview);
                    bg = placeholder;
                } else {
                    fg = AppCompatResources.getDrawable(ctx, R.drawable.ic_image);
                    if (fg != null) {
                        fg.setBounds(0, 0, fg.getIntrinsicWidth(), fg.getIntrinsicHeight());
                    }
                    placeholder = UiUtils.getPlaceholder(ctx, fg, null, scaledWidth, scaledHeight);
                }

                // "Failed to load image" placeholder.
                fg = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
                if (fg != null) {
                    fg.setBounds(0, 0, fg.getIntrinsicWidth(), fg.getIntrinsicHeight());
                }
                Drawable onError = UiUtils.getPlaceholder(ctx, fg, bg, scaledWidth, scaledHeight);

                span = new UrlImageSpan(mContainer, scaledWidth, scaledHeight, placeholder, onError);
                ((UrlImageSpan) span).load(Cache.getTinode().toAbsoluteURL(ref));
            }
        }

        if (span == null) {
            // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
            Drawable broken = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
            if (broken != null) {
                broken.setBounds(0, 0, broken.getIntrinsicWidth(), broken.getIntrinsicHeight());
                span = new ImageSpan(UiUtils.getPlaceholder(ctx, broken, null, scaledWidth, scaledHeight));
                result = new StyledTreeNode(span, content);
            }
        } else if (mClicker != null) {
            // Make image clickable by wrapping ImageSpan into a ClickableSpan.
            result = new StyledTreeNode(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    mClicker.onClick("IM", data);
                }
            }, (Object) null);
            result.addNode(new StyledTreeNode(span, content));
        }

        return result;
    }

    @Override
    protected StyledTreeNode handleFormRow(Context ctx, Map<String, Object> data, Object content) {
        return new StyledTreeNode(content);
    }

    @Override
    protected StyledTreeNode handleForm(Context ctx, Map<String, Object> data, Object content) {
        StyledTreeNode span = null;
        if (content instanceof List) {
            // Add line breaks between form elements.
            try {
                @SuppressWarnings("unchecked")
                List<TreeNode> children = (List<TreeNode>) content;
                if (children.size() > 0) {
                    span = new StyledTreeNode();
                    for (TreeNode child : children) {
                        span.addNode(child);
                        span.addNode(new StyledTreeNode("\n"));
                    }
                }
            } catch (ClassCastException ex) {
                Log.w(TAG, "Wrong type of content in Drafty", ex);
            }

            if (span != null && span.isEmpty()) {
                span = null;
            } else {
                mContainer.setLineSpacing(0, FORM_LINE_SPACING);
            }
        }
        return span;
    }

    @Override
    protected StyledTreeNode handleAttachment(final Context ctx, final Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        try {
            String mimeType = (String) data.get("mime");
            if ("application/json".equals(mimeType)) {
                // Skip JSON attachments.
                // They are not meant to be user-visible.
                return null;
            }
        } catch (ClassCastException ignored) {
        }

        StyledTreeNode result = new StyledTreeNode();
        // Insert document icon
        Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_file);
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        ImageSpan span = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
        final Rect bounds = span.getDrawable().getBounds();
        result.addNode(new StyledTreeNode(new SubscriptSpan(), new StyledTreeNode(span, " ")));

        // Insert document's file name
        String fname = null;
        try {
            fname = (String) data.get("name");
        } catch (NullPointerException | ClassCastException ignored) {
        }
        if (TextUtils.isEmpty(fname)) {
            fname = ctx.getResources().getString(R.string.default_attachment_name);
        } else if (fname.length() > 32) {
            fname = fname.substring(0, 14) + "…" + fname.substring(fname.length() - 14);
        }
        result.addNode(new StyledTreeNode(new TypefaceSpan("monospace"), fname));

        // Add download link.
        if (mClicker != null) {
            boolean valid = (data.get("ref") instanceof String);

            // Insert linebreak then a clickable [↓ save] or [(!) unavailable] line.
            result.addNode(new StyledTreeNode("\n"));
            StyledTreeNode saveLink = new StyledTreeNode();
            // Add 'download file' icon
            icon = AppCompatResources.getDrawable(ctx, valid ?
                    R.drawable.ic_download_link : R.drawable.ic_error_gray);
            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            icon.setBounds(0, 0,
                    (int) (ICON_SIZE_DP * metrics.density),
                    (int) (ICON_SIZE_DP * metrics.density));
            saveLink.addNode(new StyledTreeNode(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), " "));
            if (valid) {
                // Clickable "save".
                saveLink.addNode(new StyledTreeNode(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        mClicker.onClick("EX", data);
                    }
                }, ctx.getResources().getString(R.string.download_attachment)));
            } else {
                // Grayed-out "unavailable".
                saveLink.addNode(new StyledTreeNode(new ForegroundColorSpan(Color.GRAY),
                        " " + ctx.getResources().getString(R.string.unavailable)));
            }
            // Add space on the left to make the link appear under the file name.
            result.addNode(new StyledTreeNode(new LeadingMarginSpan.Standard(bounds.width()), saveLink));
        }
        return result;
    }

    // Button: URLSpan wrapped into LineHeightSpan and then BorderedSpan.
    @Override
    protected StyledTreeNode handleButton(final Context ctx, final Map<String, Object> data, final Object content) {
        // This is needed for button shadows.
        mContainer.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        DisplayMetrics metrics = mContainer.getContext().getResources().getDisplayMetrics();
        // Size of a DIP pixel.
        float dipSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, metrics);

        // Create BorderSpan.
        final StyledTreeNode span = new StyledTreeNode(
                (CharacterStyle) new ButtonSpan(mContainer.getContext(), mFontSize, dipSize), (Object) null);

        // Wrap URLSpan into BorderSpan.
        span.addNode(new StyledTreeNode(new URLSpan("") {
            @Override
            public void onClick(View widget) {
                if (mClicker != null) {
                    mClicker.onClick("BN", data);
                }
            }
        }, content));

        return span;
    }

    // Unknown or unsupported element.
    @Override
    protected StyledTreeNode handleUnknown(final Context ctx, final Object content, final Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        // Does object have viewport dimensions?
        int width = 0, height = 0;
        Object tmp;
        if ((tmp = data.get("width")) instanceof Number) {
            width = ((Number) tmp).intValue();
        }
        if ((tmp = data.get("height")) instanceof Number) {
            height = ((Number) tmp).intValue();
        }

        if (width <= 0 || height <= 0) {
            return handleAttachment(ctx, data);
        }

        // Calculate scaling factor for images to fit into the viewport.
        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        float scale = scaleBitmap(width, height, mViewport, metrics.density);
        // Bitmap dimensions specified by the sender converted to viewport size in display pixels.
        int scaledWidth = 0, scaledHeight = 0;
        if (scale > 0) {
            scaledWidth = (int) (width * scale * metrics.density);
            scaledHeight = (int) (height * scale * metrics.density);
        }

        StyledTreeNode result = null;
        Drawable unkn = AppCompatResources.getDrawable(ctx, R.drawable.ic_unkn_type);
        if (unkn != null) {
            unkn.setBounds(0, 0, unkn.getIntrinsicWidth(), unkn.getIntrinsicHeight());
            CharacterStyle span = new ImageSpan(UiUtils.getPlaceholder(ctx, unkn, null, scaledWidth, scaledHeight));
            result = new StyledTreeNode(span, content);
        }

        return result;
    }

    // Plain (unstyled) content.
    @Override
    protected StyledTreeNode handlePlain(final Object content) {
        return new StyledTreeNode(content);
    }

    public interface ClickListener {
        void onClick(String type, Map<String, Object> data);
    }
}
