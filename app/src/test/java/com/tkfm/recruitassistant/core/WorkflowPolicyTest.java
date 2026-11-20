package com.tkfm.recruitassistant.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WorkflowPolicyTest {

    private static List<String> tags(String... extra) {
        return Arrays.asList("攻击者", "魔族", "巨乳", "中体型", "输出");
    }

    private static List<String> leaderTags() {
        List<String> tags = new java.util.ArrayList<>(tags());
        tags.set(4, "领袖");
        return tags;
    }

    /** Mode 1 and 3: a tap on a group starts the selection. */
    @Test
    public void manualScanWaitsForTap() {
        assertEquals(WorkflowPolicy.Action.SHOW_ONLY,
                WorkflowPolicy.decide(false, false, tags()));
        assertEquals(WorkflowPolicy.Action.SHOW_ONLY,
                WorkflowPolicy.decide(false, true, tags()));
    }

    /** Mode 2: auto-select on scan, recruit stays with the user. */
    @Test
    public void autoRunWithoutRecruitIsConfirmMode() {
        assertEquals(WorkflowPolicy.Action.AUTO_SELECT_CONFIRM,
                WorkflowPolicy.decide(true, false, tags()));
    }

    /** Mode 4: fully automatic. */
    @Test
    public void autoRunWithRecruitIsFullAuto() {
        assertEquals(WorkflowPolicy.Action.AUTO_SELECT_AND_RECRUIT,
                WorkflowPolicy.decide(true, true, tags()));
    }

    /** Leader always degrades modes 2 and 4 to a manual tap. */
    @Test
    public void leaderDisablesAutoRun() {
        assertEquals(WorkflowPolicy.Action.SHOW_ONLY,
                WorkflowPolicy.decide(true, false, leaderTags()));
        assertEquals(WorkflowPolicy.Action.SHOW_ONLY,
                WorkflowPolicy.decide(true, true, leaderTags()));
    }

    @Test
    public void leaderDetectionIsExact() {
        assertTrue(WorkflowPolicy.containsLeader(leaderTags()));
        assertFalse(WorkflowPolicy.containsLeader(tags()));
        assertFalse(WorkflowPolicy.containsLeader(Collections.emptyList()));
        assertFalse(WorkflowPolicy.containsLeader(null));
    }
}
