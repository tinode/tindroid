package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;

import java.util.Map;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

// Drafty formatter for creating one-line message previews.
public class PreviewFormatter extends AbstractDraftyFormatter<MeasuredTreeNode> {
    private final float mFontSize;
    private final int mMaxLength;

    public PreviewFormatter(final Context context, float fontSize, int maxLength) {
        super(context);

        mFontSize = fontSize;
        mMaxLength = maxLength;
    }

    public Spanned toSpanned(final Drafty content) {
        if (content == null) {
            return new SpannedString("");
        }
        if (content.isPlain()) {
            String text = content.toString();
            if (text.length() > mMaxLength) {
                text = text.substring(0, mMaxLength) + "…";
            }
            return new SpannedString(text);
        }

        AbstractDraftyFormatter.TreeNode result = content.format(this, null);
        if (result instanceof MeasuredTreeNode) {
            try {
                return ((MeasuredTreeNode) result).toSpanned(mMaxLength);
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
        return new MeasuredTreeNode(" ");
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
            icon.setBounds(0, 0, (int) (mFontSize * 1.3), (int) (mFontSize * 1.3));
            node = new MeasuredTreeNode();
            node.addNode(new MeasuredTreeNode(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), " "));
            node.addNode(new MeasuredTreeNode(" " + ctx.getResources().getString(stringId)));
        }
        return node;
    }

    @Override
    protected MeasuredTreeNode handleImage(Context ctx, Object content, Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        try {
            if ("application/json".equals(data.get("mime"))) {
                // Skip JSON attachments. They are not meant to be user-visible.
                return null;
            }
        } catch (ClassCastException ignored) {
        }
        return annotatedIcon(ctx, R.drawable.ic_image_ol, R.string.picture);
    }

    @Override
    protected MeasuredTreeNode handleAttachment(Context ctx, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.ic_attach_ol, R.string.attachment);
    }

    @Override
    protected MeasuredTreeNode handleButton(Context ctx, Map<String, Object> data, Object content) {
        // Size of a DIP pixel.
        float dipSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f,
                ctx.getResources().getDisplayMetrics());
        // Make button font slightly smaller.
        MeasuredTreeNode node = new MeasuredTreeNode(new RelativeSizeSpan(0.8f), null);
        // Change background color and draw a box around text.
        node.addNode(new MeasuredTreeNode(new LabelSpan(ctx, mFontSize, dipSize), content));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleFormRow(Context ctx, Map<String, Object> data, Object content) {
        MeasuredTreeNode node = new MeasuredTreeNode();
        node.addNode(new MeasuredTreeNode(" "));
        node.addNode(new MeasuredTreeNode(content));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleForm(Context ctx, Map<String, Object> data, Object content) {
        MeasuredTreeNode node = annotatedIcon(ctx, R.drawable.ic_form_ol, R.string.form);
        node.addNode(new MeasuredTreeNode(": "));
        node.addNode(new MeasuredTreeNode(content));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleQuote(Context ctx, Map<String, Object> data, Object content) {
        // Not showing quoted content in preview.
        return null;
    }

    @Override
    protected MeasuredTreeNode handleUnknown(Context ctx, Map<String, Object> data, Object content) {
        return annotatedIcon(ctx, R.drawable.ic_unkn_type_ol, R.string.unknown);
    }

    @Override
    protected MeasuredTreeNode handlePlain(final Object content) {
        return new MeasuredTreeNode(content);
    }

    protected static class LengthExceededException extends RuntimeException {
        private final Spanned tail;

        LengthExceededException(Spanned tail) {
            this.tail = tail;
        }
    }
}
