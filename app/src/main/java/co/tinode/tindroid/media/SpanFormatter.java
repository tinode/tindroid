package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

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

    private final View mContainer;
    private final int mViewport;
    private final ClickListener mClicker;

    public SpanFormatter(final View container, final int viewport, final ClickListener clicker) {
        mContainer = container;
        mViewport = viewport;
        mClicker = clicker;
    }


    public static Spanned toSpanned(final Context ctx, final Drafty content, final int viewport,
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
                CharacterStyle span = null;
                int offset = -1, length = -1;
                String tp = style.getType();
                entity = content.getEntity(style);

                final Map<String,Object> data;
                if (entity != null) {
                    tp = entity.getType();
                    data = entity.getData();
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

    public static boolean hasClickableSpans(final Drafty content) {
        if (content != null) {
            Drafty.Entity[] entities = content.getEntities();
            if (entities == null) {
                return false;
            }

            for (Drafty.Entity ent : entities) {
                if (ent.tp == null) {
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

    private List<TreeNode> handleImage(final Context ctx, Object content, final Map<String,Object> data,
                                             final int viewport, final ClickListener clicker) {
        List<TreeNode> result = new ArrayList<>();
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
                if (icon != null) {
                    icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                    span = new ImageSpan(icon);
                }
            } else {
                span = new ImageSpan(ctx, bmp);
            }
            final boolean valid = bmp != null;

            // Add image span
            result.add(new TreeNode(span, content));
            if (clicker != null) {
                span = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        clicker.onClick("IM", valid ? data : null);
                    }
                };
                // Make image clickable
                result.add(new TreeNode(span, content));
            }
        }

        return result;
    }

    private TreeNode apply(final String tp, final Map<String,Object> data, Object child) {
        if (tp != null) {
            List<TreeNode> spans = new ArrayList<>();
            switch (tp) {
                case "ST":
                    spans.add(new TreeNode(new StyleSpan(Typeface.BOLD), child));
                    break;
                case "EM":
                    spans.add(new TreeNode(new StyleSpan(Typeface.ITALIC), child));
                    break;
                case "DL":
                    spans.add(new TreeNode(new StrikethroughSpan(), child));
                    break;
                case "CO":
                    spans.add(new TreeNode(new TypefaceSpan("monospace"), child));
                    break;
                case "BR":
                    spans.add(new TreeNode(null, "\n"));
                    break;
                case "LN":
                    try {
                        // We don't need to specify an URL for URLSpan
                        // as it's not going to be used.
                        spans.add(new TreeNode(new URLSpan("") {
                            @Override
                            public void onClick(View widget) {
                                if (mClicker != null) {
                                    mClicker.onClick("LN", data);
                                }
                            }
                        }, child));
                    } catch (ClassCastException | NullPointerException ignored) {}
                    break;
                case "MN": break;
                case "HT": break;
                case "IM":
                    // Additional processing for images
                    spans.addAll(handleImage(mContainer.getContext(), child, data, mViewport, mClicker));
                    break;
                case "BN":
                    // Button
                    try {
                        spans.add(new TreeNode(new URLSpan("") {
                            @Override
                            public void onClick(View widget) {
                                if (mClicker != null) {
                                    mClicker.onClick("BN", data);
                                }
                            }
                        }, child));
                    } catch (ClassCastException | NullPointerException ignored) {}
                    break;
                case "FM":
                    // Form
                    if (data != null) {
                        try {
                            if (children.size() > 0) {
                                float scale = mContainer.getContext().getResources().getDisplayMetrics().density;
                                form.setMinimumWidth((int)(280 * scale + 0.5f));
                                for (View view : children) {
                                    form.addView(view);
                                }
                            }
                        } catch (ClassCastException ignored) {}
                    }
                    break;
                case "RW":
                    // Form element formatting is dependent on element content.
                    break;
            }

            return React.createElement(el, attr, values);
        } else {
            return values;
        }
    }

    @Override
    public TreeNode apply(String tp, Map<String, Object> attr, String text) {
        return null;
    }

    @Override
    public TreeNode apply(String tp, Map<String, Object> attr, List<TreeNode> children) {
        return null;
    }

    public interface ClickListener {
        void onClick(String type, Map<String,Object> data);
    }

    // Structure representing Drafty as a tree of formatting nodes.
    static class TreeNode {
        CharacterStyle style;
        CharSequence text;
        private List<TreeNode> children;

        public TreeNode(CharacterStyle style, List<TreeNode> children) {
            this.style = style;
            this.children = children;
        }

        public TreeNode(CharacterStyle style, CharSequence text) {
            this.style = style;
            this.text = text;
        }

        @SuppressWarnings("unchecked")
        public TreeNode(CharacterStyle style, Object content) {
            this.style = style;
            if (content instanceof CharSequence) {
                this.text = (CharSequence) content;
            } else if (content instanceof List) {
                this.children = (List<TreeNode>) content;
            }
        }

        public TreeNode addNode(CharacterStyle span, CharSequence text) {
            return addNode(new TreeNode(span, text));
        }

        public TreeNode addNode(CharacterStyle span, List<TreeNode> children) {
            return addNode(new TreeNode(span, children));
        }

        public TreeNode addNode(TreeNode node) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(node);
            return node;
        }
    }
}
