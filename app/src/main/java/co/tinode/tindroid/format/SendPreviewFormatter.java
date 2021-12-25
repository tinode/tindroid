package co.tinode.tindroid.format;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

// Formatter for displaying reply and forward previews before they are sent.
public class SendPreviewFormatter extends QuoteFormatter {
    private int mMentionCount = 0;
    private final boolean mIsForwarded;

    public SendPreviewFormatter(TextView container, boolean isForwarded) {
        super(container, container.getTextSize());
        mIsForwarded = isForwarded;
    }

    @Override
    protected SpannableStringBuilder handleMention(Context ctx, List<SpannableStringBuilder> content,
                                                   Map<String, Object> data) {
        if (mIsForwarded && mMentionCount == 0) {
            mMentionCount ++;
            // No shortening of the mention here.
            return SpanFormatter.handleMention_Impl(content, data);
        } else {
            return super.handleMention(ctx, content, data);
        }
    }

    @Override
    protected SpannableStringBuilder handleQuote(Context ctx, List<SpannableStringBuilder> content,
                                           Map<String, Object> data) {
        if (mIsForwarded) {
            // Treat forwarded quote like normal text.
            SpannableStringBuilder quote = new SpannableStringBuilder(join(content));
            return quote.append(" ");
        }

        return SpanFormatter.handleQuote_Impl(ctx, content);
    }
}

