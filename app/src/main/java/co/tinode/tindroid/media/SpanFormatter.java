package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.ParagraphStyle;
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
public class SpanFormatter extends AbstractDraftyFormatter<SpanFormatter.TreeNode> {
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
        if (result instanceof TreeNode) {
            return ((TreeNode)result).toSpanned();
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
    protected TreeNode handleStrong(Object content) {
        return new TreeNode(new StyleSpan(Typeface.BOLD), content);
    }

    @Override
    protected TreeNode handleEmphasized(Object content) {
        return new TreeNode(new StyleSpan(Typeface.ITALIC), content);
    }

    @Override
    protected TreeNode handleDeleted(Object content) {
        return new TreeNode(new StrikethroughSpan(), content);
    }

    @Override
    protected TreeNode handleCode(Object content) {
        return new TreeNode(new TypefaceSpan("monospace"), content);
    }

    @Override
    protected TreeNode handleHidden(Object content) {
        return null;
    }

    @Override
    protected TreeNode handleLineBreak() {
        return new TreeNode("\n");
    }

    @Override
    protected TreeNode handleLink(Context ctx, Object content, Map<String, Object> data) {
        try {
            // We don't need to specify an URL for URLSpan
            // as it's not going to be used.
            return new TreeNode(new URLSpan("") {
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
    protected TreeNode handleMention(Context ctx, Object content, Map<String, Object> data) {
        return null;
    }

    @Override
    protected TreeNode handleHashtag(Context ctx, Object content, Map<String, Object> data) {
        return null;
    }

    @Override
    protected TreeNode handleImage(final Context ctx, Object content, final Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        TreeNode result = null;
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
                result = new TreeNode(span, content);
            }
        } else if (mClicker != null) {
            // Make image clickable by wrapping ImageSpan into a ClickableSpan.
            result = new TreeNode(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    mClicker.onClick("IM", data);
                }
            }, (Object) null);
            result.addNode(new TreeNode(span, content));
        }

        return result;
    }

    @Override
    protected TreeNode handleFormRow(Context ctx, Map<String, Object> data, Object content) {
        return new TreeNode(content);
    }

    @Override
    protected TreeNode handleForm(Context ctx, Map<String, Object> data, Object content) {
        TreeNode span = null;
        if (content instanceof List) {
            // Add line breaks between form elements.
            try {
                @SuppressWarnings("unchecked")
                List<TreeNode> children = (List<TreeNode>) content;
                if (children.size() > 0) {
                    span = new TreeNode();
                    for (TreeNode child : children) {
                        span.addNode(child);
                        span.addNode("\n");
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
    protected TreeNode handleAttachment(final Context ctx, final Map<String, Object> data) {
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

        TreeNode result = new TreeNode();
        // Insert document icon
        Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_file);
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        ImageSpan span = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
        final Rect bounds = span.getDrawable().getBounds();
        result.addNode(new SubscriptSpan(), new TreeNode(span, " "));

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
        result.addNode(new TypefaceSpan("monospace"), fname);

        // Add download link.
        if (mClicker != null) {
            boolean valid = (data.get("ref") instanceof String);

            // Insert linebreak then a clickable [↓ save] or [(!) unavailable] line.
            result.addNode("\n");
            TreeNode saveLink = new TreeNode();
            // Add 'download file' icon
            icon = AppCompatResources.getDrawable(ctx, valid ?
                    R.drawable.ic_download_link : R.drawable.ic_error_gray);
            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            icon.setBounds(0, 0,
                    (int) (ICON_SIZE_DP * metrics.density),
                    (int) (ICON_SIZE_DP * metrics.density));
            saveLink.addNode(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), " ");
            if (valid) {
                // Clickable "save".
                saveLink.addNode(new TreeNode(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        mClicker.onClick("EX", data);
                    }
                }, ctx.getResources().getString(R.string.download_attachment)));
            } else {
                // Grayed-out "unavailable".
                saveLink.addNode(new ForegroundColorSpan(Color.GRAY),
                        " " + ctx.getResources().getString(R.string.unavailable));
            }
            // Add space on the left to make the link appear under the file name.
            result.addNode(new LeadingMarginSpan.Standard(bounds.width()), saveLink);
        }
        return result;
    }

    // Button: URLSpan wrapped into LineHeightSpan and then BorderedSpan.
    @Override
    protected TreeNode handleButton(final Context ctx, final Map<String, Object> data, final Object content) {
        // This is needed for button shadows.
        mContainer.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        DisplayMetrics metrics = mContainer.getContext().getResources().getDisplayMetrics();
        // Size of a DIP pixel.
        float dipSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, metrics);

        // Create BorderSpan.
        final TreeNode span = new TreeNode(
                (CharacterStyle) new BorderedSpan(mContainer.getContext(), mFontSize, dipSize), (Object) null);

        // Wrap URLSpan into BorderSpan.
        span.addNode(new URLSpan("") {
            @Override
            public void onClick(View widget) {
                if (mClicker != null) {
                    mClicker.onClick("BN", data);
                }
            }
        }, content);

        return span;
    }

    // Unknown or unsupported element.
    @Override
    protected TreeNode handleUnknown(final Context ctx, final Object content, final Map<String, Object> data) {
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

        TreeNode result = null;
        Drawable unkn = AppCompatResources.getDrawable(ctx, R.drawable.ic_unkn_type);
        if (unkn != null) {
            unkn.setBounds(0, 0, unkn.getIntrinsicWidth(), unkn.getIntrinsicHeight());
            CharacterStyle span = new ImageSpan(UiUtils.getPlaceholder(ctx, unkn, null, scaledWidth, scaledHeight));
            result = new TreeNode(span, content);
        }

        return result;
    }

    public interface ClickListener {
        void onClick(String type, Map<String, Object> data);
    }

    // Structure representing Drafty as a tree of formatting nodes.
    static class TreeNode extends AbstractDraftyFormatter.TreeNode {
        private CharacterStyle cStyle;
        private ParagraphStyle pStyle;

        TreeNode() {
            super();
            cStyle = null;
            pStyle = null;
        }

        private TreeNode(CharSequence content) {
            super(content);
        }

        private TreeNode(Object content) {
            super(content);
        }

        private TreeNode(CharacterStyle style, CharSequence text) {
            super(text);
            this.cStyle = style;
        }

        TreeNode(CharacterStyle style, Object content) {
            super(content);
            this.cStyle = style;
        }

        TreeNode(ParagraphStyle style, Object content) {
            super(content);
            this.pStyle = style;
        }

        void addNode(CharacterStyle style, Object content) {
            if (content == null) {
                return;
            }
            addNode(new TreeNode(style, content));
        }

        void addNode(ParagraphStyle style, Object content) {
            if (content == null) {
                return;
            }
            addNode(new TreeNode(style, content));
        }

        Spanned toSpanned() {
            SpannableStringBuilder spanned = new SpannableStringBuilder();
            if (isPlain()) {
                spanned.append(getText());
            } else if (hasChildren()) {
                for (AbstractDraftyFormatter.TreeNode child : getChildren()) {
                    if (child == null) {
                        Log.w(TAG, "NULL child. Should not happen!!!");
                    } else if (child instanceof TreeNode){
                        spanned.append(((TreeNode) child).toSpanned());
                    } else {
                        Log.w(TAG, "Wrong child class: " + child.getClass().getSimpleName());
                    }
                }
            }

            if (spanned.length() > 0 && (cStyle != null || pStyle != null)) {
                spanned.setSpan(cStyle != null ? cStyle : pStyle,
                        0, spanned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spanned;
        }
    }
}
