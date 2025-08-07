package co.tinode.tindroid.format;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tindroid.UtilsString;
import co.tinode.tindroid.widgets.TextDrawable;
import co.tinode.tindroid.widgets.WaveDrawable;

/**
 * Convert Drafty object into a Spanned object with full support for all features.
 */
public class FullFormatter extends AbstractDraftyFormatter<SpannableStringBuilder> {
    private static final String TAG = "FullFormatter";

    private static final float FORM_LINE_SPACING = 1.2f;
    // Additional horizontal padding otherwise images sometimes fail to render.
    private static final int IMAGE_H_PADDING = 8;
    // Size of Download and Error icons in DP.
    private static final int ICON_SIZE_DP = 16;

    // Formatting parameters of the quoted text;
    private static final int CORNER_RADIUS_DP = 4;
    private static final int QUOTE_STRIPE_WIDTH_DP = 4;
    private static final int STRIPE_GAP_DP = 8;

    private static final int MAX_FILE_LENGTH = 18;

    private static final int MIN_AUDIO_PREVIEW_LENGTH = 16;

    private static TypedArray sColorsDark;
    private static int sDefaultColor;

    private final TextView mContainer;
    // Maximum width of the container TextView. Max height is maxWidth * 0.75.
    private final int mViewport;
    private final float mFontSize;
    private final ClickListener mClicker;
    private QuoteFormatter mQuoteFormatter;

    public FullFormatter(final TextView container, final ClickListener clicker) {
        super(container.getContext());

        mContainer = container;
        mViewport = container.getMaxWidth() - container.getPaddingLeft() - container.getPaddingRight();
        mFontSize = container.getTextSize();
        mClicker = clicker;
        mQuoteFormatter = null;

        Resources res = container.getResources();
        if (sColorsDark == null) {
            sColorsDark = res.obtainTypedArray(R.array.letter_tile_colors_dark);
            sDefaultColor = res.getColor(R.color.grey, null);
        }
    }

    @Override
    public SpannableStringBuilder apply(final String tp, final Map<String, Object> data,
                                        final List<SpannableStringBuilder> content, Stack<String> context) {
        if (context != null && context.contains("QQ") && mQuoteFormatter != null) {
            return mQuoteFormatter.apply(tp, data, content, context);
        }

        return super.apply(tp, data, content, context);
    }

    @Override
    public SpannableStringBuilder wrapText(CharSequence text) {
        return text != null ? new SpannableStringBuilder(text) : null;
    }

    public void setQuoteFormatter(QuoteFormatter quoteFormatter) {
        mQuoteFormatter = quoteFormatter;
    }

    @Override
    protected SpannableStringBuilder handleStrong(List<SpannableStringBuilder> content) {
        return assignStyle(new StyleSpan(Typeface.BOLD), content);
    }

    @Override
    protected SpannableStringBuilder handleEmphasized(List<SpannableStringBuilder> content) {
        return assignStyle(new StyleSpan(Typeface.ITALIC), content);
    }

    @Override
    protected SpannableStringBuilder handleDeleted(List<SpannableStringBuilder> content) {
        return assignStyle(new StrikethroughSpan(), content);
    }

    @Override
    protected SpannableStringBuilder handleCode(List<SpannableStringBuilder> content) {
        return assignStyle(new TypefaceSpan("monospace"), content);
    }

    @Override
    protected SpannableStringBuilder handleHidden(List<SpannableStringBuilder> content) {
        return null;
    }

    @Override
    protected SpannableStringBuilder handleLineBreak() {
        return new SpannableStringBuilder("\n");
    }

    @Override
    protected SpannableStringBuilder handleLink(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        try {
            // We don't need to specify an URL for URLSpan
            // as it's not going to be used.
            SpannableStringBuilder span = new SpannableStringBuilder(join(content));
            span.setSpan(new URLSpan("") {
                @Override
                public void onClick(View widget) {
                    if (mClicker != null) {
                        mClicker.onClick("LN", data, null);
                    }
                }
            }, 0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            return span;
        } catch (ClassCastException | NullPointerException ignored) {
        }
        return null;
    }

    static SpannableStringBuilder handleMention_Impl(List<SpannableStringBuilder> content, Map<String, Object> data) {
        int color = sDefaultColor;
        if (data != null) {
            color = colorMention(getStringVal("val", data, ""));
        }

        return assignStyle(new ForegroundColorSpan(color), content);
    }

    @Override
    protected SpannableStringBuilder handleMention(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        return handleMention_Impl(content, data);
    }

    private static int colorMention(String uid) {
        return TextUtils.isEmpty(uid) ?
                sDefaultColor :
                sColorsDark.getColor(Math.abs(uid.hashCode()) % sColorsDark.length(), sDefaultColor);
    }

    @Override
    protected SpannableStringBuilder handleHashtag(Context ctx, List<SpannableStringBuilder> content,
                                                   Map<String, Object> data) {
        return null;
    }

    @Override
    protected SpannableStringBuilder handleAudio(final Context ctx, List<SpannableStringBuilder> content,
                                                 final Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        mContainer.setHighlightColor(Color.TRANSPARENT);
        Resources res = ctx.getResources();
        final WaveDrawable waveDrawable;
        SpannableStringBuilder result = new SpannableStringBuilder();

        // Initialize Play icon
        StateListDrawable play = (StateListDrawable) AppCompatResources.getDrawable(ctx, R.drawable.ic_play_pause);
        //noinspection ConstantConditions
        play.setBounds(0, 0, play.getIntrinsicWidth() * 3 / 2, play.getIntrinsicHeight() * 3 / 2);
        play.setTint(res.getColor(R.color.colorAccent, null));
        ImageSpan span = new ImageSpan(play, ImageSpan.ALIGN_BOTTOM);
        final Rect bounds = span.getDrawable().getBounds();
        result.append(" ", span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        result.append(" ");

        int duration = getIntVal("duration", data);

        // Initialize and insert waveform drawable.
        Object val = data.get("preview");
        byte[] preview = null;
        if (val instanceof String) {
            try {
                preview = Base64.decode((String) val, Base64.DEFAULT);
            } catch (IllegalArgumentException ignored) {}
        }

        if (preview != null && preview.length > MIN_AUDIO_PREVIEW_LENGTH) {
            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            float width = mViewport * 0.8f - bounds.width() - 4 * IMAGE_H_PADDING * metrics.density;
            waveDrawable = new WaveDrawable(res);
            waveDrawable.setBounds(new Rect(0, 0, (int) width, (int) (bounds.height() * 0.9f)));
            waveDrawable.setCallback(mContainer);
            if (duration > 0) {
                waveDrawable.setDuration(duration);
            }
            waveDrawable.put(preview);
        } else {
            waveDrawable = null;
            result.append(res.getText(R.string.unavailable));
        }

        final AudioControlCallback aControl = new AudioControlCallback() {
            @Override
            public void reset() {
                if (waveDrawable != null) {
                    waveDrawable.reset();
                }
            }

            @Override
            public void pause() {
                play.setState(new int[]{});
                mContainer.postInvalidate();
                if (waveDrawable != null) {
                    waveDrawable.stop();
                }
            }

            @Override
            public void resume() {
                if (waveDrawable != null) {
                    waveDrawable.start();
                }
            }
        };

        if (waveDrawable != null) {
            ImageSpan wave = new ImageSpan(waveDrawable, ImageSpan.ALIGN_BASELINE);
            result.append(new SpannableStringBuilder().append(" ", wave, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE),
                    new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            int clickAt = -1;
                            Object tag = widget.getTag(R.id.click_coordinates);
                            if (tag instanceof Point) {
                                clickAt = ((Point) tag).x - widget.getPaddingLeft();
                            }
                            widget.setTag(null);

                            TextView tv = (TextView) widget;
                            Layout tvl = tv.getLayout();
                            Spanned fullText = (Spanned) tv.getText();
                            float startX = tvl.getPrimaryHorizontal(fullText.getSpanStart(wave));
                            float endX = tvl.getPrimaryHorizontal(fullText.getSpanEnd(wave));
                            float seekPosition = (clickAt - startX) / (endX - startX);
                            waveDrawable.seekTo(seekPosition);
                            mContainer.postInvalidate();
                            if (mClicker != null) {
                                mClicker.onClick("AU", data, new AudioClickAction(seekPosition, aControl));
                            }
                        }
                        @Override
                        public void updateDrawState(@NonNull TextPaint ds) {}
                    }, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            waveDrawable.seekTo(0);
            waveDrawable.setOnCompletionListener(() -> {
                play.setState(new int[]{});
                mContainer.postInvalidate();
            });
        }

        if (mClicker != null) {
            // Make image clickable by wrapping ImageSpan into a ClickableSpan.
            result.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    int[] state = play.getState();
                    AudioClickAction.Action action;
                    if (state.length > 0 && state[0] == android.R.attr.state_checked) {
                        play.setState(new int[]{});
                        action = AudioClickAction.Action.PAUSE;
                        if (waveDrawable != null) {
                            waveDrawable.stop();
                        }
                    } else {
                        play.setState(new int[]{android.R.attr.state_checked});
                        action = AudioClickAction.Action.PLAY;
                        if (waveDrawable != null) {
                            waveDrawable.start();
                        }
                    }
                    mClicker.onClick("AU", data, new AudioClickAction(action, aControl));
                    mContainer.postInvalidate();
                }
                // Ignored.
                @Override public void updateDrawState(@NonNull TextPaint ds) {}
            }, 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
        }

        // Insert duration on the next line as small text.
        result.append("\n");

        String strDur = duration > 0 ? " " + millisToTime(duration, true) : null;
        if (TextUtils.isEmpty(strDur)) {
            strDur = " -:--";
        }

        SpannableStringBuilder small = new SpannableStringBuilder()
                .append(strDur, new RelativeSizeSpan(0.8f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Add space on the left to make time appear under the waveform.
        result.append(small, new LeadingMarginSpan.Standard(bounds.width()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return result;
    }

    private static class ImageDim {
        float scale;
        int width;
        int height;
        int scaledWidth;
        int scaledHeight;

        public @NonNull String toString() {
            return width + "x" + height + "/" + scaledWidth + "x" + scaledHeight + "@" + scale;
        }
    }

    private CharacterStyle createImageSpan(final Context ctx, final Object val, final String ref,
                                           final ImageDim dim, final float density,
                                           @Nullable final Drawable overlay,
                                           @DrawableRes int id_placeholder, @DrawableRes int id_error) {

        CharacterStyle span = null;
        Bitmap bmpPreview = null;

        // Bitmap dimensions specified by the sender converted to viewport size in display pixels.
        dim.scale = scaleBitmap(dim.width, dim.height, mViewport, density);
        // Bitmap dimensions specified by the sender converted to viewport size in display pixels.
        dim.scaledWidth = 0;
        dim.scaledHeight = 0;
        if (dim.scale > 0) {
            dim.scaledWidth = (int) (dim.width * dim.scale * density);
            dim.scaledHeight = (int) (dim.height * dim.scale * density);
        }

        // Inline image.
        if (val != null) {
            try {
                // False if inline image is only a preview: try to use out of band image (default).
                boolean usePreviewAsMainImage = false;
                // If the message is not yet sent, the bits could be raw byte[] as opposed to
                // base64-encoded.
                byte[] bits = UiUtils.decodeByteArray(val);
                if (bits != null) {
                    bmpPreview = BitmapFactory.decodeByteArray(bits, 0, bits.length);
                }
                if (bmpPreview != null) {
                    // Check if the inline bitmap is big enough to be used as primary image.
                    int previewWidth = bmpPreview.getWidth();
                    int previewHeight = bmpPreview.getHeight();
                    if (dim.scale == 0) {
                        // If dimensions are not specified in the attachment metadata, try to use bitmap dimensions.
                        dim.scale = scaleBitmap(previewWidth, previewHeight, mViewport, density);
                        if (dim.scale != 0) {
                            // Because sender-provided dimensions are unknown or invalid we have to use
                            // this inline image as the primary one (out of band image is ignored).
                            usePreviewAsMainImage = true;
                            dim.scaledWidth = (int) (previewWidth * dim.scale * density);
                            dim.scaledHeight = (int) (previewHeight * dim.scale * density);
                        }
                    }

                    Bitmap oldBmp = bmpPreview;
                    if (dim.scale == 0) {
                        // Can't scale the image. There must be something wrong with it.
                        bmpPreview = null;
                    } else {
                        bmpPreview = Bitmap.createScaledBitmap(bmpPreview, dim.scaledWidth, dim.scaledHeight, true);
                        // Check if the image is big enough to use as the primary one (ignoring possible full-size
                        // out-of-band image). If it's not already suitable for preview don't bother.
                        usePreviewAsMainImage = usePreviewAsMainImage ||
                                (previewWidth / density > dim.scaledWidth * 0.35f);
                    }
                    oldBmp.recycle();
                } else {
                    Log.w(TAG, "Failed to decode preview bitmap");
                }

                if (bmpPreview != null && (usePreviewAsMainImage || ref == null)) {
                    Drawable drawable = new BitmapDrawable(ctx.getResources(), bmpPreview);
                    if (overlay != null) {
                        drawable = new LayerDrawable(new Drawable[]{drawable, overlay});
                    }
                    drawable.setBounds(0, 0, dim.scaledWidth, dim.scaledHeight);
                    span = new ImageSpan(drawable);
                }
            } catch (Exception ex) {
                Log.w(TAG, "Broken image preview", ex);
            }
        }

        // Out of band image.
        if (span == null && ref != null) {
            URL url = Cache.getTinode().toAbsoluteURL(ref);
            if (dim.scale > 0 && url != null) {
                Drawable fg;

                // "Image loading" placeholder.
                Drawable placeholder;
                if (bmpPreview != null) {
                    placeholder = new BitmapDrawable(ctx.getResources(), bmpPreview);
                } else {
                    fg = AppCompatResources.getDrawable(ctx, id_placeholder);
                    if (fg != null) {
                        fg.setBounds(0, 0, fg.getIntrinsicWidth(), fg.getIntrinsicHeight());
                    }
                    placeholder = UiUtils.getPlaceholder(ctx, fg, null, dim.scaledWidth, dim.scaledHeight);
                }

                // "Failed to load image" placeholder.
                fg = AppCompatResources.getDrawable(ctx, id_error);
                if (fg != null) {
                    fg.setBounds(0, 0, fg.getIntrinsicWidth(), fg.getIntrinsicHeight());
                }

                Drawable onError = UiUtils.getPlaceholder(ctx, overlay != null ? null : fg, null,
                        dim.scaledWidth, dim.scaledHeight);
                span = new RemoteImageSpan(mContainer, dim.scaledWidth, dim.scaledHeight,
                        false, placeholder, onError);
                ((RemoteImageSpan) span).setOverlay(overlay);
                ((RemoteImageSpan) span).load(url);
            }
        }

        return span;
    }

    @Override
    protected SpannableStringBuilder handleImage(final Context ctx, List<SpannableStringBuilder> content,
                                                 final Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        ImageDim dim = new ImageDim();

        // Bitmap dimensions specified by the sender.
        dim.width = getIntVal("width", data);
        dim.height = getIntVal("height", data);

        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        CharacterStyle span = createImageSpan(ctx, data.get("val"),
                getStringVal("ref", data, null), dim, metrics.density,
                null,
                R.drawable.ic_image, R.drawable.ic_broken_image);

        SpannableStringBuilder result = null;
        if (span == null) {
            // If the image cannot be decoded for whatever reason, show a 'broken image' icon.
            Drawable broken = AppCompatResources.getDrawable(ctx, R.drawable.ic_broken_image);
            if (broken != null) {
                broken.setBounds(0, 0, broken.getIntrinsicWidth(), broken.getIntrinsicHeight());
                span = new ImageSpan(UiUtils.getPlaceholder(ctx, broken, null, dim.scaledWidth, dim.scaledHeight));
                result = assignStyle(span, content);
            }
        } else if (mClicker != null) {
            // Make image clickable by wrapping ImageSpan into a ClickableSpan.
            result = assignStyle(span, content);
            if (result != null) {
                result.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        mClicker.onClick("IM", data, null);
                    }
                }, 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            result = assignStyle(span, content);
        }

        return result;
    }

    @Override
    protected SpannableStringBuilder handleVideo(final Context ctx, List<SpannableStringBuilder> content,
                                                 final Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        ImageDim dim = new ImageDim();
        // Video dimensions specified by the sender.
        dim.width = getIntVal("width", data);
        dim.height = getIntVal("height", data);

        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();

        // Play ( > ) icon + [00:00] over the image.
        Resources res = ctx.getResources();
        LayerDrawable overlay;
        if (mClicker != null) {
            //noinspection ConstantConditions
            overlay = (LayerDrawable) (AppCompatResources.getDrawable(ctx, R.drawable.video_overlay).mutate());
            TextDrawable duration = new TextDrawable(res,
                    millisToTime(getIntVal("duration", data), false).toString());
            duration.setTextSize(res, 12);
            // Do not need to use Theme color because the background here is a video frame.
            duration.setTextColor(0xFF666666);
            overlay.setDrawableByLayerId(R.id.duration, duration);
        } else {
            overlay = null;
        }

        CharacterStyle span = createImageSpan(ctx, data.get("preview"),
                getStringVal("preref", data, null), dim, metrics.density,
                overlay,
                R.drawable.ic_video, R.drawable.ic_video);

        SpannableStringBuilder result;
        if (span == null) {
            // If the poster cannot be decoded for whatever reason, just show show a uniform gray background
            // with play control on top.
            Drawable drawable = UiUtils.getPlaceholder(ctx, null, null, dim.scaledWidth, dim.scaledHeight);
            if (overlay != null) {
                Rect bounds = drawable.copyBounds();
                drawable = new LayerDrawable(new Drawable[]{drawable, overlay});
                drawable.setBounds(bounds);
            }
            span = new ImageSpan(drawable);
        }

        if (mClicker != null) {
            // Make image clickable by wrapping ImageSpan into a ClickableSpan.
            result = assignStyle(span, content);
            result.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    mClicker.onClick("VD", data, null);
                }
            }, 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
        } else {
            result = assignStyle(span, content);
        }

        return result;
    }

    // Scale image dimensions to fit under the given viewport size.
    protected static float scaleBitmap(int srcWidth, int srcHeight, int viewportWidth, float density) {
        if (srcWidth == 0 || srcHeight == 0) {
            return 0f;
        }

        // Convert DP to pixels.
        float width = srcWidth * density;
        float height = srcHeight * density;
        float maxWidth = viewportWidth - IMAGE_H_PADDING * density;

        // Make sure the scaled bitmap is no bigger than the viewport size;
        float scaleX = Math.min(width, maxWidth) / width;
        float scaleY = Math.min(height, maxWidth * 0.75f) / height;
        return Math.min(scaleX, scaleY);
    }

    @Override
    protected SpannableStringBuilder handleFormRow(Context ctx, List<SpannableStringBuilder> content,
                                                   Map<String, Object> data) {
        return join(content);
    }

    @Override
    protected SpannableStringBuilder handleForm(Context ctx, List<SpannableStringBuilder> content,
                                                Map<String, Object> data) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        // Add line breaks between form elements.
        SpannableStringBuilder span = new SpannableStringBuilder();
        for (SpannableStringBuilder ssb : content) {
            span.append(ssb).append("\n");
        }
        mContainer.setLineSpacing(0, FORM_LINE_SPACING);
        return span;
    }

    @Override
    protected SpannableStringBuilder handleAttachment(final Context ctx, final Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        if (isSkippableJson(data.get("mime"))) {
            // Skip JSON attachments. They are not meant to be user-visible.
            return null;
        }

        SpannableStringBuilder result = new SpannableStringBuilder();
        // Insert document icon
        Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_file);
        //noinspection ConstantConditions
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        ImageSpan span = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
        final Rect bounds = span.getDrawable().getBounds();
        result.append(" ", span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.setSpan(new SubscriptSpan(), 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Insert document's file name
        String fname = getStringVal("name", data, null);
        if (TextUtils.isEmpty(fname)) {
            fname = ctx.getResources().getString(R.string.default_attachment_name);
        } else if (fname.length() > MAX_FILE_LENGTH) {
            fname = fname.substring(0, MAX_FILE_LENGTH/2 - 1) + "…" +
                    fname.substring(fname.length() - MAX_FILE_LENGTH/2);
        }

        result.append(fname, new TypefaceSpan("monospace"), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int byteCount = getIntVal("size", data);
        if (byteCount <= 0) {
            byte[] bits = UiUtils.decodeByteArray(data.get("val"));
            if (bits != null) {
                byteCount = bits.length;
            }
        }
        if (byteCount > 0) {
            result.append("\u2009(" + UtilsString.bytesToHumanSize(byteCount) +")",
                    new ForegroundColorSpan(Color.GRAY), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (mClicker == null) {
            return result;
        }

        // Add download link.

        // Do we have attachment bits out-of-band or in-band?
        boolean valid = (data.get("ref") instanceof String) || (data.get("val") != null);

        // Insert linebreak then a clickable [↓ save] or [(!) unavailable] line.
        result.append("\n");
        SpannableStringBuilder saveLink = new SpannableStringBuilder();
        // Add 'download file' icon
        icon = AppCompatResources.getDrawable(ctx, valid ?
                R.drawable.ic_download_link : R.drawable.ic_error_gray);
        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        //noinspection ConstantConditions
        icon.setBounds(0, 0,
                (int) (ICON_SIZE_DP * metrics.density),
                (int) (ICON_SIZE_DP * metrics.density));
        saveLink.append(" ", new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (valid) {
            // Clickable "save".
            saveLink.append(ctx.getResources().getString(R.string.download_attachment),
                    new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    mClicker.onClick("EX", data, null);
                }
            }, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            // Grayed-out "unavailable".
            saveLink.append(" " + ctx.getResources().getString(R.string.unavailable),
                    new ForegroundColorSpan(Color.GRAY), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Add space on the left to make the link appear under the file name.
        result.append(saveLink, new LeadingMarginSpan.Standard(bounds.width()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Append thin space after the link, otherwise the whole line to the right is clickable.
        result.append('\u2009');
        return result;
    }

    // Button: URLSpan wrapped into LineHeightSpan and then BorderedSpan.
    @Override
    protected SpannableStringBuilder handleButton(final Context ctx, List<SpannableStringBuilder> content,
                                                  final Map<String, Object> data) {
        // This is needed for button shadows.
        mContainer.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        // Size of a DIP pixel.
        float dipSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, metrics);

        // Make button clickable.
        final SpannableStringBuilder node = new SpannableStringBuilder();
        node.append(join(content), new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (mClicker != null) {
                    mClicker.onClick("BN", data, null);
                }
            }
            // Ignored.
            @Override public void updateDrawState(@NonNull TextPaint ds) {}
        }, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // URLSpan into ButtonSpan.
        node.setSpan(new ButtonSpan(ctx, mFontSize, dipSize), 0, node.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Append a thin space after a button, otherwise the whole line after the button
        // becomes clickable if the button is the last element in a line.
        return node.append('\u2009');
    }

    static SpannableStringBuilder handleQuote_Impl(Context ctx, List<SpannableStringBuilder> content) {
        SpannableStringBuilder outer = new SpannableStringBuilder();
        SpannableStringBuilder inner = new SpannableStringBuilder();
        inner.append("\n", new RelativeSizeSpan(0.25f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        inner.append(join(content));
        // Adding a line break with some non-breaking white space around it to create extra padding.
        inner.append("\u00A0\u00A0\u00A0\u00A0\n\u00A0", new RelativeSizeSpan(0.2f),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Resources res = ctx.getResources();
        DisplayMetrics metrics = res.getDisplayMetrics();
        QuotedSpan style = new QuotedSpan(res.getColor(R.color.colorReplyBubble, null),
                CORNER_RADIUS_DP * metrics.density,
                res.getColor(R.color.colorAccent, null),
                QUOTE_STRIPE_WIDTH_DP * metrics.density,
                STRIPE_GAP_DP * metrics.density);
        return outer.append(inner, style, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    protected SpannableStringBuilder handleQuote(Context ctx, List<SpannableStringBuilder> content,
                                                 Map<String, Object> data) {
        SpannableStringBuilder node = handleQuote_Impl(ctx, content);
        // Increase spacing between the quote and the subsequent text.
        return node.append("\n\n", new RelativeSizeSpan(0.3f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    protected SpannableStringBuilder handleVideoCall(final Context ctx, List<SpannableStringBuilder> content,
                                                     final Map<String, Object> data) {

        if (data == null) {
            return handleUnknown(ctx, content, null);
        }

        SpannableStringBuilder result = new SpannableStringBuilder();
        // Insert document icon
        Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_call);
        //noinspection ConstantConditions
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        ImageSpan span = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
        final Rect bounds = span.getDrawable().getBounds();
        result.append(" ", span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        result.setSpan(new SubscriptSpan(), 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        boolean incoming = getBooleanVal("incoming", data);
        result.append(ctx.getString(incoming ? R.string.incoming_call : R.string.outgoing_call),
                new RelativeSizeSpan(1.15f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int duration = getIntVal("duration", data);
        String state = getStringVal("state", data, "");

        result.append("\n");

        SpannableStringBuilder second = new SpannableStringBuilder();
        boolean success = !Arrays.asList("busy", "declined", "disconnected", "missed")
                .contains(state);
        icon = AppCompatResources.getDrawable(ctx,
                incoming ?
                        (success ? R.drawable.ic_arrow_sw : R.drawable.ic_arrow_missed) :
                        (success ? R.drawable.ic_arrow_ne : R.drawable.ic_arrow_cancelled));
        //noinspection ConstantConditions
        icon.setBounds(0, 0, (int) (icon.getIntrinsicWidth() * 0.67), (int) (icon.getIntrinsicHeight() * 0.67));
        icon.setTint(success ? 0xFF338833 : 0xFF993333);
        second.append(" ", new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        second.append(" ");
        if (duration > 0) {
            second.append(millisToTime(duration, false));
        } else {
            second.append(ctx.getString(callStatus(incoming, state)));
        }
        // Shift second line to the right.
        result.append(second, new LeadingMarginSpan.Standard(bounds.width()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return result;
    }

    // Unknown or unsupported element.
    @Override
    protected SpannableStringBuilder handleUnknown(final Context ctx, List<SpannableStringBuilder> content,
                                                   final Map<String, Object> data) {
        SpannableStringBuilder result;
        Drawable unkn = AppCompatResources.getDrawable(ctx, R.drawable.ic_unkn_type);
        //noinspection ConstantConditions
        unkn.setBounds(0, 0, unkn.getIntrinsicWidth(), unkn.getIntrinsicHeight());

        if (data != null) {
            // Does object have viewport dimensions?
            int width = getIntVal("width", data);
            int height = getIntVal("height", data);
            if (width <= 0 || height <= 0) {
                return handleAttachment(ctx, data);
            }

            // Calculate scaling factor for images to fit into the viewport.
            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            float scale = scaleBitmap(width, height, mViewport, metrics.density);
            // Bitmap dimensions specified by the sender converted to viewport size in display pixels.
            int scaledWidth = 0, scaledHeight = 0;
            if (scale > 0) {
                scaledWidth = (int) (width * scale * metrics.density);
                scaledHeight = (int) (height * scale * metrics.density);
            }

            CharacterStyle span = new ImageSpan(UiUtils.getPlaceholder(ctx, unkn, null, scaledWidth, scaledHeight));
            result = assignStyle(span, content);
        } else {
            result = new SpannableStringBuilder();
            result.append(" ", new ImageSpan(unkn, ImageSpan.ALIGN_BOTTOM), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            result.append(" ");
            if (content != null) {
                result.append(join(content));
            } else {
                result.append(ctx.getString(R.string.unknown));
            }
        }

        return result;
    }

    // Plain (unstyled) content.
    @Override
    protected SpannableStringBuilder handlePlain(final List<SpannableStringBuilder> content) {
        return join(content);
    }

    public interface ClickListener {
        boolean onClick(String type, Map<String, Object> data, Object params);
    }

    public interface AudioControlCallback {
        void reset();
        void pause();
        void resume();
    }

    public static class AudioClickAction {
        public enum Action {PLAY, PAUSE, SEEK}

        public final Float seekTo;
        public final Action action;

        public AudioControlCallback control;

        private AudioClickAction(Action action) {
            this.action = action;
            seekTo = null;
        }

        public AudioClickAction(Action action, AudioControlCallback callback) {
            this(action);
            control = callback;
        }

        public AudioClickAction(float seekTo, AudioControlCallback callback) {
            action = Action.SEEK;
            this.seekTo = seekTo;
            control = callback;
        }
    }
}
