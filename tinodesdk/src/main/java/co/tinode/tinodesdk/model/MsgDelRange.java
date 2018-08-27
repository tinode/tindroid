package co.tinode.tinodesdk.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MsgDelRange is either an individual ID (hi=0 || hi==null) or a range of deleted IDs, low end inclusive (closed),
 * high-end exclusive (open): [low .. hi), e.g. 1..5 &rarr; 1, 2, 3, 4
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

    public static MsgDelRange[] listToRanges(final List<Integer> list) {
        if (list == null || list.size() == 0) {
            return null;
        }

        // Make sure the IDs are sorted in ascending order.
        Collections.sort(list);

        ArrayList<MsgDelRange> ranges = new ArrayList<>();
        MsgDelRange curr = new MsgDelRange(list.get(0));
        ranges.add(curr);
        for (int i = 1; i < list.size(); i++) {
            if (!curr.tryExtending(list.get(i))) {
                curr.normalize();
                // Start a new range
                curr = new MsgDelRange(list.get(i));
                ranges.add(curr);
            }
        }

        return ranges.toArray(new MsgDelRange[]{});
    }
}
