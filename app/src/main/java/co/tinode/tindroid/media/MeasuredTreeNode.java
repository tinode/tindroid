package co.tinode.tindroid.media;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;

public class MeasuredTreeNode extends StyledTreeNode {
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

    MeasuredTreeNode(ParagraphStyle style, Object content) {
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
                        continue;
                    }

                    if (child instanceof MeasuredTreeNode) {
                        spanned.append(((MeasuredTreeNode) child).toSpanned(maxLength - spanned.length()));
                    }
                }
            } catch (PreviewFormatter.LengthExceededException ex) {
                exceeded = true;
                spanned.append(ex.tail);
            }
        }

        if (spanned.length() > 0 && (cStyle != null || pStyle != null)) {
            spanned.setSpan(cStyle != null ? cStyle : pStyle,
                    0, spanned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (exceeded) {
            throw new PreviewFormatter.LengthExceededException(spanned);
        }

        return spanned;
    }
}
