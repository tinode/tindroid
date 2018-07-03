package co.tinode.tinodesdk.model;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * MsgDelRange is either an individual ID (hi=0 || hi==null) or a range of deleted IDs, low end inclusive (closed),
 * high-end exclusive (open): [low .. hi), e.g. 1..5 -> 1, 2, 3, 4
 */
@SuppressWarnings("WeakerAccess")
public class MsgDelRange {
    public Integer low;
    public Integer hi;

    public MsgDelRange() {
        low = 0;
    }

    public MsgDelRange(int id) {
        low = id;
    }

    public MsgDelRange(int low, int hi) {
        this.low = low;
        this.hi = hi;
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

    public static MsgDelRange[] arrayToRanges(int[] list) {
        if (list == null || list.length == 0) {
            return null;
        }

        // Make sure the IDs are sorted in ascending order.
        Arrays.sort(list);

        ArrayList<MsgDelRange> ranges = new ArrayList<>();
        MsgDelRange curr = new MsgDelRange(list[0]);
        ranges.add(curr);
        for (int i = 1; i < list.length; i++) {
            if (!curr.tryExtending(list[i])) {
                curr.normalize();
                // Start a new range
                curr = new MsgDelRange(list[i]);
                ranges.add(curr);
            }
        }

        return ranges.toArray(new MsgDelRange[]{});
    }
}
