package co.tinode.tindroid;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.UCharacterIterator;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;

public class UtilsString {
    // Date formatter for messages
    @NonNull
    static String shortDate(Date date) {
        if (date != null) {
            Calendar now = Calendar.getInstance();
            Calendar then = Calendar.getInstance();
            then.setTime(date);

            if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                if (then.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                        then.get(Calendar.DATE) == now.get(Calendar.DATE)) {
                    return DateFormat.getTimeInstance(DateFormat.SHORT).format(then.getTime());
                } else {
                    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(then.getTime());
                }
            }
            return DateFormat.getInstance().format(then.getTime());
        }
        return "unknown";
    }

    // Time formatter for messages.
    @NonNull
    static String timeOnly(Context context, Date date) {
        if (date != null) {
            return DateFormat.getTimeInstance(DateFormat.SHORT).format(date.getTime());
        }
        return context.getString(R.string.unknown);
    }

    // Date format relative to present.
    @NonNull
    static CharSequence relativeDateFormat(Context context, Date then) {
        if (then == null) {
            return context.getString(R.string.never);
        }
        long thenMillis = then.getTime();
        if (thenMillis == 0) {
            return context.getString(R.string.never);
        }
        long nowMillis = System.currentTimeMillis();
        if (nowMillis - thenMillis < DateUtils.MINUTE_IN_MILLIS) {
            return context.getString(R.string.just_now);
        }

        return DateUtils.getRelativeTimeSpanString(thenMillis, nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL);
    }

    // Convert milliseconds to '00:00' format.
    @NonNull
    static String millisToTime(int millis) {
        StringBuilder sb = new StringBuilder();
        float duration = millis / 1000f;
        int min = (int) Math.floor(duration / 60f);
        if (min < 10) {
            sb.append("0");
        }
        sb.append(min).append(":");
        int sec = (int) (duration % 60f);
        if (sec < 10) {
            sb.append("0");
        }
        return sb.append(sec).toString();
    }

    public static String bytesToHumanSize(long bytes) {
        if (bytes <= 0) {
            // 0x202F - narrow non-breaking space.
            return "0\u202FBytes";
        }

        String[] sizes = new String[]{"Bytes", "KB", "MB", "GB", "TB"};
        int bucket = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        double count = bytes / Math.pow(1024, bucket);
        int roundTo = bucket > 0 ? (count < 3 ? 2 : (count < 30 ? 1 : 0)) : 0;
        NumberFormat fmt = DecimalFormat.getInstance();
        fmt.setMaximumFractionDigits(roundTo);
        return fmt.format(count) + "\u202F" + sizes[bucket];
    }

    // The same as TextUtils.equals except null == "".
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean stringsEqual(CharSequence a, CharSequence b) {
        if (a == b) {
            return true;
        }

        if (a != null && b != null) {
            int length = a.length();
            if (length != b.length()) {
                return false;
            }
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            }
            for (int i = 0; i < length; i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        if (a == null) {
            return b.length() == 0;
        }
        return a.length() == 0;
    }

    /**
     * Identifies the start of the search string (needle) in the display name (haystack).
     * E.g. If display name was "Adam" and search query was "da" this would
     * return 1.
     *
     * @param haystack The contact display name.
     * @return The starting position of the search string in the display name, 0-based. The
     * method returns -1 if the string is not found in the display name, or if the search
     * string is empty or null.
     */
    static int indexOfSearchQuery(String haystack, String needle) {
        if (!TextUtils.isEmpty(needle)) {
            return haystack.toLowerCase(Locale.getDefault()).indexOf(
                    needle.toLowerCase(Locale.getDefault()));
        }

        return -1;
    }

    private static final Pattern sTelRegex = Pattern.compile(
            "^(?:\\+?(\\d{1,3}))?[- (.]*(\\d{3})[- ).]*(\\d{3})[- .]*(\\d{2})[- .]*(\\d{2})?$",
            Pattern.CASE_INSENSITIVE);
    /**
     * Checks (loosely) if the given string is a phone. If so, returns the phone number in a format
     *  as close to E.164 as possible.
     */
    static String asPhone(@NotNull String val) {
        val = val.trim();
        if (sTelRegex.matcher(val).matches()) {
            return val.replaceAll("[- ().]*", "");
        }
        return null;
    }
    private static final Pattern sEmailRegex = Pattern.compile(
            "^[a-z0-9_.+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)+$",
            Pattern.CASE_INSENSITIVE);
    /**
     * Checks (loosely) if the given string is an email. If so returns the email.
     */
    static String asEmail(@NonNull String val) {
        val = val.trim();
        if (sEmailRegex.matcher(val).matches()) {
            return val;
        }
        return null;
    }

    // If string contains emoji only, count the number of emojis up to 5.
    static int countEmoji(CharSequence text, int maxCount) {
        // Not using TextUtils.isEmpty() because it's not mocked causing unit tests to fail.
        if (text == null || text.length() == 0 || maxCount == 0) {
            return 0;
        }

        int count = 0;
        BreakIterator iter = BreakIterator.getCharacterInstance();
        iter.setText(text);
        int i = iter.first();
        for (int next = iter.next(); next != BreakIterator.DONE; i = next, next = iter.next()) {
            int cp = Character.codePointAt(text, i);
            int len = next - i;

            if (UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_MODIFIER)) {
                if (count == 0) {
                    // Modifier cannot be the first character. Invalid.
                    return -1;
                }
                // Do nothing (do not count modifiers).
                // Checking for them first because UProperty.EMOJI is true for them as well.
                continue;
            } else if (UCharacter.hasBinaryProperty(cp, UProperty.EMOJI) && (len > 1 || cp > 0x238C)) {
                count++;
            } else if (cp == 0x200D) {
                // ZERO WIDTH JOINER: it's not a stand alone codepoint and the next code point should not
                // be counted, thus count --.
                if (count > 0) {
                    count--;
                } else {
                    // If the first codepoint is a ZWJ, then it's invalid
                    return -1;
                }
            } else if (UCharacter.hasBinaryProperty(cp, UProperty.VARIATION_SELECTOR)) {
                // Variant selector as the first character: invalid.
                if (count == 0) {
                    return -1;
                }
                // Do nothing (do not count variation selectors).
                continue;
            } else {
                return -1;
            }

            if (count >= maxCount) {
                break;
            }
        }
        return count;
    }
}
