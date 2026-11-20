package com.tkfm.recruitassistant.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lightweight local history (the reference stores the same records in Room). */
public final class HistoryStore {

    public static final class Record {
        public long time;
        public String mode;
        public List<String> tags = new ArrayList<>();
        public int groupCount;
        public int characterCount;
        public String bestCombo = "";
        public String bestResult = "";
        public int bestStar;

        public String summary() {
            if (bestResult.isEmpty()) return "无匹配结果";
            return bestResult;
        }
    }

    private static final String PREFS = "recruit_history";
    private static final String KEY = "records";
    private static final int MAX = 80;

    private final SharedPreferences prefs;

    public HistoryStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void add(String mode, List<String> tags, List<MatchGroup> groups) {
        Record record = new Record();
        record.time = System.currentTimeMillis();
        record.mode = mode;
        record.tags = new ArrayList<>(tags);
        record.groupCount = groups.size();
        int chars = 0;
        for (MatchGroup g : groups) chars += g.characters.size();
        record.characterCount = chars;
        if (!groups.isEmpty()) {
            MatchGroup best = groups.get(0);
            record.bestCombo = best.comboText();
            record.bestStar = best.maxStar();
            StringBuilder sb = new StringBuilder();
            int shown = Math.min(4, best.characters.size());
            for (int i = 0; i < shown; i++) {
                if (i > 0) sb.append("、");
                sb.append(best.characters.get(i).name);
            }
            if (best.characters.size() > shown) sb.append(" 等");
            record.bestResult = sb.toString();
            if (best.isUnique()) record.bestResult += "（必出）";
        }
        List<Record> all = list();
        all.add(0, record);
        while (all.size() > MAX) all.remove(all.size() - 1);
        save(all);
    }

    public List<Record> list() {
        List<Record> out = new ArrayList<>();
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Record r = new Record();
                r.time = o.optLong("time");
                r.mode = o.optString("mode", "手动");
                JSONArray tags = o.optJSONArray("tags");
                if (tags != null) {
                    for (int j = 0; j < tags.length(); j++) r.tags.add(tags.getString(j));
                }
                r.groupCount = o.optInt("groups");
                r.characterCount = o.optInt("characters");
                r.bestCombo = o.optString("bestCombo", "");
                r.bestResult = o.optString("bestResult", "");
                r.bestStar = o.optInt("bestStar");
                out.add(r);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }

    private void save(List<Record> records) {
        JSONArray arr = new JSONArray();
        try {
            for (Record r : records) {
                JSONObject o = new JSONObject();
                o.put("time", r.time);
                o.put("mode", r.mode);
                JSONArray tags = new JSONArray();
                for (String t : r.tags) tags.put(t);
                o.put("tags", tags);
                o.put("groups", r.groupCount);
                o.put("characters", r.characterCount);
                o.put("bestCombo", r.bestCombo);
                o.put("bestResult", r.bestResult);
                o.put("bestStar", r.bestStar);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }
}
