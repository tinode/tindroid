package co.tinode.tindroid.format;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

public abstract class AbstractDraftyFormatter<T extends Spanned> implements Drafty.Formatter<T> {
    protected final Context mContext;

    protected AbstractDraftyFormatter(final Context context) {
        mContext = context;
    }

    protected abstract T handleStrong(List<T> content);

    protected abstract T handleEmphasized(List<T> content);

    protected abstract T handleDeleted(List<T> content);

    protected abstract T handleCode(List<T> content);

    protected abstract T handleHidden(List<T> content);

    protected abstract T handleLineBreak();

    // URL.
    protected abstract T handleLink(final Context ctx, List<T> content, final Map<String, Object> data);

    // Mention @user.
    protected abstract T handleMention(final Context ctx, List<T> content, final Map<String, Object> data);

    // Hashtag #searchterm.
    protected abstract T handleHashtag(final Context ctx, List<T> content, final Map<String, Object> data);

    // Embedded voice mail.
    protected abstract T handleAudio(final Context ctx, List<T> content, final Map<String, Object> data);

    // Embedded image.
    protected abstract T handleImage(final Context ctx, List<T> content, final Map<String, Object> data);

    // Embedded image.
    protected abstract T handleVideo(final Context ctx, List<T> content, final Map<String, Object> data);

    // File attachment.
    protected abstract T handleAttachment(final Context ctx, final Map<String, Object> data);

    // Button: clickable form element.
    protected abstract T handleButton(final Context ctx, List<T> content, final Map<String, Object> data);

    // Grouping of form elements.
    protected abstract T handleFormRow(final Context ctx, List<T> content, final Map<String, Object> data);

    // Interactive form.
    protected abstract T handleForm(final Context ctx, List<T> content, final Map<String, Object> data);

    // Quoted block.
    protected abstract T handleQuote(final Context ctx, List<T> content, final Map<String, Object> data);

    // Video call.
    protected abstract T handleVideoCall(final Context ctx, List<T> content, final Map<String, Object> data);

    // Unknown or unsupported element.
    protected abstract T handleUnknown(final Context ctx, List<T> content, final Map<String, Object> data);

    // Unstyled content
    protected abstract T handlePlain(List<T> content);

    @Override
    public abstract T wrapText(CharSequence text);

    @Override
    public T apply(final String tp, final Map<String, Object> data, final List<T> content, Stack<String> context) {
        if (tp != null) {
            return switch (tp) {
                case "ST" -> handleStrong(content);
                case "EM" -> handleEmphasized(content);
                case "DL" -> handleDeleted(content);
                case "CO" -> handleCode(content);
                case "HD" ->
                    // Hidden text
                        handleHidden(content);
                case "BR" -> handleLineBreak();
                case "LN" -> handleLink(mContext, content, data);
                case "MN" -> handleMention(mContext, content, data);
                case "HT" -> handleHashtag(mContext, content, data);
                case "AU" ->
                    // Audio player.
                        handleAudio(mContext, content, data);
                case "IM" -> handleImage(mContext, content, data);
                case "VD" -> handleVideo(mContext, content, data);
                case "EX" ->
                    // Attachments; attachments cannot have sub-elements.
                        handleAttachment(mContext, data);
                case "BN" ->
                    // Button
                        handleButton(mContext, content, data);
                case "FM" ->
                    // Form
                        handleForm(mContext, content, data);
                case "RW" ->
                    // Form element formatting is dependent on element content.
                        handleFormRow(mContext, content, data);
                case "QQ" ->
                    // Quoted block.
                        handleQuote(mContext, content, data);
                case "VC" ->
                    // Video call.
                        handleVideoCall(mContext, content, data);
                default ->
                    // Unknown element
                        handleUnknown(mContext, content, data);
            };
        }
        return handlePlain(content);
    }

    protected static @Nullable SpannableStringBuilder join(List<SpannableStringBuilder> content) {
        SpannableStringBuilder ssb = null;
        if (content != null) {
            Iterator<SpannableStringBuilder> it = content.iterator();
            ssb = it.next();
            while (it.hasNext()) {
                ssb.append(it.next());
            }
        }
        return ssb;
    }

    protected static @Nullable SpannableStringBuilder assignStyle(@NonNull Object style,
                                                                  List<SpannableStringBuilder> content) {
        SpannableStringBuilder ssb = join(content);
        if (ssb != null) {
            ssb.setSpan(style, 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    // Convert milliseconds to '[00:0]0:00' or '[00:]00:00' (fixedMin) format.
    protected static StringBuilder millisToTime(@NonNull Number millis, boolean fixedMin) {
        StringBuilder sb = new StringBuilder();
        float duration = millis.floatValue() / 1000;

        int hours = (int) Math.floor(duration / 3600f);
        if (hours > 0) {
            sb.append(hours).append(":");
        }

        int min = (int) Math.floor(duration / 60f);
        if (hours > 0 || (fixedMin && min < 10)) {
            sb.append("0");
        }
        sb.append(min % 60).append(":");

        int sec = (int) (duration % 60f);
        if (sec < 10) {
            sb.append("0");
        }
        return sb.append(sec);
    }

    protected static int callStatus(boolean incoming, String event) {
        return switch (event) {
            case "busy" -> R.string.busy_call;
            case "declined" -> R.string.declined_call;
            case "missed" -> incoming ? R.string.missed_call : R.string.cancelled_call;
            case "started" -> R.string.connecting_call;
            case "accepted" -> R.string.in_progress_call;
            default -> R.string.disconnected_call;
        };
    }

    protected static int getIntVal(String name, Map<String, Object> data) {
        Object tmp;
        if ((tmp = data.get(name)) instanceof Number) {
            return ((Number) tmp).intValue();
        }
        return 0;
    }

    protected static String getStringVal(String name, Map<String, Object> data, String def) {
        Object tmp;
        if ((tmp = data.get(name)) instanceof CharSequence) {
            return tmp.toString();
        }
        return def;
    }

    /** @noinspection SameParameterValue*/
    protected static boolean getBooleanVal(String name, Map<String, Object> data) {
        Object tmp;
        if ((tmp = data.get(name)) instanceof Boolean) {
            return (boolean) tmp;
        }
        return false;
    }

    protected static boolean isSkippableJson(Object mime) {
        return Drafty.isFormResponseType(mime);
    }
}
