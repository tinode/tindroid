package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

import java.util.Map;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import co.tinode.tindroid.R;

// Drafty formatter for creating message previews in push notifications.
// Push notifications don't support ImageSpan, sonsequenly, using UNicode chars instead of icons.
public class UnicodeFormatter extends PreviewFormatter {
    private static final Character IMAGE = ' ';
    private static final Character ATTACHMENT = ' ';
    private static final Character FORM = ' ';
    private static final Character UNKNOWN = ' ';

    UnicodeFormatter(final Context context, float fontSize) {
        super(context, fontSize);
    }

    private MeasuredTreeNode annotatedIcon(Context ctx, @DrawableRes int iconId, @StringRes int stringId) {
        MeasuredTreeNode node = null;
        Drawable icon = AppCompatResources.getDrawable(ctx, iconId);
        if (icon != null) {
            icon.setTint(ctx.getResources().getColor(R.color.colorDarkGray));
            icon.setBounds(0, 0, (int) (mFontSize * 1.3), (int) (mFontSize * 1.3));
            node = new MeasuredTreeNode();
            node.addNode(new MeasuredTreeNode(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), " "));
            node.addNode(new MeasuredTreeNode(" " + ctx.getResources().getString(stringId)));
        }
        return node;
    }

    @Override
    protected MeasuredTreeNode handleImage(Context ctx, Object content, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.ic_image_ol, R.string.picture);
    }

    @Override
    protected MeasuredTreeNode handleAttachment(Context ctx, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.ic_attach_ol, R.string.attachment);
    }

    @Override
    protected MeasuredTreeNode handleForm(Context ctx, Map<String, Object> data, Object content) {
        MeasuredTreeNode node = annotatedIcon(ctx, R.drawable.ic_form_ol, R.string.form);
        node.addNode(new MeasuredTreeNode(": "));
        node.addNode(new MeasuredTreeNode(content));
        return node;
    }

    @Override
    protected MeasuredTreeNode handleUnknown(Context ctx, Object content, Map<String, Object> data) {
        return annotatedIcon(ctx, R.drawable.ic_unkn_type_ol, R.string.unknown);
    }
}
