package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Range is either an individual ID (hi=0 || hi==null) or a range of deleted IDs, low end inclusive (closed),
 * high-end exclusive (open): [low .. hi), e.g. 1..5 → 1, 2, 3, 4
 */
@JsonInclude(NON_DEFAULT)
@SuppressWarnings("WeakerAccess")
public class MsgRange implements Comparable<MsgRange>, Serializable {
    // The low value is required, thus it's a primitive type.
    public final int low;
    // The high value is optional.
    public Integer hi;

    public MsgRange() {
        low = 0;
    }

    public MsgRange(int id) {
        low = id;
    }

    public MsgRange(int low, int hi) {
        this.low = low;
        this.hi = hi;
    }

    public MsgRange(MsgRange that) {
        this.low = that.low;
        this.hi = that.hi;
    }

    @Override
    public int compareTo(MsgRange other) {
        int r = low - other.low;
        if (r == 0) {
            r = nullableCompare(other.hi, hi);
        }
        return r;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MsgRange other = (MsgRange) obj;
        return low == other.low && nullableCompare(hi, other.hi) == 0;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return "{low: " + low + (hi != null ? (", hi: " + hi) : "") + "}";
    }

    @JsonIgnore
    public int getLower() {
        return low;
    }

    @JsonIgnore
    public int getUpper() {
        return (hi != null && hi != 0) ? hi : low + 1;
    }

    protected boolean tryExtending(int h) {
        boolean done = false;
        if (h == low) {
            done = true;
        } else if (hi != null && hi != 0) {
            if (h == hi) {
                hi ++;
                done = true;
            }
        } else if (h == low + 1) {
            hi = h + 1;
            done = true;
        }
        return done;
    }

    // If <b>hi</b> is meaningless or invalid, remove it.
    protected void normalize() {
        if (hi != null && hi <= low + 1) {
            hi = null;
        }
    }

    /**
     * Convert List of IDs to multiple ranges.
     */
    public static MsgRange[] toRanges(@Nullable final List<Integer> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        // Make sure the IDs are sorted in ascending order.
        Collections.sort(list);

        ArrayList<MsgRange> ranges = new ArrayList<>();
        MsgRange curr = new MsgRange(list.get(0));
        ranges.add(curr);
        for (int i = 1; i < list.size(); i++) {
            if (!curr.tryExtending(list.get(i))) {
                curr.normalize();
                // Start a new range
                curr = new MsgRange(list.get(i));
                ranges.add(curr);
            }
        }

        // No need to sort the ranges. They are already sorted.

        return ranges.toArray(new MsgRange[]{});
    }

    /**
     * Convert array of IDs to multiple ranges.
     */
    public static @Nullable MsgRange[] toRanges(final int[] list) {
        if (list == null || list.length == 0) {
            return null;
        }

        // Make sure the IDs are sorted in ascending order.
        Arrays.sort(list);

        ArrayList<MsgRange> ranges = new ArrayList<>();
        MsgRange curr = new MsgRange(list[0]);
        ranges.add(curr);
        for (int i = 1; i < list.length; i++) {
            if (!curr.tryExtending(list[i])) {
                curr.normalize();
                // Start a new range
                curr = new MsgRange(list[i]);
                ranges.add(curr);
            }
        }

        // No need to sort the ranges. They are already sorted.

        return ranges.toArray(new MsgRange[0]);
    }

    /**
     * Collapse multiple possibly overlapping ranges into as few ranges non-overlapping
     * ranges as possible: [1..6],[2..4],[5..7] -> [1..7].
     * The input array of ranges must be sorted.
     *
     * @param ranges ranges to collapse
     * @return non-overlapping ranges.
     */
    public static @NotNull MsgRange[] collapse(@NotNull MsgRange[] ranges) {
        if (ranges.length < 2) {
            return ranges;
        }

        int prev = 0;
        for (int i = 1; i < ranges.length; i++) {
            if (ranges[prev].low == ranges[i].low) {
                // Same starting point.

                // Earlier range is guaranteed to be wider or equal to the later range,
                // collapse two ranges into one (by doing nothing)
                continue;
            }

            // Check for full or partial overlap
            int prev_hi = ranges[prev].getUpper();
            if (prev_hi >= ranges[i].low) {
                // Partial overlap: previous hi is above or equal to current low.
                int curr_hi = ranges[i].getUpper();
                if (curr_hi > prev_hi) {
                    // Current range extends further than previous, extend previous.
                    ranges[prev].hi = curr_hi;
                }
                // Otherwise the next range is fully within the previous range, consume it by doing nothing.
                continue;
            }

            // No overlap. Just copy the values.
            prev ++;
            ranges[prev] = ranges[i];
        }

        // Clip array.
        if (prev + 1 < ranges.length) {
            ranges = Arrays.copyOfRange(ranges, 0, prev + 1);
        }

        return ranges;
    }

    /**
     * Get maximum enclosing range. The input array must be sorted.
     */
    public static @Nullable MsgRange enclosing(final @NotNull MsgRange[] ranges) {
        if (ranges == null || ranges.length == 0) {
            return null;
        }

        MsgRange first = new MsgRange(ranges[0]);
        if (ranges.length > 1) {
            MsgRange last = ranges[ranges.length - 1];
            first.hi = last.getUpper();
        } else if (first.hi == null) {
            first.hi = first.low + 1;
        }

        return first;
    }

    /**
     * Find gaps in the given array of non-overlapping ranges. The input must be sorted and overlaps removed.
     */
    public static @NotNull MsgRange[] gaps(@NotNull MsgRange[] ranges) {
        if (ranges.length < 2) {
            return new MsgRange[0];
        }

        List<MsgRange> gaps = new LinkedList<>();
        for (int i = 1; i < ranges.length; i++) {
            if (ranges[i-1].getUpper() < ranges[i].getLower()) {
                // Gap found
                gaps.add(new MsgRange(ranges[i-1].getUpper(), ranges[i].getLower()));
            }
        }

        return gaps.toArray(new MsgRange[0]);
    }

    // Comparable which does not crash on null values. Nulls are treated as 0.
    private static int nullableCompare(Integer x, Integer y) {
        return (x == null ? 0 : x) - (y == null ? 0 : y);
    }

    /**
     * Cut 'clip' range out of the 'src' range.
     *
     * @param src source range to subtract from.
     * @param clip range to subtract.
     * @return array with 0, 1 or 2 elements.
     */
    public static @NotNull MsgRange[] clip(@NotNull MsgRange src, @NotNull MsgRange clip) {
        if (clip.getUpper() < src.getLower() || clip.getLower() >= src.getUpper()) {
            // Clip is completely outside of src, no intersection.
            return new MsgRange[]{src};
        }

        if (clip.low <= src.low) {
            if (clip.getUpper() >= src.getUpper()) {
                // The source range is completely inside the clipping range.
                return new MsgRange[0];
            }
            // Partial clipping at the top.
            return new MsgRange[]{new MsgRange(src.getLower(), clip.getUpper())};
        }

        // Range on the lower end.
        MsgRange lower = new MsgRange(src.getLower(), clip.getLower());
        if (clip.getUpper() < src.getUpper()) {
            return new MsgRange[]{lower, new MsgRange(clip.getUpper(), src.getUpper())};
        }
        return new MsgRange[]{lower};
    }
}
