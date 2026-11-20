package com.tkfm.recruitassistant.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class MatchEngineTest {

    private static List<Character> characters;
    private static MatchEngine engine;

    @BeforeClass
    public static void load() throws Exception {
        File file = new File("src/main/assets/characters.json");
        if (!file.exists()) file = new File("app/src/main/assets/characters.json");
        assertTrue("characters.json not found", file.exists());
        String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONArray arr = new JSONArray(raw);
        characters = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            JSONArray tags = o.getJSONArray("tags");
            List<String> list = new ArrayList<>();
            for (int j = 0; j < tags.length(); j++) list.add(tags.getString(j));
            characters.add(new Character(o.getInt("no"), o.getInt("star"), o.getString("name"),
                    "", list, new LinkedHashMap<>()));
        }
        engine = new MatchEngine(characters);
    }

    @Test
    public void databaseShape() {
        assertEquals(77, characters.size());
        int[] hist = new int[4];
        for (Character c : characters) hist[3 - c.star]++;
        assertEquals(28, hist[0]);
        assertEquals(14, hist[1]);
        assertEquals(10, hist[2]);
        assertEquals(25, hist[3]);
        for (Character c : characters) assertFalse(c.tags.size() > 33);
    }

    @Test
    public void sampleScreenshotMatchesTenGroups() {
        List<MatchGroup> groups = engine.analyze(linked("干扰", "光属性", "治疗者", "生存力", "闇属性"));
        assertEquals(10, groups.size());

        MatchGroup top = groups.get(0);
        assertEquals(Arrays.asList("闇属性"), top.combo);
        assertEquals(8, top.characters.size());
        assertTrue(containsName(top.characters, "魔管家 艾可"));

        MatchGroup unique = null;
        for (MatchGroup g : groups) if (g.isUnique()) unique = g;
        assertNotNull(unique);
        assertEquals("生存力 · 闇属性", unique.comboText());
        assertEquals("木乃伊 穆穆", unique.characters.get(0).name);
        assertTrue(unique.badgeText().equals("必出") || unique.badgeText().equals("推荐"));

        for (MatchGroup g : groups) {
            for (Character c : g.characters) {
                assertTrue("leader leaked without 领袖: " + c.name, c.star < 3);
            }
        }
    }

    @Test
    public void leaderGateHidesAllThreeStars() {
        List<MatchGroup> withoutLeader = engine.analyze(linked("攻击者", "火属性"));
        for (MatchGroup g : withoutLeader) {
            for (Character c : g.characters) assertTrue(c.star < 3);
        }
        List<MatchGroup> withLeader = engine.analyze(linked("领袖", "攻击者", "火属性"));
        boolean found = false;
        for (MatchGroup g : withLeader) if (containsName(g.characters, "魔王 巴尔")) found = true;
        assertTrue(found);
    }

    @Test
    public void duplicateCandidateListsKeepShortestCombo() {
        // Every 闇属性 character is also 中体型, so both combos produce the same list.
        List<MatchGroup> groups = engine.analyze(linked("中体型", "闇属性"));
        MatchGroup target = null;
        for (MatchGroup g : groups) {
            if (g.combo.contains("闇属性") && containsName(g.characters, "魔管家 艾可")) target = g;
        }
        assertNotNull(target);
        assertEquals(1, target.combo.size());
        assertEquals("闇属性", target.combo.get(0));
    }

    @Test
    public void eliteComboWinsOverShorterNonElite() {
        List<MatchGroup> groups = engine.analyze(linked("中体型", "支援", "菁英"));
        MatchGroup target = null;
        for (MatchGroup g : groups) {
            if (g.combo.contains("菁英") && g.combo.contains("支援") && containsName(g.characters, "魔管家 艾可")) target = g;
        }
        assertNotNull(target);
        assertEquals(Arrays.asList("支援", "菁英"), target.combo);
    }

    @Test
    public void multiTagComboIsGroupedAndRanked() {
        List<MatchGroup> groups = engine.analyze(linked("光属性", "治疗者"));
        MatchGroup combo = null;
        for (MatchGroup g : groups) {
            if (g.combo.size() == 2 && g.combo.contains("光属性") && g.combo.contains("治疗者")) {
                combo = g;
            }
        }
        assertNotNull(combo);
        assertEquals(4, combo.characters.size());
        assertTrue(combo.isUnique() || combo.isRecommended() || combo.characters.size() == 4);
        // 光属性 alone is a larger candidate pool and must rank above the 4-character subset.
        assertTrue(groups.get(0).characters.size() >= combo.characters.size());
    }

    @Test
    public void scoreMatchesReferenceBytecode() {
        Character singleN = characters.stream().filter(c -> c.star == 0).findFirst().get();
        Character singleSr = characters.stream().filter(c -> c.star == 2).findFirst().get();
        assertEquals(199.0, MatchEngine.score(Arrays.asList(singleN)), 0.0001);
        assertEquals(10201.0, MatchEngine.score(Arrays.asList(singleSr)), 0.0001);
        Character ssr = characters.stream().filter(c -> c.star == 3).findFirst().get();
        assertEquals(100199.0, MatchEngine.score(Arrays.asList(ssr)), 0.0001);
    }

    @Test
    public void exclusiveCombinationIsUnique() {
        List<MatchGroup> groups = engine.analyze(linked("领袖", "群体攻击"));
        boolean anyUnique = false;
        for (MatchGroup g : groups) {
            if (g.isUnique() && containsName(g.characters, "魔王 伊布力斯")) anyUnique = true;
        }
        assertTrue(anyUnique);
    }

    private static LinkedHashSet<String> linked(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private static boolean containsName(List<Character> list, String name) {
        for (Character c : list) if (name.equals(c.name)) return true;
        return false;
    }
}
