package com.tkfm.recruitassistant.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 33 recruit tags and their OCR aliases.
 *
 * <p>The reference app receives its tag table from a remote endpoint.  The reconstructed
 * catalog below is the same 33-tag taxonomy documented for the calculator and encoded in
 * assets/tags.json, with simplified/traditional aliases added for OCR.</p>
 */
public final class TagCatalog {

    public static final String LEADER = "领袖";
    public static final String ELITE = "菁英";

    private static TagCatalog instance;

    private final List<String> groupOrder = new ArrayList<>();
    private final Map<String, List<String>> groups = new LinkedHashMap<>();
    private final List<String> allTags = new ArrayList<>();
    private final Map<String, List<String>> aliases = new LinkedHashMap<>();
    private final Map<java.lang.Character, java.lang.Character> traditionalToSimplified = new LinkedHashMap<>();

    public static synchronized TagCatalog get(Context context) {
        if (instance == null) instance = new TagCatalog(context.getApplicationContext());
        return instance;
    }

    private TagCatalog(Context context) {
        load(context);
        buildNormalization();
    }

    private void load(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("tags.json"), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject group = arr.getJSONObject(i);
                String name = group.getString("group");
                JSONArray tags = group.getJSONArray("tags");
                List<String> list = new ArrayList<>();
                for (int j = 0; j < tags.length(); j++) {
                    String tag = tags.getString(j);
                    list.add(tag);
                    allTags.add(tag);
                }
                groupOrder.add(name);
                groups.put(name, Collections.unmodifiableList(list));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load tags.json", e);
        }
    }

    private void buildNormalization() {
        putAliases("攻击者", "攻擊者", "攻击者", "攻击", "攻击考");
        putAliases("守护者", "守護者", "守护者", "守護");
        putAliases("辅助者", "輔助者", "辅助者");
        putAliases("治疗者", "治療者", "治疗者", "治療", "冶疗者", "治疗著", "洽疗者");
        putAliases("妨碍者", "妨礙者", "妨碍者", "妨礙");
        putAliases("魔族", "魔族");
        putAliases("亚人", "亞人", "亚人");
        putAliases("人类", "人類", "人类");
        putAliases("贫乳", "貧乳", "贫乳");
        putAliases("美乳", "美乳");
        putAliases("巨乳", "巨乳");
        putAliases("小体型", "小體型", "小体型", "小型体", "小休型");
        putAliases("中体型", "中體型", "中体型", "中型体", "中体休型", "中休型");
        putAliases("水属性", "水屬性", "水属性");
        putAliases("火属性", "火屬性", "火属性");
        putAliases("风属性", "風屬性", "风属性");
        putAliases("光属性", "光屬性", "光属性", "光届性", "光厲性");
        putAliases("闇属性", "闇屬性", "闇属性", "暗属性", "暗屬性", "闇厲性", "闇属牲",
                "間属性", "閨属性", "閨届性", "闇届性");
        putAliases("回复", "回復", "回复", "回復力");
        putAliases("输出", "輸出", "输出");
        putAliases("削弱", "削弱");
        putAliases("干扰", "干擾", "干扰", "干拢", "干擾力", "于扰", "于拢", "千扰", "千拢", "干忧", "于忧");
        putAliases("保护", "保護", "保护", "保拒", "保户", "保沪");
        putAliases("士兵", "士兵");
        putAliases("防御", "防禦", "防御");
        putAliases("支援", "支援", "支媛", "支授", "支缓");
        putAliases("爆发力", "爆發力", "爆发力");
        putAliases("生存力", "生存力", "生存カ");
        putAliases("群体攻击", "群體攻擊", "群体攻击", "群體攻击", "群体攻擊",
                "群体休攻击", "群体休玫击", "群体玫击", "群体改击", "群体攻去",
                "群休攻击", "群休玫击");
        putAliases("越战越强", "越戰越強", "越战越强");
        putAliases("领袖", "領袖", "领袖", "領?袖");
        putAliases("菁英", "菁英", "精英", "菁英");
        putAliases("回击", "回擊", "回击");

        // Common traditional characters that OCR may return on a zh-TW client.
        String from = "領擊護輔療礙亞貧體風屬復輸擾禦發戰強";
        String to = "领击护辅疗碍亚贫体风属复输扰御发战强";
        for (int i = 0; i < from.length(); i++) {
            traditionalToSimplified.put(from.charAt(i), to.charAt(i));
        }
        traditionalToSimplified.put('暗', '闇');
    }

    private void putAliases(String tag, String... values) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String v : values) set.add(normalize(v));
        aliases.put(tag, new ArrayList<>(set));
    }

    /** Fold traditional variants, whitespace and punctuation so OCR text can be compared. */
    public String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            java.lang.Character mapped = traditionalToSimplified.get(c);
            if (mapped != null) c = mapped;
            if (java.lang.Character.isWhitespace(c)) continue;
            if (c == '·' || c == '•' || c == '・' || c == '.' || c == ',' || c == '，'
                    || c == '。' || c == '、' || c == ':' || c == '：') continue;
            out.append(c);
        }
        return out.toString();
    }

    public List<String> allTags() {
        return Collections.unmodifiableList(allTags);
    }

    public List<String> groupOrder() {
        return Collections.unmodifiableList(groupOrder);
    }

    public List<String> tagsInGroup(String group) {
        return groups.get(group);
    }

    public List<String> aliasesOf(String tag) {
        List<String> list = aliases.get(tag);
        return list == null ? Collections.singletonList(normalize(tag)) : list;
    }

    /**
     * Finds the most specific tag mentioned in a normalized OCR line.
     * Returns null when the line contains no tag name.
     */
    public String matchTag(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isEmpty()) return null;
        String best = null;
        int bestLen = 0;
        for (String tag : allTags) {
            for (String alias : aliasesOf(tag)) {
                if (alias.length() >= 2 && normalizedLine.contains(alias)) {
                    if (alias.length() > bestLen) {
                        bestLen = alias.length();
                        best = tag;
                    }
                }
            }
        }
        return best;
    }

    /** All tags mentioned in a line (used when OCR merges several chips into one line). */
    public List<String> matchAllTags(String normalizedLine) {
        List<String> found = new ArrayList<>();
        if (normalizedLine == null || normalizedLine.isEmpty()) return found;
        List<AliasMatch> matches = new ArrayList<>();
        for (String tag : allTags) {
            for (String alias : aliasesOf(tag)) {
                if (alias.length() >= 2) matches.add(new AliasMatch(tag, alias));
            }
        }
        // Longest aliases win.  This prevents "群体攻击" from also producing "攻击者"
        // through the shorter "攻击" alias, which used to shift the five-tag result.
        matches.sort((a, b) -> Integer.compare(b.alias.length(), a.alias.length()));
        boolean[] used = new boolean[normalizedLine.length()];
        List<AliasMatch> accepted = new ArrayList<>();
        for (AliasMatch match : matches) {
            int from = 0;
            while (from <= normalizedLine.length() - match.alias.length()) {
                int start = normalizedLine.indexOf(match.alias, from);
                if (start < 0) break;
                boolean overlap = false;
                for (int i = start; i < start + match.alias.length(); i++) {
                    if (used[i]) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    for (int i = start; i < start + match.alias.length(); i++) used[i] = true;
                    accepted.add(new AliasMatch(match.tag, match.alias, start));
                }
                from = start + match.alias.length();
            }
        }
        accepted.sort((a, b) -> Integer.compare(a.start, b.start));
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (AliasMatch match : accepted) unique.add(match.tag);
        found.addAll(unique);
        return found;
    }

    private static final class AliasMatch {
        final String tag;
        final String alias;
        final int start;

        AliasMatch(String tag, String alias) {
            this(tag, alias, 0);
        }

        AliasMatch(String tag, String alias, int start) {
            this.tag = tag;
            this.alias = alias;
            this.start = start;
        }
    }

    public boolean isTag(String name) {
        return allTags.contains(name);
    }

    public Set<String> tagSet() {
        return new LinkedHashSet<>(allTags);
    }
}
