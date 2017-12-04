package co.tinode.tinodesdk.model;

import java.util.ArrayList;

/**
 * Range of message IDs
 */
public class MsgDelRange {
    public Integer low;
    public Integer hi;

    public MsgDelRange() {}

    public MsgDelRange(int id) {
        low = id;
    }

    public MsgDelRange(int low, int hi) {
        this.low = low;
        this.hi = hi;
    }

    public void extend() {
        if (hi == null) {
            hi = low + 2;
        } else {
            hi++;
        }
    }

    public static MsgDelRange[] arrayToRanges(int[] list) {
        if (list == null || list.length == 0) {
            return null;
        }

        ArrayList<MsgDelRange> ranges = new ArrayList<>();
        MsgDelRange curr = new MsgDelRange(list[0]);
        ranges.add(curr);
        for (int i = 1; i < list.length; i++) {
            if (curr.low + 1 == list[i] || curr.hi == list[i]) {
                // Extend the current range
                curr.extend();
            } else {
                // Start a new range
                curr = new MsgDelRange(list[i]);
                ranges.add(curr);
            }
        }

        return ranges.toArray(new MsgDelRange[]{});
    }
}
