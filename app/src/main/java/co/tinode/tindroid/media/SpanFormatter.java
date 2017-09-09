package co.tinode.tindroid.media;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.CharacterStyle;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Log;

import java.util.Map;


import co.tinode.tinodesdk.model.Drafty;

/**
 * Convert Drafty object into a Spanned object
 */

public class SpanFormatter {

    public static Spanned toSpanned(Drafty content) {
        if (content == null) {
            // Malicious user may send a message with null content.
            return new SpannedString("");
        }

        SpannableStringBuilder text = new SpannableStringBuilder(content.toString());
        Drafty.Style[] fmt = content.getStyles();

        if (fmt != null) {
            Drafty.Entity entity;
            Map<String,String> data;

            for (Drafty.Style style : fmt) {
                CharacterStyle span = null;
                String tp = style.getType();
                entity = content.getEntity(style);
                if (entity != null) {
                    tp = entity.getType();
                    data = entity.getData();
                } else {
                    data = null;
                }
                switch (tp) {
                    case "ST": span = new StyleSpan(Typeface.BOLD); break;
                    case "EM": span = new StyleSpan(Typeface.ITALIC); break;
                    case "DL": span = new StrikethroughSpan(); break;
                    case "CO": span = new TypefaceSpan("monospace"); break;
                    case "BR":
                        text.replace(style.getOffset(), style.getOffset() + style.length(), "\n");
                        span = null;
                        break;
                    case "LN":
                        String url = data != null ? data.get("url") : null;
                        span = url != null ? new URLSpan(url) : null;
                        //Log.d("SpanFormatter", "Doing link span '" + url + "', "
                        //        + span + ", data=" + data + ", entity=" + entity);
                        break;
                    case "MN": span = null; break;
                    case "HT": span = null; break;
                    default:
                        // TODO(gene): report unknown style to user
                        break;
                }

                if (span != null) {
                    text.setSpan(span, style.getOffset(), style.getOffset() + style.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        return text;
    }
}
