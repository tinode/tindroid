package co.tinode.tinodesdk;

import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Augmented SimpleDateFormat for handling optional milliseconds in RFC3339 timestamps.
 */
public class RFC3339Format extends SimpleDateFormat {
    private final SimpleDateFormat mShortDate;

    public RFC3339Format() {
        super("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);
        mShortDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

        setTimeZone(TimeZone.getTimeZone("UTC"));
        mShortDate.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // Server may generate timestamps without milliseconds.
    // SDF cannot parse optional millis. Must treat them explicitly.
    @Override
    public Date parse(@NotNull String text) throws ParseException {
        Date date;
        try {
            date = super.parse(text);
        } catch (ParseException ignore) {
            date = mShortDate.parse(text);
        }
        return date;
    }
}

