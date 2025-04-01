package co.tinode.tindroid;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import co.tinode.tinodesdk.Tinode;

public class UtilsString {
    // Default tag parameters
    private static final int DEFAULT_MIN_TAG_LENGTH = 4;
    private static final int DEFAULT_MAX_TAG_LENGTH = 96;
    private static final int DEFAULT_MAX_TAG_COUNT = 16;

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

    // Parse comma separated list of possible quoted string into an array.
    static String[] parseTags(final String tagList) {
        if (TextUtils.isEmpty(tagList)) {
            return null;
        }

        ArrayList<String> tags = new ArrayList<>();
        int start = 0;
        final Tinode tinode = Cache.getTinode();
        final long maxTagCount = tinode.getServerLimit(Tinode.MAX_TAG_COUNT, DEFAULT_MAX_TAG_COUNT);
        final long maxTagLength = tinode.getServerLimit(Tinode.MAX_TAG_LENGTH, DEFAULT_MAX_TAG_LENGTH);
        final long minTagLength = tinode.getServerLimit(Tinode.MIN_TAG_LENGTH, DEFAULT_MIN_TAG_LENGTH);

        final int length = tagList.length();
        boolean quoted = false;
        for (int idx = 0; idx < length && tags.size() < maxTagCount; idx++) {
            if (tagList.charAt(idx) == '\"') {
                // Toggle 'inside of quotes' state.
                quoted = !quoted;
            }

            String tag;
            if (tagList.charAt(idx) == ',' && !quoted) {
                tag = tagList.substring(start, idx);
                start = idx + 1;
            } else if (idx == length - 1) {
                // Last char
                tag = tagList.substring(start);
            } else {
                continue;
            }

            tag = tag.trim();
            // Remove possible quotes.
            if (tag.length() > 1 && tag.charAt(0) == '\"' && tag.charAt(tag.length() - 1) == '\"') {
                tag = tag.substring(1, tag.length() - 1).trim();
            }
            if (tag.length() >= minTagLength && tag.length() <= maxTagLength) {
                tags.add(tag);
            }
        }

        if (tags.isEmpty()) {
            return null;
        }

        return tags.toArray(new String[]{});
    }
}
