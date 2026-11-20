package com.tkfm.recruitassistant.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProphecyEngineTest {

    private static final List<List<String>> RECIPES = Arrays.asList(
            Arrays.asList("攻击者", "魔族", "巨乳", "输出", "领袖"),
            Arrays.asList("攻击者", "魔族", "巨乳", "士兵", "菁英"),
            Arrays.asList("攻击者", "人类", "美乳", "回复", "支援"));

    private static ProphecyEngine engine() {
        return new ProphecyEngine(RECIPES);
    }

    @Test
    public void prefixMatchesRespectOrder() {
        assertEquals(3, engine().matches(Collections.singletonList("攻击者")).size());
        assertEquals(2, engine().matches(Arrays.asList("攻击者", "魔族")).size());
        // Order matters: 魔族 first is not a recipe prefix.
        assertTrue(engine().matches(Arrays.asList("魔族", "攻击者")).isEmpty());
    }

    /** Only one recipe continues from 攻击者/人类, so its next tag is green. */
    @Test
    public void uniqueNextTagIsGreen() {
        Map<String, Integer> hints = engine().hints(Arrays.asList("攻击者", "人类"));
        assertEquals(1, hints.size());
        assertEquals(Integer.valueOf(ProphecyEngine.HINT_GREEN), hints.get("美乳"));
    }

    /** Two recipes share the same next tag, so it is only a blue hint. */
    @Test
    public void ambiguousNextTagIsBlue() {
        Map<String, Integer> hints = engine().hints(Arrays.asList("攻击者", "魔族"));
        assertEquals(1, hints.size());
        assertEquals(Integer.valueOf(ProphecyEngine.HINT_BLUE), hints.get("巨乳"));
    }

    /** Different next tags from different recipes are each green (unique continuation). */
    @Test
    public void divergingRecipesAreGreen() {
        Map<String, Integer> hints = engine().hints(Arrays.asList("攻击者", "魔族", "巨乳"));
        assertEquals(2, hints.size());
        assertEquals(Integer.valueOf(ProphecyEngine.HINT_GREEN), hints.get("输出"));
        assertEquals(Integer.valueOf(ProphecyEngine.HINT_GREEN), hints.get("士兵"));
    }

    @Test
    public void noRecipeMeansNoHints() {
        assertTrue(engine().hints(Arrays.asList("士兵", "领袖")).isEmpty());
        assertTrue(engine().hints(Arrays.asList("攻击者", "魔族", "巨乳", "输出", "领袖"))
                .isEmpty());
    }

    @Test
    public void completionReturnsRemainingTagsOfFirstRecipe() {
        assertEquals(Collections.singletonList("美乳"),
                engine().completion(Arrays.asList("攻击者", "人类")));
        assertEquals(Arrays.asList("魔族", "巨乳"),
                engine().completion(Collections.singletonList("攻击者")));
    }

    @Test
    public void completionStopsAtThreeConditions() {
        assertTrue(engine().completion(
                Arrays.asList("攻击者", "魔族", "巨乳")).isEmpty());
    }
}
