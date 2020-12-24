package co.tinode.tindroid.media;

import android.text.Spanned;
import android.widget.TextView;

import co.tinode.tinodesdk.model.Drafty;

// Drafty formatter for creating one-line message previews.
public class PreviewFormatter extends SpanFormatter {
    private final int mLength;

    PreviewFormatter(final TextView container, final int length) {
        super(container, null);
        mLength = length;
    }

    public static Spanned toSpanned(final TextView container, final Drafty content) {
        return toSpanned(container, content, null);
    }

    public static boolean hasClickableSpans(final Drafty content) {
        return false;
    }
}
