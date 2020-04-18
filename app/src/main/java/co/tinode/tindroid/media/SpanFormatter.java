package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
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
import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

/**
 * Convert Drafty object into a Spanned object
 */
public class SpanFormatter implements Drafty.Formatter<SpanFormatter.TreeNode> {
    private static final String TAG = "SpanFormatter";
    private static final float FORM_LINE_SPACING = 1.5f;
    private static final float BUTTON_HEIGHT = FORM_LINE_SPACING + 0.4f;
    // Additional horizontal padding otherwise images sometimes fail to render.
    private static final int IMAGE_H_PADDING = 8;

    private final TextView mContainer;
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

    private TreeNode handleImage(final Context ctx, Object content, final Map<String,Object> data) {
        TreeNode result = null;
        if (data != null) {
            CharacterStyle span = null;
            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            Bitmap bmp = null;
            try {
                Object val = data.get("val");
                // If the message is unsent, the bits could be raw byte[] as opposed to
                // base64-encoded.
                byte[] bits = (val instanceof String) ?
                        Base64.decode((String) val, Base64.DEFAULT) : (byte[]) val;
                // noinspection ConstantConditions (NullPointerException is explicitly caught).
                bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                // Scale bitmap for display density.
                float width = bmp.getWidth() * metrics.density;
                float height = bmp.getHeight() * metrics.density;
                float maxWidth = mViewport - IMAGE_H_PADDING * metrics.density;

                // Make sure the scaled bitmap is no bigger than the viewport size;
                float scaleX = Math.min(width, maxWidth) / width;
                float scaleY = Math.min(height, maxWidth * 0.75f) / height;
                float scale = Math.min(scaleX, scaleY);

                bmp = Bitmap.createScaledBitmap(bmp, (int)(width * scale), (int)(height * scale), true);

            } catch (Exception ex) {
                Log.w(TAG, "Broken Image", ex);
            }

            if (bmp == null) {
                // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
                Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
                if (icon != null) {
                    icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                    span = new ImageSpan(icon);
                }
            } else {
                span = new ImageSpan(ctx, bmp);
                Log.i(TAG, "Image span created " + span);
            }

            if (mClicker != null && bmp != null) {
                // Make image clickable but wrapping ImageSpan into a ClickableSpan.
                result = new TreeNode(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        mClicker.onClick("IM", data);
                    }
                }, (CharSequence) null);
                result.addNode(new TreeNode(span, content));
            } else {
                // Just create an image span
                result = new TreeNode(span, content);
            }
        }

        return result;
    }

    private TreeNode handleAttachment(final Context ctx,
                                      @SuppressWarnings("unused") Object unused,
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

            // result.addNode( "\n");

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
                // Insert linebreak then a clickable [↓ save] line
                result.addNode("\n");
                TreeNode saveLink = new TreeNode();
                // Add 'download file' icon
                icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_download_link);
                //noinspection ConstantConditions
                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                saveLink.addNode(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), " ");
                // Add "save" text and make it clickable.
                saveLink.addNode(new TreeNode(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        mClicker.onClick("EX", data);
                    }
                }, ctx.getResources().getString(R.string.download_attachment)));
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
                new BorderedSpan(mContainer.getContext(), mFontSize * BUTTON_HEIGHT, dipSize),
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
