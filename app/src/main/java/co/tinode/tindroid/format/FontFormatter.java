package co.tinode.tindroid.format;

import android.content.Context;

import java.util.Map;

import androidx.annotation.StringRes;
import co.tinode.tindroid.R;

// Drafty formatter for creating message previews in push notifications.
// Push notifications don't support ImageSpan or TypefaceSpan, consequently, using Unicode chars instead of icons.
public class FontFormatter extends PreviewFormatter {
    // Emoji characters from the stock font: Camera 📷, Paperclip 📎, Memo 📝, Question-Mark ❓.
    // These characters are present in Android 5 and up.
    private static final String[] UNICODE_STRINGS = new String[]{"\uD83D\uDCF7", "\uD83D\uDCCE", "\uD83D\uDCDD", "\u2753"};

    // Index into character sets.
    private static final int IMAGE = 0;
    private static final int ATTACHMENT = 1;
    private static final int FORM = 2;
    private static final int UNKNOWN = 3;

    public FontFormatter(final Context context, float fontSize, int maxLength) {
        super(context, fontSize, maxLength);
    }

    private MeasuredTreeNode annotatedIcon(Context ctx, int charIndex, @StringRes int stringId) {
        MeasuredTreeNode node = new MeasuredTreeNode(mMaxLength);
        node.addNode(new MeasuredTreeNode(UNICODE_STRINGS[charIndex], mMaxLength));
        node.addNode(new MeasuredTreeNode(" " + ctx.getResources().getString(stringId), mMaxLength));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleImage(Context ctx, Object content, Map<String, Object> data) {
        return annotatedIcon(ctx, IMAGE, R.string.picture);
    }

    @Override
    protected MeasuredTreeNode handleAttachment(Context ctx, Map<String, Object> data) {
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
        return annotatedIcon(ctx, ATTACHMENT, R.string.attachment);
    }

    @Override
    protected MeasuredTreeNode handleForm(Context ctx, Map<String, Object> data, Object content) {
        MeasuredTreeNode node = annotatedIcon(ctx, FORM, R.string.form);
        node.addNode(new MeasuredTreeNode(": ", mMaxLength));
        node.addNode(new MeasuredTreeNode(content, mMaxLength));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleUnknown(Context ctx, Map<String, Object> data, Object content) {
        return annotatedIcon(ctx, UNKNOWN, R.string.unknown);
    }
}
