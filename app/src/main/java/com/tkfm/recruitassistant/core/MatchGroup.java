package com.tkfm.recruitassistant.core;

import java.util.List;

/** A tag combination and the characters it can produce. */
public final class MatchGroup {
    public final List<String> combo;
    public final List<Character> characters;
    public final double score;

    public MatchGroup(List<String> combo, List<Character> characters, double score) {
        this.combo = combo;
        this.characters = characters;
        this.score = score;
    }

    /** Only one possible character: the game guarantees that character for this combo. */
    public boolean isUnique() {
        return characters.size() == 1;
    }

    /** All candidates are SR or above and there are several of them: guaranteed SR+. */
    public boolean isRecommended() {
        if (characters.size() <= 1) return false;
        for (Character c : characters) {
            if (c.star < 2) return false;
        }
        return true;
    }

    public boolean containsSsr() {
        for (Character c : characters) if (c.star == 3) return true;
        return false;
    }

    public int maxStar() {
        int max = 0;
        for (Character c : characters) max = Math.max(max, c.star);
        return max;
    }

    public int countStar(int star) {
        int n = 0;
        for (Character c : characters) if (c.star == star) n++;
        return n;
    }

    public String comboText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < combo.size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(combo.get(i));
        }
        return sb.toString();
    }

    public String badgeText() {
        if (isUnique()) return "必出";
        if (isRecommended()) return "推荐";
        return characters.size() + " 名候选";
    }
}
