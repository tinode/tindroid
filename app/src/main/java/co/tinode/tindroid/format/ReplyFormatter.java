package co.tinode.tindroid.format;

import android.content.Context;
import android.widget.TextView;

import java.util.Map;

import co.tinode.tindroid.UiUtils;

// Formatter for displaying rely and forward previews before they are sent.
public class ReplyFormatter extends QuoteFormatter {
    private int mMentionCount = 0;
    private final boolean mIsForwarded;

    public ReplyFormatter(TextView container, boolean isForwarded) {
        super(container.getContext(), container.getTextSize(), UiUtils.QUOTED_REPLY_LENGTH);
        mIsForwarded = isForwarded;
    }

    @Override
    protected MeasuredTreeNode handleMention(Context ctx, Object content, Map<String, Object> data) {
        if (mIsForwarded && mMentionCount == 0) {
            mMentionCount ++;
            // No shortening of the mention here.
            StyledTreeNode node = SpanFormatter.handleMention_Impl(content, data);
            return new MeasuredTreeNode(node, mMaxLength);
        } else {
            return super.handleMention(ctx, content, data);
        }
    }

    @Override
    protected MeasuredTreeNode handleQuote(Context ctx, Map<String, Object> data, Object content) {
        if (mIsForwarded) {
            // Treat forwarded quote like normal text.
            MeasuredTreeNode quote = new MeasuredTreeNode(content, UiUtils.QUOTED_REPLY_LENGTH);
            quote.addNode(new MeasuredTreeNode(" ", UiUtils.QUOTED_REPLY_LENGTH));
            return quote;
        }
        return new MeasuredTreeNode(SpanFormatter.handleQuote_Impl(ctx, content), UiUtils.QUOTED_REPLY_LENGTH);
    }
}

