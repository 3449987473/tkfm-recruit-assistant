package com.tkfm.recruitassistant.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MatchGroupTest {

    private static Character character(int no, int star, String name) {
        return new Character(no, star, name, "", Collections.emptyList(), null);
    }

    private static MatchGroup group(List<Character> characters) {
        return new MatchGroup(Collections.singletonList("光属性"), characters, 0);
    }

    @Test
    public void uniqueSsrUsesTierAndName() {
        assertEquals("已按「必出 SSR：测试SSR」组合招募",
                group(Arrays.asList(character(1, 3, "测试SSR"))).resultSummary());
    }

    @Test
    public void uniqueSrUsesTierAndName() {
        assertEquals("已按「必出 SR：测试SR」组合招募",
                group(Arrays.asList(character(2, 2, "测试SR"))).resultSummary());
    }

    @Test
    public void uniqueOneStarUsesStarText() {
        assertEquals("已按「必出 1★：测试一星」组合招募",
                group(Arrays.asList(character(3, 1, "测试一星"))).resultSummary());
    }

    @Test
    public void uniqueZeroStarUsesStarText() {
        assertEquals("已按「必出 0★：测试零星」组合招募",
                group(Arrays.asList(character(4, 0, "测试零星"))).resultSummary());
    }

    @Test
    public void multipleAllSrIsRecommended() {
        MatchGroup g = group(Arrays.asList(character(5, 2, "A"), character(6, 3, "B")));
        assertEquals("已按「全 SR 以上，2 名候选」组合招募", g.resultSummary());
    }

    @Test
    public void multipleWithLowRarityReportsMaxStar() {
        MatchGroup g = group(Arrays.asList(character(7, 1, "A"), character(8, 2, "B"),
                character(9, 0, "C")));
        assertEquals("已按「最高 2★，3 名候选」组合招募", g.resultSummary());
    }

    @Test
    public void emptyGroupSaysNoMatch() {
        assertEquals("没有匹配角色", group(Collections.emptyList()).resultSummary());
    }
}
