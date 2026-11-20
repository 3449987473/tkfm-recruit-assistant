package com.tkfm.recruitassistant.core;

import java.util.List;

/**
 * Pure decision logic for what happens after a scan.  Kept free of Android types so the
 * four workflows and the leader exception can be unit tested.
 */
public final class WorkflowPolicy {

    /** The tag that always forces manual confirmation and never auto-recruits. */
    public static final String LEADER = "领袖";

    public enum Action {
        /** Show the result sheet and wait for the user to tap a group. */
        SHOW_ONLY,
        /** Select tags/time for the best group, then offer the in-sheet recruit button. */
        AUTO_SELECT_CONFIRM,
        /** Select tags/time for the best group and click the in-game recruit button. */
        AUTO_SELECT_AND_RECRUIT
    }

    private WorkflowPolicy() {
    }

    public static boolean containsLeader(List<String> tags) {
        return tags != null && tags.contains(LEADER);
    }

    /**
     * @param autoRunOnScan scan immediately selects tags/time for the best group
     * @param autoRecruit   click the in-game recruit button after selecting
     * @param tags          the five tags recognised from the recruit panel
     */
    public static Action decide(boolean autoRunOnScan, boolean autoRecruit, List<String> tags) {
        if (!autoRunOnScan) return Action.SHOW_ONLY;
        if (containsLeader(tags)) return Action.SHOW_ONLY;
        return autoRecruit ? Action.AUTO_SELECT_AND_RECRUIT : Action.AUTO_SELECT_CONFIRM;
    }
}
