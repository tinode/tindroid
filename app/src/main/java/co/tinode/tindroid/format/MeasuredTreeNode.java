package co.tinode.tindroid.format;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;

// Node with a limited length.
public class MeasuredTreeNode extends StyledTreeNode {
    protected final int mMaxLength;

    MeasuredTreeNode(int maxLength) {
        super();
        mMaxLength = maxLength;
    }

    MeasuredTreeNode(CharSequence content, int maxLength) {
        super(content);
        mMaxLength = maxLength;
    }

    MeasuredTreeNode(Object content, int maxLength) {
        super(content);
        mMaxLength = maxLength;
    }

    MeasuredTreeNode(CharacterStyle style, Object content, int maxLength) {
        super(style, content);
        mMaxLength = maxLength;
    }

    MeasuredTreeNode(AbstractDraftyFormatter.TreeNode s, int maxLength) {
        super(s);
        mMaxLength = maxLength;
    }

    public Spanned toSpanned() {
        return toSpanned(mMaxLength);
    }

    protected Spanned toSpanned(final int maxLength) {
        SpannableStringBuilder spanned = new SpannableStringBuilder();
        boolean exceeded = false;

        if (maxLength == 0) {
            exceeded = true;
        } else if (isPlain()) {
            CharSequence text = getText();
            if (text.length() > maxLength) {
                text = text.subSequence(0, maxLength-1);
                exceeded = true;
            }
            spanned.append(text);
        } else if (hasChildren()) {
            try {
                for (AbstractDraftyFormatter.TreeNode child : getChildren()) {
                    if (child == null) {
                        continue;
                    }

                    int spaceLeft = maxLength - spanned.length();
                    if (child instanceof MeasuredTreeNode) {
                        spanned.append(((MeasuredTreeNode) child).toSpanned(spaceLeft));
                    } else {
                        spanned.append(new MeasuredTreeNode(child, spaceLeft).toSpanned());
                    }
                }
            } catch (LengthExceededException ex) {
                exceeded = true;
                spanned.append(ex.getTail());
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
