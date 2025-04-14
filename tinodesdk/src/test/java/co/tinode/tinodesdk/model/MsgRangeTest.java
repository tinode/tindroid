package co.tinode.tinodesdk.model;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class MsgRangeTest {
    @Test
    public void testRange() {
        MsgRange r1 = new MsgRange(1, 5);
        MsgRange r2 = new MsgRange(1, 5);
        MsgRange r3 = new MsgRange(2, 4);
        MsgRange r4 = new MsgRange(3, 6);

        assertEquals(r1, r2);
        assertNotEquals(r1, r3);
        assertNotEquals(r1, r4);
        assertNotEquals(r3, r4);
    }

    @Test
    public void testRangeEnclosing() {
        MsgRange r1 = new MsgRange(1, 5);
        MsgRange r2 = new MsgRange(4, 8);
        MsgRange r3 = new MsgRange(6, 10);

        MsgRange e1 = MsgRange.enclosing(new MsgRange[]{r1, r2, r3});
        MsgRange e2 = MsgRange.enclosing(new MsgRange[]{r1, r3});
        MsgRange e3 = MsgRange.enclosing(new MsgRange[]{r2, r3});

        assertEquals(new MsgRange(1, 10), e1);
        assertEquals(new MsgRange(1, 10), e2);
        assertEquals(new MsgRange(4, 10), e3);
    }

    @Test
    public void testRangeNormalize() {
        MsgRange r1 = new MsgRange(1, 5);
        MsgRange r2 = new MsgRange(3, 3);
        MsgRange r3 = new MsgRange(3, -50);

        r1.normalize();
        r2.normalize();
        r3.normalize();

        assertEquals(new MsgRange(1, 5), r1);
        assertEquals(new MsgRange(3), r2);
        assertEquals(new MsgRange(3), r3);
    }

    @Test
    public void testRangeToString() {
        MsgRange r1 = new MsgRange(1, 5);
        assertEquals("{low: 1, hi: 5}", r1.toString());

        MsgRange r2 = new MsgRange(3);
        assertEquals("{low: 3}", r2.toString());
    }

    @Test
    public void testRangeToRanges() {
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(5);
        list.add(6);

        MsgRange[] ranges = MsgRange.toRanges(list);
        assertEquals(2, ranges.length);
        assertEquals(new MsgRange(1, 4), ranges[0]);
        assertEquals(new MsgRange(5, 7), ranges[1]);
    }

    @Test
    public void testRangeToRangesEmpty() {
        List<Integer> list = new ArrayList<>();
        MsgRange[] ranges = MsgRange.toRanges(list);
        assertNull(ranges);

        int[] arr = new int[0];
        ranges = MsgRange.toRanges(arr);
        assertNull(ranges);
    }

    @Test
    public void testRangeToRangesSingle() {
        List<Integer> list = new ArrayList<>();
        list.add(1);

        MsgRange[] ranges = MsgRange.toRanges(list);
        assertEquals(1, ranges.length);
        assertEquals(new MsgRange(1), ranges[0]);

        int[] arr = new int[]{1};
        ranges = MsgRange.toRanges(arr);
        assertEquals(1, ranges.length);
        assertEquals(new MsgRange(1), ranges[0]);
    }

    @Test
    public void testTryExtending() {
        MsgRange r1 = new MsgRange(1, 5);
        assertTrue(r1.tryExtending(5));
        assertEquals(new MsgRange(1, 6), r1);

        MsgRange r2 = new MsgRange(1, 5);
        assertFalse(r2.tryExtending(0));
        assertEquals(new MsgRange(1, 5), r2);

        MsgRange r3 = new MsgRange(1, 5);
        assertFalse(r3.tryExtending(6));
        assertEquals(new MsgRange(1, 5), r3);
    }

    @Test
    public void testCollapse() {
        MsgRange r1 = new MsgRange(1, 5);
        MsgRange r2 = new MsgRange(3, 7);
        MsgRange r3 = new MsgRange(6, 10);

        List<MsgRange> ranges = new ArrayList<>();
        ranges.add(r1);
        ranges.add(r2);
        ranges.add(r3);

        MsgRange[] collapsed = MsgRange.collapse(ranges.toArray(new MsgRange[]{}));
        assertEquals(1, collapsed.length);
        assertEquals(new MsgRange(1, 10), collapsed[0]);
    }

    @Test
    public void testCollapseEmpty() {
        MsgRange[] ranges = new MsgRange[]{};
        MsgRange[] collapsed = MsgRange.collapse(ranges);
        assertEquals(0, collapsed.length);
    }

    @Test
    public void testCollapseSingle() {
        MsgRange r1 = new MsgRange(1, 5);
        List<MsgRange> ranges = new ArrayList<>();
        ranges.add(r1);

        MsgRange[] collapsed = MsgRange.collapse(ranges.toArray(new MsgRange[]{}));
        assertEquals(1, collapsed.length);
        assertEquals(new MsgRange(1, 5), collapsed[0]);
    }

    @Test
    public void testCollapseNonOverlapping() {
        MsgRange r1 = new MsgRange(1, 5);
        MsgRange r2 = new MsgRange(7, 10);
        MsgRange r3 = new MsgRange(11, 15);

        List<MsgRange> ranges = new ArrayList<>();
        ranges.add(r1);
        ranges.add(r2);
        ranges.add(r3);

        MsgRange[] collapsed = MsgRange.collapse(ranges.toArray(new MsgRange[]{}));
        assertEquals(3, collapsed.length);
        assertEquals(new MsgRange(1, 5), collapsed[0]);
        assertEquals(new MsgRange(7, 10), collapsed[1]);
        assertEquals(new MsgRange(11, 15), collapsed[2]);
    }

    @Test
    public void testGaps() {
        MsgRange r1 = new MsgRange(1, 5);
        MsgRange r2 = new MsgRange(7, 10);
        MsgRange r3 = new MsgRange(11, 15);

        List<MsgRange> ranges = new ArrayList<>();
        ranges.add(r1);
        ranges.add(r2);
        ranges.add(r3);

        MsgRange[] gaps = MsgRange.gaps(ranges.toArray(new MsgRange[]{}));
        assertEquals(2, gaps.length);
        assertEquals(new MsgRange(5, 7), gaps[0]);
        assertEquals(new MsgRange(10, 11), gaps[1]);
    }

    @Test
    public void testClip() {
        MsgRange r1 = new MsgRange(1, 5);
        MsgRange r2 = new MsgRange(3, 7);
        MsgRange r3 = new MsgRange(1, 10);

        MsgRange[] clipped = MsgRange.clip(r1, r2);
        assertEquals(1, clipped.length);
        assertEquals(new MsgRange(1, 3), clipped[0]);

        clipped = MsgRange.clip(r1, r3);
        assertEquals(0, clipped.length);

        clipped = MsgRange.clip(r2, r1);
        assertEquals(1, clipped.length);
        assertEquals(new MsgRange(3, 5), clipped[0]);

        clipped = MsgRange.clip(r3, r2);
        assertEquals(2, clipped.length);
        assertEquals(new MsgRange(1, 3), clipped[0]);
        assertEquals(new MsgRange(7, 10), clipped[1]);
    }
}
