package com.tkfm.recruitassistant.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Faithful reimplementation of the reference module's matching algorithm.
 *
 * <ol>
 *   <li>Enumerate every subset of the selected tags with size 1..3.</li>
 *   <li>Keep characters containing the whole subset; 3★ leaders only appear when
 *       the subset itself contains 领袖 (the "leader gate").</li>
 *   <li>Collapse combinations that produce the identical character list, keeping the
 *       shortest combination (elite combinations win ties).</li>
 *   <li>Rank groups with the reference scoring formula.</li>
 * </ol>
 */
public final class MatchEngine {

    private final List<Character> characters;

    public MatchEngine(CharacterRepository repository) {
        this(repository.all());
    }

    public MatchEngine(List<Character> characters) {
        this.characters = characters;
    }

    public List<MatchGroup> analyze(Set<String> selected) {
        if (selected == null || selected.isEmpty()) return Collections.emptyList();
        List<String> tagList = new ArrayList<>(new LinkedHashSet<>(selected));
        List<List<String>> combos = new ArrayList<>();
        for (int size = 1; size <= Math.min(3, tagList.size()); size++) {
            combine(tagList, size, 0, new ArrayList<>(), combos);
        }

        Map<String, GroupBucket> buckets = new LinkedHashMap<>();
        for (List<String> combo : combos) {
            List<Character> candidates = findCandidates(combo);
            if (candidates.isEmpty()) continue;
            String key = keyOf(candidates);
            GroupBucket bucket = buckets.get(key);
            if (bucket == null) {
                buckets.put(key, new GroupBucket(new ArrayList<>(combo), candidates));
            } else {
                bucket.offer(combo);
            }
        }

        List<MatchGroup> groups = new ArrayList<>(buckets.size());
        for (GroupBucket bucket : buckets.values()) {
            groups.add(new MatchGroup(Collections.unmodifiableList(bucket.combo),
                    Collections.unmodifiableList(bucket.characters), score(bucket.characters)));
        }
        groups.sort(GROUP_ORDER);
        return groups;
    }

    private List<Character> findCandidates(List<String> combo) {
        List<Character> out = new ArrayList<>();
        boolean leaderGate = combo.contains(TagCatalog.LEADER);
        for (Character c : characters) {
            if (!leaderGate && c.isLeader()) continue;
            if (c.tags.containsAll(combo)) out.add(c);
        }
        return out;
    }

    private static String keyOf(List<Character> list) {
        StringBuilder sb = new StringBuilder(list.size() * 4);
        for (Character c : list) sb.append(c.no).append(',');
        return sb.toString();
    }

    private static void combine(List<String> source, int size, int start, List<String> current,
                                List<List<String>> out) {
        if (current.size() == size) {
            out.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < source.size(); i++) {
            current.add(source.get(i));
            combine(source, size, i + 1, current, out);
            current.remove(current.size() - 1);
        }
    }

    /**
     * Verbatim score reconstructed from the reference bytecode:
     * SSR pool +100000 / SR pool +10000 / R pool +1000, unique +200,
     * multi-SR+ +100, then SR ratio (or -candidate count when none).
     */
    static double score(List<Character> characters) {
        boolean ssr = false, sr = false, r = false, allHigh = true;
        int srCount = 0;
        for (Character c : characters) {
            if (c.star == 3) ssr = true;
            if (c.star == 2) { sr = true; srCount++; }
            if (c.star == 1) r = true;
            if (c.star < 2) allHigh = false;
        }
        double score = 0;
        if (ssr) score += 100000;
        else if (sr) score += 10000;
        else if (r) score += 1000;

        if (characters.size() == 1) score += 200;
        else if (allHigh) score += 100;

        if (srCount == 0) score -= characters.size();
        else score += (double) srCount / characters.size();
        return score;
    }

    private static final Comparator<MatchGroup> GROUP_ORDER = (a, b) -> {
        int cmp = Double.compare(b.score, a.score);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(b.maxStar(), a.maxStar());
        if (cmp != 0) return cmp;
        cmp = Integer.compare(a.characters.size(), b.characters.size());
        if (cmp != 0) return cmp;
        cmp = Integer.compare(b.combo.size(), a.combo.size());
        if (cmp != 0) return cmp;
        return a.comboText().compareTo(b.comboText());
    };

    /**
     * Most useful combination for a character: a guaranteed (single-candidate) group wins,
     * otherwise the highest-ranked group that contains the character.
     */
    public static MatchGroup bestGroupFor(List<MatchGroup> groups, Character character) {
        for (MatchGroup g : groups) {
            if (g.isUnique() && g.characters.contains(character)) return g;
        }
        for (MatchGroup g : groups) {
            if (g.characters.contains(character)) return g;
        }
        return null;
    }

    public static List<String> bestComboFor(List<MatchGroup> groups, Character character) {
        MatchGroup group = bestGroupFor(groups, character);
        return group == null ? null : group.combo;
    }

    private static final class GroupBucket {
        final List<String> combo;
        final List<Character> characters;

        GroupBucket(List<String> combo, List<Character> characters) {
            this.combo = combo;
            this.characters = characters;
        }

        void offer(List<String> other) {
            boolean currentElite = combo.contains(TagCatalog.ELITE);
            boolean otherElite = other.contains(TagCatalog.ELITE);
            if (otherElite && !currentElite) {
                combo.clear();
                combo.addAll(other);
                return;
            }
            if (otherElite == currentElite && other.size() < combo.size()) {
                combo.clear();
                combo.addAll(other);
            }
        }
    }
}
