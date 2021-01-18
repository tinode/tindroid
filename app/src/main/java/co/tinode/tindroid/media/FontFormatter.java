package co.tinode.tindroid.media;

import android.content.Context;
import android.text.Spanned;

import java.util.Map;

import androidx.annotation.StringRes;
import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

// Drafty formatter for creating message previews in push notifications.
// Push notifications don't support ImageSpan or TypefaceSpan, consequently, using Unicode chars instead of icons.
public class FontFormatter extends PreviewFormatter {
    // Index into character sets.
    private static final int IMAGE = 0;
    private static final int ATTACHMENT = 1;
    private static final int FORM = 2;
    private static final int UNKNOWN = 3;

    // Emoji characters from the stock font: Camera, Paperclip, Memo, Question-Mark.
    // These characters are present in Android 5 and up.
    private static final String[] UNICODE_STRINGS = new String[]{"\uD83D\uDCF7", "\uD83D\uDCCE", "\uD83D\uDCDD", "\u2753"};

    FontFormatter(final Context context, float fontSize) {
        super(context, fontSize);
    }

    public static Spanned toSpanned(final Context context, float fontSize, final Drafty content, final int maxLength) {
        return toSpanned(new FontFormatter(context, fontSize), context, fontSize, content, maxLength);
    }

    private MeasuredTreeNode annotatedIcon(Context ctx, int charIndex, @StringRes int stringId) {
        MeasuredTreeNode node = new MeasuredTreeNode();
        node.addNode(new MeasuredTreeNode(UNICODE_STRINGS[charIndex]));
        node.addNode(new MeasuredTreeNode(" " + ctx.getResources().getString(stringId)));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleImage(Context ctx, Object content, Map<String, Object> data) {
        return annotatedIcon(ctx, IMAGE, R.string.picture);
    }

    @Override
    protected MeasuredTreeNode handleAttachment(Context ctx, Map<String, Object> data) {
        return annotatedIcon(ctx, ATTACHMENT, R.string.attachment);
    }

    @Override
    protected MeasuredTreeNode handleForm(Context ctx, Map<String, Object> data, Object content) {
        MeasuredTreeNode node = annotatedIcon(ctx, FORM, R.string.form);
        node.addNode(new MeasuredTreeNode(": "));
        node.addNode(new MeasuredTreeNode(content));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleUnknown(Context ctx, Object content, Map<String, Object> data) {
        return annotatedIcon(ctx, UNKNOWN, R.string.unknown);
    }
}
