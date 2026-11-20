package com.tkfm.recruitassistant.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AutoSelectPlannerTest {

    private static AutoSelectPlanner.TagHit hit(String tag, int left, int top, int right, int bottom) {
        return new AutoSelectPlanner.TagHit(tag, new AutoSelectPlanner.Box(left, top, right, bottom));
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    @Test
    public void selectsMissingAndDeselectsExtra() {
        List<AutoSelectPlanner.TagHit> hits = Arrays.asList(
                hit("攻击者", 10, 100, 110, 140),
                hit("火属性", 130, 100, 230, 140),
                hit("魔族", 250, 100, 350, 140));
        AutoSelectPlanner.Box time = new AutoSelectPlanner.Box(100, 300, 160, 340);

        AutoSelectPlanner.Plan plan = AutoSelectPlanner.build(
                Arrays.asList("攻击者", "火属性"), hits, set("火属性", "魔族"), 5, time);

        // 05:00 -> 09:00 is four taps on the up arrow.
        assertEquals(6, plan.taps.size());
        assertEquals("time+", plan.taps.get(0).label);
        assertEquals(130, plan.taps.get(0).x);
        assertEquals(320 - 80, plan.taps.get(0).y);
        assertEquals("time+", plan.taps.get(3).label);
        assertEquals("on:攻击者", plan.taps.get(4).label);
        assertEquals("off:魔族", plan.taps.get(5).label);
        assertTrue(plan.timeAdjusted);
        assertTrue(plan.missing.isEmpty());
    }

    @Test
    public void usesDownArrowForShortHours() {
        AutoSelectPlanner.Box time = new AutoSelectPlanner.Box(100, 300, 160, 340);
        AutoSelectPlanner.Plan plan = AutoSelectPlanner.build(
                Arrays.asList("攻击者"),
                Arrays.asList(hit("攻击者", 0, 0, 20, 20)),
                set(),
                2,
                time);

        // 02:00 -> 01:00 -> 09:00 is two taps on the down arrow.
        assertEquals(3, plan.taps.size());
        assertEquals("time-", plan.taps.get(0).label);
        assertEquals("time-", plan.taps.get(1).label);
        assertEquals(320 + 80, plan.taps.get(0).y);
        assertEquals("on:攻击者", plan.taps.get(2).label);
        assertEquals(9, plan.targetHour);
    }

    @Test
    public void hourOneWrapsWithOneDownTap() {
        AutoSelectPlanner.Box time = new AutoSelectPlanner.Box(100, 300, 160, 340);
        AutoSelectPlanner.Plan plan = AutoSelectPlanner.build(
                Arrays.asList("攻击者"),
                Arrays.asList(hit("攻击者", 0, 0, 20, 20)),
                set(),
                1,
                time);
        assertEquals(2, plan.taps.size());
        assertEquals("time-", plan.taps.get(0).label);
    }

    @Test
    public void skipsTimeWhenAlreadyAtMax() {
        AutoSelectPlanner.Plan plan = AutoSelectPlanner.build(
                Arrays.asList("攻击者"),
                Arrays.asList(hit("攻击者", 0, 0, 20, 20)),
                set(),
                9,
                new AutoSelectPlanner.Box(0, 0, 20, 20));
        assertEquals(1, plan.taps.size());
        assertFalse(plan.timeAdjusted);
    }

    @Test
    public void reportsMissingTag() {
        AutoSelectPlanner.Plan plan = AutoSelectPlanner.build(
                Arrays.asList("攻击者", "领袖"),
                Arrays.asList(hit("攻击者", 0, 0, 20, 20)),
                set(),
                null,
                null);
        assertEquals(1, plan.taps.size());
        assertEquals(Arrays.asList("领袖"), plan.missing);
    }

    @Test
    public void flagsCanDisableTimeAndTags() {
        List<AutoSelectPlanner.TagHit> hits = Arrays.asList(
                hit("攻击者", 10, 100, 110, 140));
        AutoSelectPlanner.Box time = new AutoSelectPlanner.Box(100, 300, 160, 340);

        AutoSelectPlanner.Plan none = AutoSelectPlanner.build(
                Arrays.asList("攻击者"), hits, set(), 5, time, false, false);
        assertTrue(none.taps.isEmpty());
        assertFalse(none.timeAdjusted);

        AutoSelectPlanner.Plan timeOnly = AutoSelectPlanner.build(
                Arrays.asList("攻击者"), hits, set(), 5, time, true, false);
        assertEquals(4, timeOnly.taps.size());
        assertTrue(timeOnly.timeAdjusted);

        AutoSelectPlanner.Plan tagsOnly = AutoSelectPlanner.build(
                Arrays.asList("攻击者"), hits, set(), 5, time, false, true);
        assertEquals(1, tagsOnly.taps.size());
        assertEquals("on:攻击者", tagsOnly.taps.get(0).label);
        assertFalse(tagsOnly.timeAdjusted);
    }
}
