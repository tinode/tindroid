package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.content.res.AppCompatResources;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.ParagraphStyle;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

/**
 * Convert Drafty object into a Spanned object
 */
public class SpanFormatter implements Drafty.Formatter<SpanFormatter.TreeNode> {
    private static final String TAG = "SpanFormatter";
    private static final float FORM_LINE_SPACING = 1.5f;

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
/*
    public static Spanned toSpanned2(final Context ctx, final Drafty content, final int viewport,
                                    final ClickListener clicker) {
        if (content == null) {
            // Malicious user may send a message with null content.
            return new SpannedString("");
        }

        SpannableStringBuilder text = new SpannableStringBuilder(content.toString());
        Drafty.Style[] fmt = content.getStyles();

        if (fmt != null) {
            Drafty.Entity entity;

            for (Drafty.Style style : fmt) {
                final Map<String,Object> data;
                CharacterStyle span = null;

                int offset = -1, length = -1;
                String tp = style.getType();
                if (tp == null) {
                    entity = content.getEntity(style);
                    if (entity != null) {
                        tp = entity.getType();
                        data = entity.getData();
                    } else {
                        data = null;
                    }
                } else {
                    data = null;
                }

                if (tp == null) {
                    Log.d(TAG, "Null type in " + style.toString());
                    continue;
                }

                switch (tp) {
                    case "ST": span = new StyleSpan(Typeface.BOLD); break;
                    case "EM": span = new StyleSpan(Typeface.ITALIC); break;
                    case "DL": span = new StrikethroughSpan(); break;
                    case "CO": span = new TypefaceSpan("monospace"); break;
                    case "BR":
                        text.replace(style.getOffset(), style.getOffset() + style.length(), "\n");
                        span = null;
                        break;
                    case "LN":
                        String url = data != null ? (String) data.get("url") : null;
                        span = url != null ? new URLSpan("") {
                            @Override
                            public void onClick(View widget) {
                                if (clicker != null) {
                                    clicker.onClick("LN", data);
                                }
                            }
                        } : null;
                        break;
                    case "MN": span = null; break;
                    case "HT": span = null; break;
                    case "HD":
                        // Hidden text
                        span = null;
                        text.replace(style.getOffset(), style.getOffset() + style.length(), "");
                        break;
                    case "IM":
                        // Image
                        if (data != null) {
                            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
                            Bitmap bmp = null;
                            try {
                                Object val = data.get("val");
                                // If the message is unsent, the bits could be raw byte[] as opposed to
                                // base64-encoded.
                                byte[] bits = (val instanceof String) ?
                                    Base64.decode((String) val, Base64.DEFAULT) : (byte[]) val;
                                bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                                // Scale bitmap for display density.
                                float width = bmp.getWidth() * metrics.density;
                                float height = bmp.getHeight() * metrics.density;
                                // Make sure the scaled bitmap is no bigger than the viewport size;
                                float scaleX = (width < viewport ? width : viewport) / width;
                                float scaleY = (height < viewport * 0.75f ? height : viewport * 0.75f) / height;
                                float scale = scaleX < scaleY ? scaleX : scaleY;

                                bmp = Bitmap.createScaledBitmap(bmp, (int)(width * scale), (int)(height * scale), true);

                            } catch (NullPointerException | IllegalArgumentException | ClassCastException ex) {
                                Log.e(TAG, "Broken Image", ex);
                            }

                            if (bmp == null) {
                                // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
                                Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
                                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                                span = new ImageSpan(icon);
                            } else {
                                span = new ImageSpan(ctx, bmp);
                            }
                            final boolean valid = bmp != null;

                            // Insert inline image
                            text.setSpan(span, style.getOffset(), style.getOffset() + style.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            if (clicker != null) {
                                span = new ClickableSpan() {
                                    @Override
                                    public void onClick(@NonNull View widget) {
                                        clicker.onClick("IM", valid ? data : null);
                                    }
                                };
                                // Make image clickable
                                text.setSpan(span, style.getOffset(), style.getOffset() + style.length(),
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            span = null;
                        }
                        break;
                    case "EX":
                        if (data != null) {
                            try {
                                String mimeType = (String) data.get("mime");
                                if ("application/json".equals(mimeType)) {
                                    // Skip JSON attachments.
                                    // They are not meant to be user-visible.
                                    continue;
                                }
                            } catch (NullPointerException | ClassCastException ignored) {
                            }
                            if (text.length() > 0) {
                                offset = text.length() + 1;
                                text.append("\n ");
                            } else {
                                offset = 0;
                                text.append(" ");
                            }

                            // Insert document icon
                            Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_insert_drive_file);
                            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                            span = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
                            Rect bounds = ((ImageSpan) span).getDrawable().getBounds();
                            text.setSpan(span, offset, offset + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            text.setSpan(new SubscriptSpan(), offset, offset + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                            // Insert document's file name
                            String fname = null;
                            try {
                                fname = (String) data.get("name");
                            } catch (NullPointerException | ClassCastException ignored) {
                            }
                            if (TextUtils.isEmpty(fname)) {
                                fname = ctx.getResources().getString(R.string.default_attachment_name);
                            } else if (fname.length() > 32) {
                                fname = fname.substring(0, 16) + "..." + fname.substring(fname.length() - 16);
                            }
                            SpannableStringBuilder substr = new SpannableStringBuilder(fname);
                            substr.setSpan(new TypefaceSpan("monospace"), 0, substr.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            text.append(substr);

                            if (clicker != null) {
                                // Insert linebreak then a clickable [↓ save] line
                                text.append("\n");
                                // Build the icon with a clicker.
                                substr = new SpannableStringBuilder(" ")
                                        .append(ctx.getResources().getString(R.string.download_attachment));
                                // Insert 'download file' icon
                                icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_file_download);
                                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                                substr.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), 0, 1,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                                // Make line clickable
                                span = new ClickableSpan() {
                                    @Override
                                    public void onClick(@NonNull View widget) {
                                        clicker.onClick("EX", data);
                                    }
                                };
                                substr.setSpan(span, 1, substr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                // Move line right to make it appear under the file name.
                                substr.setSpan(new LeadingMarginSpan.Standard(bounds.width()), 0, substr.length(),
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                                text.append(substr);
                            }
                            span = null;
                        }
                        break;
                    case "BN":
                        // Button
                        span = new URLSpan("") {
                            @Override
                            public void onClick(View widget) {
                                if (clicker != null) {
                                    clicker.onClick("BN", data);
                                }
                            }
                        };
                        text.setSpan(span, style.getOffset(), style.getOffset() + style.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        text.setSpan(new BorderedSpan(ctx), style.getOffset(), style.getOffset() + style.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case "FM":
                        // Form.
                        break;
                    case "RW":
                        // Logical grouping of elements, do nothing.
                        break;
                    default:
                        Log.i(TAG, "Unknown style '" + tp + "'");
                        break;
                }

                if (span != null) {
                    offset = offset < 0 ? style.getOffset() : offset;
                    length = length < 0 ? style.length() : length;
                    text.setSpan(span, offset, offset + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        return text;
    }
*/

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
                bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                // Scale bitmap for display density.
                float width = bmp.getWidth() * metrics.density;
                float height = bmp.getHeight() * metrics.density;
                // Make sure the scaled bitmap is no bigger than the viewport size;
                float scaleX = (width < mViewport ? width : mViewport) / width;
                float scaleY = (height < mViewport * 0.75f ? height : mViewport * 0.75f) / height;
                float scale = scaleX < scaleY ? scaleX : scaleY;

                bmp = Bitmap.createScaledBitmap(bmp, (int)(width * scale), (int)(height * scale), true);

            } catch (NullPointerException | IllegalArgumentException | ClassCastException ex) {
                Log.e(TAG, "Broken Image", ex);
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
            }

            // Add image span
            result = new TreeNode(span, content);
            if (mClicker != null && bmp != null) {
                // Make image clickable but wrapping it into a ClickableSpan.
                result = new TreeNode(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        mClicker.onClick("IM", data);
                    }
                }, result);
            }
        }

        return result;
    }

    private TreeNode handleAttachment(final Context ctx, Object unused, final Map<String,Object> data) {
        TreeNode result = new TreeNode();
        if (data != null) {
            try {
                String mimeType = (String) data.get("mime");
                if ("application/json".equals(mimeType)) {
                    // Skip JSON attachments.
                    // They are not meant to be user-visible.
                    return null;
                }
            } catch (NullPointerException | ClassCastException ignored) {
            }

            result.addNode( "\n");

            // Insert document icon
            Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_insert_drive_file);
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            CharacterStyle span = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
            final Rect bounds = ((ImageSpan) span).getDrawable().getBounds();
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
                fname = fname.substring(0, 16) + "..." + fname.substring(fname.length() - 16);
            }
            result.addNode(new TypefaceSpan("monospace"), fname);

            // Add download link.
            if (mClicker != null) {
                // Insert linebreak then a clickable [↓ save] line
                result.addNode("\n");
                TreeNode saveLink = new TreeNode();
                // Add 'download file' icon
                icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_file_download);
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
            span = null;
        }
        return result;
    }

    // Button: URLSpan wrapped into LineHeightSpan and then BorderedSpan.
    private TreeNode handleButton(final Map<String,Object> data, final Object content) {
        TreeNode span = new TreeNode(new BorderedSpan(mContainer.getContext(), mFontSize * FORM_LINE_SPACING), (
                CharSequence) null);
        TreeNode lhs = new TreeNode(new LineHeightSpan() {
            @Override
            public void chooseHeight(CharSequence text, int start, int end,
                                     int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
                float height = mFontSize * FORM_LINE_SPACING;
                int spacing = (int) ((height - mFontSize) * 0.5f);
                fm.bottom += spacing;
                fm.top -= spacing;
            }
        }, null);
        lhs.addNode(new URLSpan("") {
            @Override
            public void onClick(View widget) {
                if (mClicker != null) {
                    Log.d(TAG, "Button click: title=" + data.get("title") +", content="+content.toString());
                    //mClicker.onClick("BN", data);
                }
            }
        }, content);
        span.addNode(lhs);
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
                case "MN": break;
                case "HT": break;
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
                    Log.d(TAG, "Button " + content.toString() + "==" + data.get("title"));
                    span = handleButton(data, content);
                    break;
                case "FM":
                    // Form
                    if (content instanceof List) {
                        // Add line breaks between form elements.
                        // Don't insert a BR after the last element.
                        try {
                            List<TreeNode> children = (List<TreeNode>) content;
                            if (children.size() > 0) {
                                span = new TreeNode();
                                span.addNode(children.get(0));
                                for (int i = 1; i < children.size(); i++) {
                                    span.addNode("\n");
                                    span.addNode(children.get(i));
                                }
                            }
                        } catch (ClassCastException ex) {
                            Log.e(TAG, "Exception", ex);
                        }

                        if (span != null && span.isEmpty()) {
                            Log.d(TAG, "FM: empty span");
                            span = null;
                        } else {
                            mContainer.setLineSpacing(mFontSize * FORM_LINE_SPACING, 0);
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

        TreeNode(List<TreeNode> content) {
            this.children = content;
        }

        TreeNode(Object content) {
            assignContent(content);
        }

        private void assignContent(Object content) {
            if (content == null) {
                return;
            }
            if (content instanceof CharSequence) {
                this.text = (CharSequence) content;
            } else if (content instanceof List) {
                this.children = (List<TreeNode>) content;
            } else {
                throw new IllegalArgumentException("Invalid content");
            }
        }

        @SuppressWarnings("unchecked")
        TreeNode(CharacterStyle style, Object content) {
            this.cStyle = style;
            assignContent(content);
        }

        @SuppressWarnings("unchecked")
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

        void addNode(List<TreeNode> content) {
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

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("{");
            if (text != null) {
                if (text.equals("\n")) {
                    result.append("text='BR'");
                } else {
                    result.append("text='").append(text.toString()).append("'");
                }
            } else if (children != null) {
                result.append("children=[");
                for (TreeNode child : children) {
                    result.append(child.toString());
                    result.append(",");
                }
                result.append("]");
            } else {
                result.append(" no content");
            }
            result.append("}");
            return result.toString();
        }

        Spanned toSpanned() {
            SpannableStringBuilder spanned = new SpannableStringBuilder();
            if (text != null) {
                spanned.append(text);
            } else if (children != null) {
                for (TreeNode child : children) {
                    spanned.append(child.toSpanned());
                }
            }
            if (cStyle != null || pStyle != null) {
                spanned.setSpan(cStyle != null ? cStyle : pStyle,
                        0, spanned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spanned;
        }
    }
}
