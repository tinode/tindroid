package co.tinode.tindroid.format;

import android.content.Context;
import android.widget.TextView;

import java.util.Map;

public class ReplyFormatter extends SpanFormatter {
    public ReplyFormatter(TextView container, ClickListener clicker) {
        super(container, clicker);
    }

    @Override
    protected StyledTreeNode handleQuote(Context ctx, Map<String, Object> data, Object content) {
        return handleQuote_Impl(ctx, data, content);
    }
}

