package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

/**
 * Convert Drafty object into a Spanned object
 */
public class SpanFormatter implements Drafty.Formatter<SpanFormatter.TreeNode> {
    private static final String TAG = "SpanFormatter";
    private static final float FORM_LINE_SPACING = 1.2f;
    // Additional horizontal padding otherwise images sometimes fail to render.
    private static final int IMAGE_H_PADDING = 8;
    // Size of Download and Error icons in DP.
    private static final int ICON_SIZE_DP = 16;

    private final TextView mContainer;
    // Maximum width of the container TextView. Max height is maxWidth * 0.75.
    private final int mViewport;
    private final float mFontSize;
    private final ClickListener mClicker;

    private SpanFormatter(final TextView container, final ClickListener clicker) {
        mContainer = container;
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
        TreeNode result = content.format(new SpanFormatter(container, clicker));
        return result.toSpanned();
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
    private static float scaleBitmap(int srcWidth, int srcHeight, int viewportWidth, float density) {
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

    private TreeNode handleImage(final Context ctx, Object content, final Map<String,Object> data) {
        TreeNode result = null;
        if (data != null) {
            CharacterStyle span = null;
            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            Object val = data.get("val");
            if (val != null) {
                try {
                    // If the message is not yet sent, the bits could be raw byte[] as opposed to
                    // base64-encoded.
                    byte[] bits = (val instanceof String) ?
                            Base64.decode((String) val, Base64.DEFAULT) : (byte[]) val;
                    Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                    if (bmp != null) {
                        // Scale bitmap for display density. The data.get("width") and data.get("height") are ignored.
                        int width = bmp.getWidth();
                        int height = bmp.getHeight();
                        float scale = scaleBitmap(width, height, mViewport, metrics.density);
                        if (scale == 0) {
                            bmp = null;
                        } else {
                            bmp = Bitmap.createScaledBitmap(bmp, (int) (width * scale * metrics.density),
                                    (int) (height * scale * metrics.density), true);
                        }
                    }
                    if (bmp != null) {
                        span = new ImageSpan(ctx, bmp);
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "Broken image", ex);
                }
            } else {
                Object ref = data.get("ref");
                if (ref instanceof String) {
                    int width = 0, height = 0;
                    Object tmp = data.get("width");
                    if (tmp instanceof Number) {
                        width = ((Number) tmp).intValue();
                    }
                    tmp = data.get("height");
                    if (tmp instanceof Number) {
                        height = ((Number) tmp).intValue();
                    }
                    float scale = scaleBitmap(width, height, mViewport, metrics.density);
                    if (scale > 0) {
                        Drawable onError = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
                        if (onError != null) {
                            onError.setBounds(0, 0, onError.getIntrinsicWidth(), onError.getIntrinsicHeight());
                        }
                        Drawable placeholder = AppCompatResources.getDrawable(ctx, R.drawable.ic_image);
                        if (placeholder != null) {
                            placeholder.setBounds(0, 0, placeholder.getIntrinsicWidth(), placeholder.getIntrinsicHeight());
                        }
                        width = (int) (width * scale * metrics.density);
                        height = (int) (height * scale * metrics.density);
                        span = new UrlImageSpan(mContainer, width, height, placeholder, onError);
                        ((UrlImageSpan) span).load(Cache.getTinode().authorizeURL((String) ref));
                    }
                }
            }

            if (span == null) {
                // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
                Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
                if (icon != null) {
                    icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                    span = new ImageSpan(icon);
                    result = new TreeNode(span, content);
                }
            } else if (mClicker != null) {
                // Make image clickable by wrapping ImageSpan into a ClickableSpan.
                result = new TreeNode(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        mClicker.onClick("IM", data);
                    }
                }, (CharSequence) null);
                result.addNode(new TreeNode(span, content));
            }
        }

        return result;
    }

    private TreeNode handleAttachment(final Context ctx,
                                      Object unused,
                                      final Map<String,Object> data) {
        TreeNode result = new TreeNode();
        if (data != null) {
            try {
                String mimeType = (String) data.get("mime");
                if ("application/json".equals(mimeType)) {
                    // Skip JSON attachments.
                    // They are not meant to be user-visible.
                    return null;
                }
            } catch (ClassCastException ignored) {
            }

            // Insert document icon
            Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_file);
            //noinspection ConstantConditions
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
                //noinspection ConstantConditions
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
        }
        return result;
    }

    // Button: URLSpan wrapped into LineHeightSpan and then BorderedSpan.
    private TreeNode handleButton(final Map<String,Object> data, final Object content) {
        // This is needed for button shadows.
        mContainer.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        DisplayMetrics metrics = mContainer.getContext().getResources().getDisplayMetrics();
        // Size of a DIP pixel.
        float dipSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, metrics);

        // Create BorderSpan.
        final TreeNode span = new TreeNode(
                (CharacterStyle) new BorderedSpan(mContainer.getContext(), mFontSize, dipSize),
                (CharSequence) null);

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

    @Override
    public TreeNode apply(final String tp, final Map<String,Object> data, final Object content) {
        if (tp != null) {
            TreeNode span = null;
            switch (tp) {
                case "ST":
                    span = new TreeNode(new StyleSpan(Typeface.BOLD), content);
                    break;
                case "EM":
                    span = new TreeNode(new StyleSpan(Typeface.ITALIC), content);
                    break;
                case "DL":
                    span = new TreeNode(new StrikethroughSpan(), content);
                    break;
                case "CO":
                    span = new TreeNode(new TypefaceSpan("monospace"), content);
                    break;
                case "BR":
                    span = new TreeNode("\n");
                    break;
                case "LN":
                    try {
                        // We don't need to specify an URL for URLSpan
                        // as it's not going to be used.
                        span = new TreeNode(new URLSpan("") {
                            @Override
                            public void onClick(View widget) {
                                if (mClicker != null) {
                                    mClicker.onClick("LN", data);
                                }
                            }
                        }, content);
                    } catch (ClassCastException | NullPointerException ignored) {}
                    break;
                case "MN":
                case "HT":
                    break;
                case "HD":
                    // Hidden text
                    break;
                case "IM":
                    // Additional processing for images
                    span = handleImage(mContainer.getContext(), content, data);
                    break;
                case "EX":
                    // Attachments
                    span = handleAttachment(mContainer.getContext(), content, data);
                    break;
                case "BN":
                    // Button
                    span = handleButton(data, content);
                    break;
                case "FM":
                    // Form
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
                    break;
                case "RW":
                    // Form element formatting is dependent on element content.
                    span = new TreeNode(content);
                    break;
            }
            return span;
        }
        return new TreeNode(content);
    }

    public interface ClickListener {
        void onClick(String type, Map<String,Object> data);
    }

    // Structure representing Drafty as a tree of formatting nodes.
    static class TreeNode {
        private CharacterStyle cStyle;
        private ParagraphStyle pStyle;
        private CharSequence text;
        private List<TreeNode> children;

        TreeNode() {
            cStyle = null;
            pStyle = null;
            text = null;
            children = null;
        }

        private TreeNode(CharacterStyle style, List<TreeNode> children) {
            this.cStyle = style;
            this.text = null;
            this.children = children;
        }

        private TreeNode(CharacterStyle style, CharSequence text) {
            this.cStyle = style;
            this.text = text;
            this.children = null;
        }

        private TreeNode(ParagraphStyle style, CharSequence text) {
            this.pStyle = style;
            this.text = text;
            this.children = null;
        }

        TreeNode(CharSequence content) {
            this.text = content;
        }

        TreeNode(Object content) {
            assignContent(content);
        }

        @SuppressWarnings("unchecked")
        private void assignContent(Object content) {
            if (content == null) {
                return;
            }
            if (content instanceof CharSequence) {
                text = (CharSequence) content;
            } else if (content instanceof List) {
                children = (List<TreeNode>) content;
            } else if (content instanceof TreeNode) {
                if (children == null) {
                    children = new ArrayList<>();
                }
                children.add((TreeNode) content);
            } else {
                throw new IllegalArgumentException("Invalid content");
            }
        }

        TreeNode(CharacterStyle style, Object content) {
            this.cStyle = style;
            assignContent(content);
        }

        TreeNode(ParagraphStyle style, Object content) {
            this.pStyle = style;
            assignContent(content);
        }

        void addNode(TreeNode node) {
            if (node == null) {
                return;
            }

            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(node);
        }

        void addNode(CharSequence content) {
            if (content == null) {
                return;
            }
            addNode(new TreeNode(content));
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

        boolean isEmpty() {
            return (text == null || text.equals("")) &&
                    (children == null || children.size() == 0);
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("{");
            if (text != null) {
                if (text.equals("\n")) {
                    result.append("txt='BR'");
                } else {
                    result.append("txt='").append(text.toString()).append("'");
                }
            } else if (children != null) {
                if (children.size() == 0) {
                    result.append("ERROR:EMPTY");
                } else if (children.size() == 1) {
                    result.append(children.get(0).toString());
                } else {
                    result.append("[");
                    for (TreeNode child : children) {
                        if (child != null) {
                            result.append(child.toString());
                            result.append(",");
                        } else {
                            result.append("ERROR:NULL,");
                        }
                    }
                    // Remove dangling comma.
                    result.setLength(result.length() - 1);
                    result.append("]");
                }
            } else {
                result.append("ERROR:NULL");
            }
            result.append(styleName());
            result.append("}");
            return result.toString();
        }

        private String styleName() {
            if (pStyle != null) {
                return ", stl="+pStyle.getClass().getName();
            }
            if (cStyle != null) {
                return ", stl="+cStyle.getClass().getName();
            }
            return "";
        }

        Spanned toSpanned() {
            SpannableStringBuilder spanned = new SpannableStringBuilder();
            if (text != null) {
                spanned.append(text);
            } else if (children != null) {
                for (TreeNode child : children) {
                    if (child == null) {
                        Log.w(TAG, "NULL child. Should not happen!!!");
                    } else {
                        spanned.append(child.toSpanned());
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
