package com.tkfm.recruitassistant.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tkfm.recruitassistant.R;
import com.tkfm.recruitassistant.core.Character;
import com.tkfm.recruitassistant.core.CharacterRepository;
import com.tkfm.recruitassistant.core.HistoryStore;
import com.tkfm.recruitassistant.core.MatchEngine;
import com.tkfm.recruitassistant.core.MatchGroup;
import com.tkfm.recruitassistant.core.ProphecyEngine;
import com.tkfm.recruitassistant.util.Ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ManualFragment extends Fragment {

    private static final String PREFS = "manual_state";
    private static final String KEY_TAGS = "tags";

    private TagPickerView picker;
    private TextView selectedCount;
    private LinearLayout resultHeader;
    private LinearLayout results;
    private TextView btnSave;

    private MatchEngine engine;
    private ProphecyEngine prophecy;
    private HistoryStore history;
    private List<MatchGroup> lastGroups = Collections.emptyList();
    private List<String> lastSelection = new ArrayList<>();
    private boolean syncing;
    private List<String> pendingTags;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manual, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        engine = new MatchEngine(CharacterRepository.get(context));
        prophecy = ProphecyEngine.get(context);
        history = new HistoryStore(context);
        picker = view.findViewById(R.id.tagPicker);
        selectedCount = view.findViewById(R.id.selectedCount);
        resultHeader = view.findViewById(R.id.resultHeader);
        results = view.findViewById(R.id.resultsContainer);
        btnSave = view.findViewById(R.id.btnSave);

        picker.setOnSelectionChanged(this::onSelectionChanged);
        view.findViewById(R.id.clearButton).setOnClickListener(v -> {
            picker.clearSelection();
            persist(Collections.emptyList());
        });
        btnSave.setOnClickListener(v -> saveRecord());

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> saved = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY_TAGS, "");
            if (raw != null && !raw.isEmpty()) {
                for (String tag : raw.split(",")) if (!tag.isEmpty()) saved.add(tag);
            }
        } catch (Exception ignored) {
        }
        if (pendingTags != null) {
            saved = pendingTags;
            pendingTags = null;
        }
        picker.setSelection(saved);
    }

    public void setTags(List<String> tags) {
        if (picker == null) {
            pendingTags = tags;
            return;
        }
        picker.setSelection(tags);
        persist(tags);
    }

    private void onSelectionChanged(Set<String> selected) {
        if (syncing) return;
        List<String> ordered = new ArrayList<>(selected);
        String green = picker.consumeGreenTapped();
        if (green != null) {
            List<String> rest = prophecy.completion(ordered);
            if (!rest.isEmpty()) {
                LinkedHashSet<String> completed = new LinkedHashSet<>(ordered);
                completed.addAll(rest);
                syncing = true;
                picker.setSelection(completed);
                syncing = false;
                applySelection(completed);
                return;
            }
        }
        applySelection(selected);
    }

    private void applySelection(Collection<String> selection) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>(selection);
        lastSelection = new ArrayList<>(ordered);
        persist(lastSelection);
        selectedCount.setText("已选 " + ordered.size() + " / 5");

        lastGroups = engine.analyze(ordered);
        resultHeader.setVisibility(ordered.isEmpty() ? View.GONE : View.VISIBLE);
        btnSave.setVisibility(lastGroups.isEmpty() ? View.GONE : View.VISIBLE);
        ResultsRenderer.render(requireContext(), results, lastGroups, lastSelection,
                this::onCharacterClicked);

        if (ordered.size() >= ProphecyEngine.PANEL_TAGS) {
            picker.setHints(Collections.emptyMap());
        } else {
            picker.setHints(prophecy.hints(lastSelection));
        }
    }

    private void onCharacterClicked(MatchGroup group, Character character) {
        List<MatchGroup> containing = new ArrayList<>();
        for (MatchGroup g : lastGroups) {
            if (g.characters.contains(character)) containing.add(g);
        }
        if (containing.isEmpty()) containing.add(group);
        String[] items = new String[containing.size()];
        for (int i = 0; i < containing.size(); i++) {
            MatchGroup g = containing.get(i);
            items[i] = g.comboText() + "    → " + g.badgeText();
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(character.displayName() + " 的组合")
                .setItems(items, (dialog, which) -> {
                    picker.setSelection(containing.get(which).combo);
                    Toast.makeText(requireContext(), "已锁定该组合", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void saveRecord() {
        if (lastSelection.isEmpty() || lastGroups.isEmpty()) return;
        history.add("手动", lastSelection, lastGroups);
        Toast.makeText(requireContext(), "已保存到招募记录", Toast.LENGTH_SHORT).show();
    }

    private void persist(List<String> tags) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TAGS, String.join(",", tags)).apply();
    }
}
