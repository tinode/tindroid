package co.tinode.tindroid.format;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

// Formatter for displaying reply previews before they are sent.
public class SendReplyFormatter extends QuoteFormatter {
    public SendReplyFormatter(TextView container) {
        super(container, container.getTextSize());
    }

    @Override
    protected SpannableStringBuilder handleQuote(Context ctx, List<SpannableStringBuilder> content,
                                                 Map<String, Object> data) {
        // The entire preview is wrapped into a quote, so format content as if it's standalone (not quoted).
        return FullFormatter.handleQuote_Impl(ctx, content);
    }
}

