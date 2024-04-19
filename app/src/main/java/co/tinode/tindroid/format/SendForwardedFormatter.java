package co.tinode.tindroid.format;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

import co.tinode.tindroid.R;

// Formatter for displaying forwarded previews before they are sent.
public class SendForwardedFormatter extends QuoteFormatter {
    public SendForwardedFormatter(TextView container) {
        super(container, container.getTextSize());
    }

    @Override
    protected SpannableStringBuilder handleQuote(Context ctx, List<SpannableStringBuilder> content,
                                           Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.ic_quote_ol, 0).append(" ");
    }
}

