package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Map;

import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

/**
 * Convert Drafty object into a Spanned object
 */

public class SpanFormatter {
    private static final String TAG = "SpanFormatter";

    public static Spanned toSpanned(final Context ctx, final Drafty content, final int viewport,
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
                            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
                            Bitmap bmp = null;
                            try {
                                byte[] bits = Base64.decode((String) data.get("val"), Base64.DEFAULT);
                                bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                                // Scale bitmap for display density.
                                float width = bmp.getWidth() * metrics.density;
                                float height = bmp.getHeight() * metrics.density;
                                // Make sure the scaled bitmap is no bigger than the viewport size;
                                float scaleX = (width < viewport ? width : viewport) / width;
                                float scaleY = (height < viewport * 0.75f ? height : viewport * 0.75f) / height;
                                float scale = scaleX < scaleY ? scaleX : scaleY;

                                bmp = Bitmap.createScaledBitmap(bmp, (int)(width * scale), (int)(height * scale), true);

                            } catch (NullPointerException | IllegalArgumentException | ClassCastException ignored) {
                                // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
                            }

                            if (bmp == null) {
                                span = new ImageSpan(ctx, R.drawable.ic_broken_image);
                            } else {
                                span = new ImageSpan(ctx, bmp);
                            }
                            final boolean valid = bmp != null;

                            // Insert inline image
                            text.setSpan(span, style.getOffset(), style.getOffset() + style.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            span = new ClickableSpan() {
                                @Override
                                public void onClick(View widget) {
                                    clicker.onClick("IM", valid ? data : null);
                                }
                            };
                            // Make image clickable
                            text.setSpan(span, style.getOffset(), style.getOffset() + style.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            span = null;
                        }
                        break;
                    case "EX":
                        if (data != null) {
                            if (text.length() > 0) {
                                offset = text.length() + 1;
                                text.append("\n ");
                            } else {
                                offset = 0;
                                text.append(" ");
                            }
                            // Insert document icon
                            span = new ImageSpan(ctx, R.drawable.ic_insert_drive_file, ImageSpan.ALIGN_BOTTOM);
                            Rect bounds = ((ImageSpan) span).getDrawable().getBounds();
                            text.setSpan(span, offset, offset + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            text.setSpan(new SubscriptSpan(), offset, offset + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                            // Insert document's file name
                            String fname = null;
                            try {
                                fname = (String) data.get("name");
                            } catch (NullPointerException | ClassCastException ignored) {
                            }
                            if (TextUtils.isEmpty(fname)) {
                                fname = ctx.getResources().getString(R.string.default_attachment_name);
                            } else if (fname.length() > 32) {
                                fname = fname.substring(0, 16) + "..." + fname.substring(fname.length() - 16);
                            }
                            SpannableStringBuilder substr = new SpannableStringBuilder(fname);
                            substr.setSpan(new TypefaceSpan("monospace"), 0, substr.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            text.append(substr);
                            text.append("\n");

                            // Insert clickable [_ save] line
                            // Build string
                            substr = new SpannableStringBuilder(" ")
                                    .append(ctx.getResources().getString(R.string.download_attachment));
                            // Insert 'download file' icon
                            substr.setSpan(new ImageSpan(ctx, R.drawable.ic_file_download, ImageSpan.ALIGN_BOTTOM),
                                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            // Make line clickable
                            span = new ClickableSpan() {
                                @Override
                                public void onClick(View widget) {
                                    clicker.onClick("EX", data);
                                }
                            };
                            substr.setSpan(span, 1, substr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            // Move line right to make it appear under the file name.
                            substr.setSpan(new LeadingMarginSpan.Standard(bounds.width()), 0, substr.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                            text.append(substr);
                            span = null;
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
