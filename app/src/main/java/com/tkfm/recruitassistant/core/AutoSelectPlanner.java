package com.tkfm.recruitassistant.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the tap sequence that turns the current in-game selection into the requested
 * recruit conditions.  Kept free of Android types so the sequence can be unit tested.
 */
public final class AutoSelectPlanner {

    /** Bounding box in screen pixels. */
    public static final class Box {
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        public Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int centerX() {
            return (left + right) / 2;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }

        public int height() {
            return Math.max(0, bottom - top);
        }
    }

    public static final class TagHit {
        public final String tag;
        public final Box box;

        public TagHit(String tag, Box box) {
            this.tag = tag;
            this.box = box;
        }
    }

    public static final class Tap {
        public final int x;
        public final int y;
        public final String label;

        public Tap(int x, int y, String label) {
            this.x = x;
            this.y = y;
            this.label = label;
        }

        @Override
        public String toString() {
            return label + "@" + x + "," + y;
        }
    }

    public static final class Plan {
        public final List<Tap> taps = new ArrayList<>();
        public final List<String> missing = new ArrayList<>();
        public boolean timeAdjusted;
        public int targetHour;

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("taps=").append(taps.size());
            if (timeAdjusted) sb.append(" time=").append(targetHour);
            if (!missing.isEmpty()) sb.append(" missing=").append(missing);
            return sb.toString();
        }
    }

    private AutoSelectPlanner() {
    }

    /**
     * @param desired  tags of the chosen result group
     * @param hits     every tag currently visible on screen, with its OCR box
     * @param selected tags that the pixel probe currently considers selected
     * @param hour     hour read from the recruit-time control, or null when not found
     * @param timeBox  OCR box of that hour, or null
     */
    public static Plan build(List<String> desired, List<TagHit> hits, Set<String> selected,
                             Integer hour, Box timeBox) {
        return build(desired, hits, selected, hour, timeBox, true, true);
    }

    public static Plan build(List<String> desired, List<TagHit> hits, Set<String> selected,
                             Integer hour, Box timeBox, boolean adjustTime,
                             boolean toggleTags) {
        Plan plan = new Plan();
        Set<String> wanted = new LinkedHashSet<>(desired);
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        // 1. Move the recruit time to 09:00 using the shorter direction.
        //    Hour range is 01..09; down from 01 wraps to 09.
        //    h < 5  -> down h times (01 -> 09 is one tap)
        //    h >= 5 -> up (9-h) times
        if (adjustTime && hour != null && timeBox != null
                && hour >= 1 && hour <= 9 && hour != 9) {
            int x = timeBox.centerX();
            int offset = Math.max(timeBox.height() * 2, 40);
            if (hour < 5) {
                int y = timeBox.centerY() + offset;
                for (int i = 0; i < hour; i++) {
                    plan.taps.add(new Tap(x, y, "time-"));
                }
            } else {
                int y = timeBox.centerY() - offset;
                int steps = 9 - hour;
                for (int i = 0; i < steps; i++) {
                    plan.taps.add(new Tap(x, y, "time+"));
                }
            }
            plan.timeAdjusted = true;
            plan.targetHour = 9;
        }

        if (toggleTags) {
            // 2. Select every missing tag.
            for (String tag : wanted) {
                if (!seen.add(tag)) continue;
                if (selected.contains(tag)) continue;
                TagHit hit = find(hits, tag);
                if (hit == null) {
                    plan.missing.add(tag);
                } else {
                    plan.taps.add(new Tap(hit.box.centerX(), hit.box.centerY(), "on:" + tag));
                }
            }

            // 3. Deselect tags that are not part of the group.
            for (String tag : selected) {
                if (wanted.contains(tag)) continue;
                TagHit hit = find(hits, tag);
                if (hit != null) {
                    plan.taps.add(new Tap(hit.box.centerX(), hit.box.centerY(), "off:" + tag));
                }
            }
        }
        return plan;
    }

    private static TagHit find(List<TagHit> hits, String tag) {
        for (TagHit hit : hits) {
            if (hit.tag.equals(tag)) return hit;
        }
        return null;
    }
}
