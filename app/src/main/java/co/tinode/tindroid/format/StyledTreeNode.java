package co.tinode.tindroid.format;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

class StyledTreeNode extends AbstractDraftyFormatter.TreeNode {
    private static final String TAG = "StyledTreeNode";

    protected CharacterStyle cStyle;
    protected ParagraphStyle pStyle;

    StyledTreeNode() {
        super();
        cStyle = null;
        pStyle = null;
    }

    // Copy constructor.
    StyledTreeNode(AbstractDraftyFormatter.TreeNode s) {
        super(s);
        if (s instanceof StyledTreeNode) {
            cStyle = ((StyledTreeNode) s).cStyle;
            pStyle = ((StyledTreeNode) s).pStyle;
        }
    }

    StyledTreeNode(CharSequence content) {
        super(content);
    }

    StyledTreeNode(Object content) {
        super(content);
    }

    StyledTreeNode(CharacterStyle style, Object content) {
        super(content);
        this.cStyle = style;
    }

    StyledTreeNode(ParagraphStyle style, Object content) {
        super(content);
        this.pStyle = style;
    }

    public Spanned toSpanned() {
        SpannableStringBuilder spanned = new SpannableStringBuilder();
        if (isPlain()) {
            spanned.append(getText());
        } else if (hasChildren()) {
            for (AbstractDraftyFormatter.TreeNode child : getChildren()) {
                if (child == null) {
                    Log.e(TAG, "NULL child. Should not happen!!!");
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

    @NotNull
    public String toString() {
        CharSequence txt = text == null ? "null" : text == "\n" ? "<br>" : text;
        return "{" + (cStyle != null ? cStyle.getClass().getSimpleName() :
                pStyle != null ? pStyle.getClass().getSimpleName() : "PLAIN") +
                ": '" + txt + "', " + (children != null ? children.toString() : "NULL") + "}";
    }
}
