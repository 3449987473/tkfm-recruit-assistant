package com.tkfm.recruitassistant.core;

import android.content.Context;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prefix-based "prophecy": 155 known five-tag recipes tell the player which tag to pick next.
 * A unique next tag can be auto-completed; ambiguous ones are only highlighted.
 */
public final class ProphecyEngine {

    public static final int HINT_NONE = 0;
    public static final int HINT_BLUE = 1;
    public static final int HINT_GREEN = 2;

    /** The game lets the player pick at most three conditions, so completion stops there. */
    public static final int MAX_CONDITIONS = 3;

    private static ProphecyEngine instance;

    private final List<List<String>> recipes;

    public static synchronized ProphecyEngine get(Context context) {
        if (instance == null) instance = new ProphecyEngine(context.getApplicationContext());
        return instance;
    }

    private ProphecyEngine(Context context) {
        recipes = load(context);
    }

    /** Test seam: lets the prefix logic run on the JVM without assets. */
    ProphecyEngine(List<List<String>> recipes) {
        this.recipes = recipes;
    }

    private static List<List<String>> load(Context context) {
        List<List<String>> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("prophetic_words.json"), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONArray row = arr.getJSONArray(i);
                if (row.length() != 5) continue;
                List<String> recipe = new ArrayList<>();
                for (int j = 0; j < 5; j++) recipe.add(row.getString(j));
                out.add(Collections.unmodifiableList(recipe));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load prophetic_words.json", e);
        }
        return out;
    }

    /** Recipes whose first N tags match the selection; order matters. */
    public List<List<String>> matches(List<String> selectedInOrder) {
        List<List<String>> out = new ArrayList<>();
        if (selectedInOrder == null || selectedInOrder.isEmpty()
                || selectedInOrder.size() >= 5) return out;
        int n = selectedInOrder.size();
        for (List<String> recipe : recipes) {
            boolean ok = true;
            for (int i = 0; i < n; i++) {
                if (!recipe.get(i).equals(selectedInOrder.get(i))) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(recipe);
        }
        return out;
    }

    /** Next-tag highlights: HINT_GREEN when only one recipe continues, HINT_BLUE otherwise. */
    public Map<String, Integer> hints(List<String> selectedInOrder) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (selectedInOrder == null || selectedInOrder.isEmpty()
                || selectedInOrder.size() >= 5) return out;
        int index = selectedInOrder.size();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (List<String> recipe : matches(selectedInOrder)) {
            String next = recipe.get(index);
            Integer count = counts.get(next);
            counts.put(next, count == null ? 1 : count + 1);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            out.put(entry.getKey(),
                    entry.getValue() == 1 ? HINT_GREEN : HINT_BLUE);
        }
        return out;
    }

    /** Remaining tags of the first matching recipe, capped at the game's three conditions. */
    public List<String> completion(List<String> selectedInOrder) {
        List<List<String>> matches = matches(selectedInOrder);
        if (matches.isEmpty()) return Collections.emptyList();
        List<String> recipe = matches.get(0);
        int end = Math.min(recipe.size(), MAX_CONDITIONS);
        if (selectedInOrder.size() >= end) return Collections.emptyList();
        return new ArrayList<>(recipe.subList(selectedInOrder.size(), end));
    }

    public int recipeCount() {
        return recipes.size();
    }
}
