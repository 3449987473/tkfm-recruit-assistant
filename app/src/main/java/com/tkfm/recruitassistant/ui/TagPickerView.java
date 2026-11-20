package com.tkfm.recruitassistant.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tkfm.recruitassistant.core.ProphecyEngine;
import com.tkfm.recruitassistant.core.TagCatalog;
import com.tkfm.recruitassistant.util.Ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scrollable tag catalog with up to five selectable tags. */
public class TagPickerView extends LinearLayout {

    public interface OnSelectionChanged {
        void onSelectionChanged(Set<String> selected);
    }

    private final TagCatalog catalog;
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final List<TextView> buttons = new ArrayList<>();
    private final Map<String, Integer> hints = new LinkedHashMap<>();
    private OnSelectionChanged listener;
    private int maxSelection = 5;
    private boolean built;
    private boolean freeMode;
    private String greenTapped;

    public TagPickerView(Context context) {
        super(context);
        catalog = TagCatalog.get(context);
        setOrientation(VERTICAL);
        build();
    }

    public TagPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        catalog = TagCatalog.get(context);
        setOrientation(VERTICAL);
        build();
    }

    public void setOnSelectionChanged(OnSelectionChanged listener) {
        this.listener = listener;
    }

    public Set<String> getSelection() {
        return new LinkedHashSet<>(selected);
    }

    public void setSelection(Collection<String> tags) {
        selected.clear();
        if (tags != null) {
            for (String tag : tags) {
                if (catalog.isTag(tag) && selected.size() < maxSelection) selected.add(tag);
            }
        }
        freeMode = false;
        greenTapped = null;
        refresh();
        dispatch();
    }

    public void clearSelection() {
        selected.clear();
        freeMode = false;
        greenTapped = null;
        hints.clear();
        refresh();
        dispatch();
    }

    /** Prophecy highlights for the next tags; ignored once the user goes off-recipe. */
    public void setHints(Map<String, Integer> next) {
        hints.clear();
        if (next != null) hints.putAll(next);
        refresh();
    }

    public boolean isFreeMode() {
        return freeMode;
    }

    /** Returns the tag that was tapped while showing a green hint, then clears the flag. */
    public String consumeGreenTapped() {
        String tag = greenTapped;
        greenTapped = null;
        return tag;
    }

    public void addTag(String tag) {
        if (selected.size() >= maxSelection) {
            Toast.makeText(getContext(), "最多只能选择 " + maxSelection + " 个词条", Toast.LENGTH_SHORT).show();
            return;
        }
        if (catalog.isTag(tag) && selected.add(tag)) {
            refresh();
            dispatch();
        }
    }

    private void build() {
        int gap = Ui.dp(getContext(), 6);
        for (String group : catalog.groupOrder()) {
            List<String> tags = catalog.tagsInGroup(group);
            for (int i = 0; i < tags.size(); i += 3) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = gap;
                for (int j = 0; j < 3; j++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            0, Ui.dp(getContext(), 40), 1);
                    if (j > 0) lp.setMarginStart(gap);
                    if (i + j < tags.size()) {
                        TextView button = makeTagButton(tags.get(i + j));
                        row.addView(button, lp);
                        buttons.add(button);
                    } else {
                        row.addView(new View(getContext()), lp);
                    }
                }
                addView(row, rowLp);
            }
        }
    }

    private TextView makeTagButton(String tag) {
        TextView tv = new TextView(getContext());
        tv.setText(tag);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT);
        tv.setPadding(Ui.dp(getContext(), 4), 0, Ui.dp(getContext(), 4), 0);
        tv.setTag(tag);
        tv.setOnClickListener(v -> toggle(tag));
        return tv;
    }

    private void toggle(String tag) {
        if (selected.contains(tag)) {
            selected.remove(tag);
            freeMode = false;
        } else {
            if (selected.size() >= maxSelection) {
                Toast.makeText(getContext(), "最多只能选择 " + maxSelection + " 个词条",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Integer hint = hints.get(tag);
            if (hint != null && hint == ProphecyEngine.HINT_GREEN) {
                greenTapped = tag;
            } else if (hint == null && !hints.isEmpty()) {
                // Off-recipe tap: prophecy switches off until the selection is reset.
                freeMode = true;
                hints.clear();
            }
            selected.add(tag);
        }
        refresh();
        dispatch();
    }

    private void refresh() {
        for (TextView button : buttons) {
            String tag = (String) button.getTag();
            boolean on = selected.contains(tag);
            if (on) {
                applyButton(button, 0xFFFFFFFF, Typeface.BOLD,
                        Ui.rounded(0xFF2F6DB8, 0xFF5B9BD5, 9, 1, getContext()), 1f);
                continue;
            }
            Integer hint = freeMode ? null : hints.get(tag);
            if (hint != null && hint == ProphecyEngine.HINT_GREEN) {
                applyButton(button, Ui.GREEN, Typeface.BOLD,
                        Ui.rounded(Ui.withAlpha(Ui.GREEN, 0x22), Ui.GREEN, 9, 1,
                                getContext()), 1f);
            } else if (hint != null && hint == ProphecyEngine.HINT_BLUE) {
                applyButton(button, Ui.PRIMARY, Typeface.BOLD,
                        Ui.rounded(Ui.withAlpha(Ui.PRIMARY, 0x1C), Ui.PRIMARY, 9, 1,
                                getContext()), 1f);
            } else {
                applyButton(button, Ui.TEXT_DIM, Typeface.NORMAL,
                        Ui.ripple(getContext(), Ui.SURFACE_2, Ui.STROKE, 9), 0.92f);
            }
        }
    }

    private void applyButton(TextView button, int color, int style,
                             android.graphics.drawable.Drawable background, float alpha) {
        button.setTextColor(color);
        button.setTypeface(Typeface.DEFAULT, style);
        button.setBackground(background);
        button.setAlpha(alpha);
    }

    private void dispatch() {
        if (listener != null) listener.onSelectionChanged(getSelection());
    }
}
