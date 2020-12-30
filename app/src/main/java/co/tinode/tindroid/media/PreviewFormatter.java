package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.widget.TextView;

import java.util.Map;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

// Drafty formatter for creating one-line message previews.
public class PreviewFormatter extends AbstractDraftyFormatter<PreviewFormatter.MeasuredTreeNode> {
    private final float mFontSize;

    PreviewFormatter(final TextView container) {
        super(container);

        mFontSize = container.getTextSize();
    }

    public static Spanned toSpanned(final TextView container, final Drafty content, final int maxLength) {
        if (content == null) {
            return new SpannedString("");
        }
        if (content.isPlain()) {
            String text = content.toString();
            if (text.length() > maxLength) {
                text = text.substring(0, maxLength) + "…";
            }
            return new SpannedString(text);
        }

        AbstractDraftyFormatter.TreeNode result = content.format(new PreviewFormatter(container));
        if (result instanceof MeasuredTreeNode) {
            try {
                return ((MeasuredTreeNode) result).toSpanned(maxLength);
            } catch (LengthExceededException ex) {
                return new SpannableStringBuilder(ex.tail).append("…");
            }
        }

        return new SpannedString("");
    }

    @Override
    protected MeasuredTreeNode handleStrong(Object content) {
        return new MeasuredTreeNode(new StyleSpan(Typeface.BOLD), content);
    }

    @Override
    protected MeasuredTreeNode handleEmphasized(Object content) {
        return new MeasuredTreeNode(new StyleSpan(Typeface.ITALIC), content);
    }

    @Override
    protected MeasuredTreeNode handleDeleted(Object content) {
        return new MeasuredTreeNode(new StrikethroughSpan(), content);
    }

    @Override
    protected MeasuredTreeNode handleCode(Object content) {
        return new MeasuredTreeNode(new TypefaceSpan("monospace"), content);
    }

    @Override
    protected MeasuredTreeNode handleHidden(Object content) {
        return null;
    }

    @Override
    protected MeasuredTreeNode handleLineBreak() {
        return null;
    }

    @Override
    protected MeasuredTreeNode handleLink(Context ctx, Object content, Map<String, Object> data) {
        return new MeasuredTreeNode(new ForegroundColorSpan(ctx.getResources().getColor(R.color.colorAccent)), content);
    }

    @Override
    protected MeasuredTreeNode handleMention(Context ctx, Object content, Map<String, Object> data) {
        return new MeasuredTreeNode(content);
    }

    @Override
    protected MeasuredTreeNode handleHashtag(Context ctx, Object content, Map<String, Object> data) {
        return new MeasuredTreeNode(content);
    }

    private MeasuredTreeNode annotatedIcon(Context ctx, @DrawableRes int iconId, @StringRes int stringId) {
        MeasuredTreeNode node = null;
        Drawable icon = AppCompatResources.getDrawable(ctx, iconId);
        if (icon != null) {
            icon.setTint(ctx.getResources().getColor(R.color.colorDarkGray));
            icon.setBounds(0, 0, (int) mFontSize, (int) mFontSize);
            node = new MeasuredTreeNode();
            node.addNode(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), " ");
            node.addNode(" " + ctx.getResources().getString(stringId));
        }
        return node;
    }

    @Override
    protected MeasuredTreeNode handleImage(Context ctx, Object content, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.ic_image, R.string.picture);
    }

    @Override
    protected MeasuredTreeNode handleAttachment(Context ctx, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.ic_attach, R.string.attachment);
    }

    @Override
    protected MeasuredTreeNode handleButton(Context ctx, Map<String, Object> data, Object content) {
        MeasuredTreeNode node = new MeasuredTreeNode("[");
        node.addNode(new MeasuredTreeNode(content));
        node.addNode(new MeasuredTreeNode("]"));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleFormRow(Context ctx, Map<String, Object> data, Object content) {
        return new MeasuredTreeNode(content);
    }

    @Override
    protected MeasuredTreeNode handleForm(Context ctx, Map<String, Object> data, Object content) {
        MeasuredTreeNode node = annotatedIcon(ctx, R.drawable.ic_form, R.string.form);
        node.addNode(new MeasuredTreeNode(": "));
        node.addNode(new MeasuredTreeNode(content));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleUnknown(Context ctx, Object content, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.ic_unkn_type, R.string.unknown);
    }

    @Override
    protected MeasuredTreeNode handlePlain(final Object content) {
        return new MeasuredTreeNode(content);
    }

    static class MeasuredTreeNode extends StyledTreeNode {
        private static final String TAG = "MeasuredTreeNode";

        MeasuredTreeNode() {
            super();
        }

        MeasuredTreeNode(CharSequence content) {
            super(content);
        }

        MeasuredTreeNode(Object content) {
            super(content);
        }

        MeasuredTreeNode(CharacterStyle style, Object content) {
            super(style, content);
        }

        protected Spanned toSpanned(final int maxLength) {
            SpannableStringBuilder spanned = new SpannableStringBuilder();
            boolean exceeded = false;

            if (isPlain()) {
                CharSequence text = getText();
                if (text.length() > maxLength) {
                    text = text.subSequence(0, maxLength);
                    exceeded = true;
                }
                spanned.append(text);
            } else if (hasChildren()) {
                try {
                    for (AbstractDraftyFormatter.TreeNode child : getChildren()) {
                        if (child == null) {
                            Log.w(TAG, "NULL child. Should not happen!!!");
                        } else if (child instanceof MeasuredTreeNode) {
                            spanned.append(((MeasuredTreeNode) child).toSpanned(maxLength - spanned.length()));
                        } else {
                            Log.w(TAG, "Wrong child class: " + child.getClass().getSimpleName());
                        }
                    }
                } catch (LengthExceededException ex) {
                    exceeded = true;
                    spanned.append(ex.tail);
                }
            }

            if (spanned.length() > 0 && (cStyle != null || pStyle != null)) {
                spanned.setSpan(cStyle != null ? cStyle : pStyle,
                        0, spanned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (exceeded) {
                throw new LengthExceededException(spanned);
            }

            return spanned;
        }
    }

    static class LengthExceededException extends RuntimeException {
        private final Spanned tail;
        LengthExceededException(Spanned tail) {
            this.tail = tail;
        }
    }
}
