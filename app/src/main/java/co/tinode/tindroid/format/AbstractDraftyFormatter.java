package co.tinode.tindroid.format;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import androidx.annotation.NonNull;
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
            T span;
            switch (tp) {
                case "ST":
                    span = handleStrong(content);
                    break;
                case "EM":
                    span = handleEmphasized(content);
                    break;
                case "DL":
                    span = handleDeleted(content);
                    break;
                case "CO":
                    span = handleCode(content);
                    break;
                case "HD":
                    // Hidden text
                    span = handleHidden(content);
                    break;
                case "BR":
                    span = handleLineBreak();
                    break;
                case "LN":
                    span = handleLink(mContext, content, data);
                    break;
                case "MN":
                    span = handleMention(mContext, content, data);
                    break;
                case "HT":
                    span = handleHashtag(mContext, content, data);
                    break;
                case "AU":
                    // Audio player.
                    span = handleAudio(mContext, content, data);
                    break;
                case "IM":
                    span = handleImage(mContext, content, data);
                    break;
                case "VD":
                    span = handleVideo(mContext, content, data);
                    break;
                case "EX":
                    // Attachments; attachments cannot have sub-elements.
                    span = handleAttachment(mContext, data);
                    break;
                case "BN":
                    // Button
                    span = handleButton(mContext, content, data);
                    break;
                case "FM":
                    // Form
                    span = handleForm(mContext, content, data);
                    break;
                case "RW":
                    // Form element formatting is dependent on element content.
                    span = handleFormRow(mContext, content, data);
                    break;
                case "QQ":
                    // Quoted block.
                    span = handleQuote(mContext, content, data);
                    break;
                case "VC":
                    // Video call.
                    span = handleVideoCall(mContext, content, data);
                    break;
                default:
                    // Unknown element
                    span = handleUnknown(mContext, content, data);
            }
            return span;
        }
        return handlePlain(content);
    }

    protected static SpannableStringBuilder join(List<SpannableStringBuilder> content) {
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

    protected static SpannableStringBuilder assignStyle(@NonNull Object style, List<SpannableStringBuilder> content) {
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
        int comment;
        switch (event) {
            case "busy":
                comment = R.string.busy_call;
                break;
            case "declined":
                comment = R.string.declined_call;
                break;
            case "missed":
                comment = incoming ? R.string.missed_call : R.string.cancelled_call;
                break;
            case "started":
                comment = R.string.connecting_call;
                break;
            case "accepted":
                comment = R.string.in_progress_call;
                break;
            default:
                comment = R.string.disconnected_call;
                break;
        }
        return comment;
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

    protected static boolean getBooleanVal(String name, Map<String, Object> data) {
        Object tmp;
        if ((tmp = data.get(name)) instanceof Boolean) {
            return (boolean) tmp;
        }
        return false;
    }
}
