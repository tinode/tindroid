package co.tinode.tinui.format;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.List;
import java.util.Map;

import co.tinode.tinui.R;

// Drafty formatter for creating one-line message previews.
public class PreviewFormatter extends AbstractDraftyFormatter<SpannableStringBuilder> {
    private final float mFontSize;

    public PreviewFormatter(final Context context, float fontSize) {
        super(context);

        mFontSize = fontSize;
    }

    @Override
    public SpannableStringBuilder wrapText(CharSequence text) {
        return text != null ? new SpannableStringBuilder(text) : null;
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
        return new SpannableStringBuilder(" ");
    }

    @Override
    protected SpannableStringBuilder handleLink(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        return assignStyle(new ForegroundColorSpan(ctx.getResources().getColor(R.color.colorAccent)), content);
    }

    @Override
    protected SpannableStringBuilder handleMention(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        return join(content);
    }

    @Override
    protected SpannableStringBuilder handleHashtag(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        return join(content);
    }

    protected SpannableStringBuilder annotatedIcon(Context ctx, @DrawableRes int iconId, @StringRes int stringId) {
        SpannableStringBuilder node = null;
        Drawable icon = AppCompatResources.getDrawable(ctx, iconId);
        if (icon != null) {
            Resources res = ctx.getResources();
            icon.setTint(res.getColor(R.color.colorDarkGray));
            icon.setBounds(0, 0, (int) (mFontSize * 1.3), (int) (mFontSize * 1.3));
            node = new SpannableStringBuilder(" ");
            node.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), 0, node.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (stringId != -1) {
                node.append(" ").append(res.getString(stringId));
            }
        }
        return node;
    }

    @Override
    protected SpannableStringBuilder handleImage(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.tinui_ic_image_ol, R.string.picture);
    }

    @Override
    protected SpannableStringBuilder handleAttachment(Context ctx, Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        try {
            if ("application/json".equals(data.get("mime"))) {
                // Skip JSON attachments. They are not meant to be user-visible.
                return null;
            }
        } catch (ClassCastException ignored) {}

        return annotatedIcon(ctx, R.drawable.tinui_ic_attach_ol, R.string.attachment);
    }

    @Override
    protected SpannableStringBuilder handleButton(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        SpannableStringBuilder outer = new SpannableStringBuilder();
        // Non-breaking space as padding in front of the button.
        outer.append("\u00A0");
        // Size of a DIP pixel.
        float dipSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f,
                ctx.getResources().getDisplayMetrics());

        SpannableStringBuilder inner = join(content);
        // Make button font slightly smaller.
        inner.setSpan(new RelativeSizeSpan(0.8f), 0, inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Change background color and draw a box around text.
        inner.setSpan(new LabelSpan(ctx, mFontSize, dipSize), 0, inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return outer.append(inner);
    }

    @Override
    protected SpannableStringBuilder handleFormRow(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        return new SpannableStringBuilder(" ").append(join(content));
    }

    @Override
    protected SpannableStringBuilder handleForm(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        SpannableStringBuilder node = annotatedIcon(ctx, R.drawable.tinui_ic_form_ol, R.string.form);
        return node.append(": ").append(join(content));
    }

    @Override
    protected SpannableStringBuilder handleQuote(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        // Not showing quoted content in preview.
        return null;
    }

    @Override
    protected SpannableStringBuilder handleUnknown(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.tinui_ic_unkn_type_ol, R.string.unknown);
    }

    @Override
    protected SpannableStringBuilder handlePlain(final List<SpannableStringBuilder> content) {
        return join(content);
    }
}
