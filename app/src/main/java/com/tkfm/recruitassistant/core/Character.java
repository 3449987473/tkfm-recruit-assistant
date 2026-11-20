package com.tkfm.recruitassistant.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One recruitable character from the reference database (77 entries). */
public final class Character {
    public final int no;
    public final int star;
    public final String name;
    public final String code;
    public final List<String> tags;
    public final Map<String, String> notes;

    public Character(int no, int star, String name, String code, List<String> tags,
                     Map<String, String> notes) {
        this.no = no;
        this.star = star;
        this.name = name;
        this.code = code;
        this.tags = Collections.unmodifiableList(tags);
        this.notes = Collections.unmodifiableMap(notes == null ? new LinkedHashMap<>() : notes);
    }

    public boolean isLeader() {
        return tags.contains(TagCatalog.LEADER);
    }

    public boolean isElite() {
        return tags.contains(TagCatalog.ELITE);
    }

    /** 3★ -> SSR, 2★ -> SR, 1★ -> R, 0★ -> N (reference rarity model). */
    public String rarity() {
        switch (star) {
            case 3: return "SSR";
            case 2: return "SR";
            case 1: return "R";
            default: return "N";
        }
    }

    public String starText() {
        return star + "★";
    }

    public String displayName() {
        return star + "★ " + name;
    }

    @Override public String toString() {
        return displayName();
    }
}
