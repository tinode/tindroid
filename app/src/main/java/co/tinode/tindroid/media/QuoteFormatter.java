package co.tinode.tindroid.media;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;

import java.util.Map;

import co.tinode.tindroid.R;

public class QuoteFormatter extends PreviewFormatter {
    private static TypedArray sColorsDark;
    private static int sDefaultColor;

    public QuoteFormatter(final Context context, float fontSize, final SpanFormatter.ClickListener clicker) {
        super(context, fontSize);

        Resources res = context.getResources();
        if (sColorsDark == null) {
            sColorsDark = res.obtainTypedArray(R.array.letter_tile_colors_dark);
            sDefaultColor = res.getColor(R.color.grey);
        }
    }

    @Override
    protected MeasuredTreeNode handleLineBreak() {
        return new MeasuredTreeNode("\n");
    }

    @Override
    protected MeasuredTreeNode handleMention(Context ctx, Object content, Map<String, Object> data) {
        int color = sDefaultColor;
        try {
            color = colorMention((String) data.get("val"));
        } catch(ClassCastException ignored){}

        return new MeasuredTreeNode(new ForegroundColorSpan(color), content);
    }

    @Override
    protected MeasuredTreeNode handleImage(Context ctx, Object content, Map<String, Object> data) {
        /*
                            attr = inlineImageAttr.call(this, attr, data);
                    values = [React.createElement('img', attr, null), ' ', attr.alt];
                    el = React.Fragment;
                    // Fragment attributes.
                    attr = {key: key};
                    break;
         */
        return null;
    }

    @Override
    protected MeasuredTreeNode handleQuote(Context ctx, Map<String, Object> data, Object content) {
        /*
          padding: 0.15rem 0.5rem 0.15rem 0.5rem;
          border-left: 0.25rem solid #039be5;
        */
        // TODO: make clickable.
        Resources res = ctx.getResources();
        QuotedSpan style = new QuotedSpan(res.getColor(R.color.colorReplyBubbleOther),
                0, res.getColor(R.color.colorQuoteStripe), 2, 6);
        return new MeasuredTreeNode(style, content);
    }

    private int colorMention(String uid) {
        return TextUtils.isEmpty(uid) ?
                sDefaultColor :
                sColorsDark.getColor(Math.abs(uid.hashCode()) % sColorsDark.length(), sDefaultColor);
    }
}
