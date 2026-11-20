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
import java.util.List;
import java.util.Map;

/** Loads the 77-character table reconstructed from the calculator data set. */
public final class CharacterRepository {

    private static CharacterRepository instance;
    private final List<Character> characters;

    public static synchronized CharacterRepository get(Context context) {
        if (instance == null) instance = new CharacterRepository(context.getApplicationContext());
        return instance;
    }

    private CharacterRepository(Context context) {
        List<Character> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("characters.json"), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray tagsArr = o.getJSONArray("tags");
                List<String> tags = new ArrayList<>();
                for (int j = 0; j < tagsArr.length(); j++) tags.add(tagsArr.getString(j));
                JSONObject notesObj = o.optJSONObject("notes");
                Map<String, String> notes = new LinkedHashMap<>();
                if (notesObj != null) {
                    for (java.util.Iterator<String> it = notesObj.keys(); it.hasNext(); ) {
                        String key = it.next();
                        notes.put(key, notesObj.optString(key));
                    }
                }
                list.add(new Character(o.getInt("no"), o.getInt("star"), o.getString("name"),
                        o.optString("code", ""), tags, notes));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load characters.json", e);
        }
        this.characters = Collections.unmodifiableList(list);
    }

    public List<Character> all() {
        return characters;
    }

    public Character byNo(int no) {
        for (Character c : characters) if (c.no == no) return c;
        return null;
    }

    public int count() {
        return characters.size();
    }

    /** Number of characters by star, e.g. [28, 14, 10, 25] for 3..0. */
    public int[] starHistogram() {
        int[] h = new int[4];
        for (Character c : characters) {
            if (c.star >= 0 && c.star <= 3) h[3 - c.star]++;
        }
        return h;
    }
}
