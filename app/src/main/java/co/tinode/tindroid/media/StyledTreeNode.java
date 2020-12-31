package co.tinode.tindroid.media;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.util.Log;

class StyledTreeNode extends AbstractDraftyFormatter.TreeNode {
    private static final String TAG = "StyledTreeNode";

    protected CharacterStyle cStyle;
    protected ParagraphStyle pStyle;

    StyledTreeNode() {
        super();
        cStyle = null;
        pStyle = null;
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

    protected Spanned toSpanned() {
        SpannableStringBuilder spanned = new SpannableStringBuilder();
        if (isPlain()) {
            spanned.append(getText());
        } else if (hasChildren()) {
            for (AbstractDraftyFormatter.TreeNode child : getChildren()) {
                if (child == null) {
                    Log.w(TAG, "NULL child. Should not happen!!!");
                } else if (child instanceof StyledTreeNode){
                    spanned.append(((StyledTreeNode) child).toSpanned());
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
