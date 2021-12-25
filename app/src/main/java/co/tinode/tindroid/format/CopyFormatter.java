package co.tinode.tindroid.format;

import android.content.Context;
import android.text.SpannableStringBuilder;

import java.util.List;
import java.util.Map;

// Formatter used for copying text to clipboard.
public class CopyFormatter extends FontFormatter {
    public CopyFormatter(final Context context) {
        super(context, 0f);
    }

    // Button as '[ content ]'.
    @Override
    protected SpannableStringBuilder handleButton(Context ctx, List<SpannableStringBuilder> content,
                                                  Map<String, Object> data) {
        SpannableStringBuilder node = new SpannableStringBuilder();
        // Non-breaking space as padding in front of the button.
        node.append("\u00A0[\u00A0");
        node.append(join(content));
        node.append("\u00A0]");
        return node;
    }
}
