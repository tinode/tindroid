package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import java.util.Map;

import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

/**
 * Convert Drafty object into a Spanned object
 */

public class SpanFormatter {
    private static final String TAG = "SpanFormatter";

    public static Spanned toSpanned(final Context ctx, final Drafty content, final int viewportWidth,
                                    final ClickListener clicker) {
        if (content == null) {
            // Malicious user may send a message with null content.
            return new SpannedString("");
        }

        SpannableStringBuilder text = new SpannableStringBuilder(content.toString());
        Drafty.Style[] fmt = content.getStyles();

        if (fmt != null) {
            Drafty.Entity entity;

            for (Drafty.Style style : fmt) {
                CharacterStyle span = null;
                int offset = -1, length = -1;
                String tp = style.getType();
                entity = content.getEntity(style);

                final Map<String,Object> data;
                if (entity != null) {
                    tp = entity.getType();
                    data = entity.getData();
                } else {
                    data = null;
                }

                if (tp == null) {
                    Log.d(TAG, "Null type in " + style.toString());
                    continue;
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
                        String url = data != null ? (String) data.get("url") : null;
                        span = url != null ? new URLSpan(url) {
                            @Override
                            public void onClick(View widget) {
                                if (clicker != null) {
                                    clicker.onClick("LN", data);
                                }
                            }
                        } : null;
                        break;
                    case "MN": span = null; break;
                    case "HT": span = null; break;
                    case "IM":
                        if (data != null) {
                            Bitmap bmp = null;
                            try {
                                byte[] bits = Base64.decode((String) data.get("val"), Base64.DEFAULT);
                                bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                            } catch (NullPointerException | IllegalArgumentException | ClassCastException ignored) {
                                // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
                            }

                            if (bmp == null) {
                                span = new ImageSpan(ctx, R.drawable.ic_broken_image);
                            } else {
                                span = new ImageSpan(ctx, bmp);
                            }
                        }
                        break;
                    case "EX":
                        if (data != null) {
                            span = new ImageSpan(ctx, R.drawable.ic_insert_drive_file, ImageSpan.ALIGN_BOTTOM);
                            length = 1;
                            if (text.length() > 0) {
                                offset = text.length() + 1;
                                text.append("\n ");
                            } else {
                                offset = 0;
                                text.append(" ");
                            }
                            try {
                                text.append((String) data.get("name"));
                            } catch (NullPointerException | ClassCastException ignored) {
                            }
                        }
                        break;
                    default:
                        // TODO(gene): report unknown style to user
                        break;
                }

                if (span != null) {
                    offset = offset < 0 ? style.getOffset() : offset;
                    length = length < 0 ? style.length() : length;
                    text.setSpan(span, offset, offset + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        return text;
    }

    public static boolean hasClickableSpans(final Drafty content) {
        if (content != null) {
            Drafty.Entity[] entities = content.getEntities();
            if (entities == null) {
                return false;
            }

            for (Drafty.Entity ent : entities) {
                if (ent.tp == null) {
                    continue;
                }
                switch (ent.tp) {
                    case "LN":
                    case "MN":
                    case "HT":
                    case "IM":
                    case "EX":
                        return true;
                    default:
                }
            }
        }
        return false;
    }

    public interface ClickListener {
        void onClick(String type, Map<String,Object> data);
    }
}
