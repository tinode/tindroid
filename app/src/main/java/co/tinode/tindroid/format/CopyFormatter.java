package co.tinode.tindroid.format;

import android.content.Context;

import java.util.Map;

// Formatter used for copying text to clipboard.
public class CopyFormatter extends FontFormatter {
    private static final int MAX_LENGTH = Integer.MAX_VALUE - 1024;

    public CopyFormatter(final Context context) {
        super(context, 0f, MAX_LENGTH);
    }

    // Button as '[ content ]'.
    @Override
    protected MeasuredTreeNode handleButton(Context ctx, Map<String, Object> data, Object content) {
        MeasuredTreeNode node = new MeasuredTreeNode(mMaxLength);
        // Non-breaking space as padding in front of the button.
        node.addNode(new MeasuredTreeNode("\u00A0[\u00A0", mMaxLength));
        node.addNode(new MeasuredTreeNode(content, mMaxLength));
        node.addNode(new MeasuredTreeNode("\u00A0]", mMaxLength));
        return node;
    }
}
