package co.tinode.android.ui.format;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.StringRes;

import java.util.List;
import java.util.Map;

import co.tinode.android.ui.R;

// Drafty formatter for creating message previews in push notifications.
// Push notifications don't support ImageSpan or TypefaceSpan, consequently, using Unicode chars instead of icons.
public class FontFormatter extends PreviewFormatter {
    // Emoji characters from the stock font: Camera 📷 (image), Paperclip 📎 (attachment),
    // Memo 📝 (form), Question-Mark ❓ (unknown).
    // These characters are present in Android 5 and up.
    private static final String[] UNICODE_STRINGS = new String[]{"\uD83D\uDCF7", "\uD83D\uDCCE", "\uD83D\uDCDD", "\u2753"};

    // Index into character sets.
    private static final int IMAGE = 0;
    private static final int ATTACHMENT = 1;
    private static final int FORM = 2;
    private static final int UNKNOWN = 3;

    public FontFormatter(final Context context, float fontSize) {
        super(context, fontSize);
    }

    protected SpannableStringBuilder annotatedIcon(Context ctx, int charIndex, @StringRes int stringId) {
        SpannableStringBuilder node = new SpannableStringBuilder(UNICODE_STRINGS[charIndex]);
        return node.append(" ").append(ctx.getResources().getString(stringId));
    }

    @Override
    protected SpannableStringBuilder handleImage(Context ctx, List<SpannableStringBuilder> content,
                                                 Map<String, Object> data) {
        return annotatedIcon(ctx, IMAGE, R.string.picture);
    }

    @Override
    protected SpannableStringBuilder handleAttachment(Context ctx, Map<String, Object> data) {
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
    protected SpannableStringBuilder handleForm(Context ctx, List<SpannableStringBuilder> content,
                                                Map<String, Object> data) {
        SpannableStringBuilder node = annotatedIcon(ctx, FORM, R.string.form);
        return node.append(": ").append(join(content));
    }

    @Override
    protected SpannableStringBuilder handleUnknown(Context ctx, List<SpannableStringBuilder> content,
                                                   Map<String, Object> data) {
        return annotatedIcon(ctx, UNKNOWN, R.string.unknown);
    }
}
