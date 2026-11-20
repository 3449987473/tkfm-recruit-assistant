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

import com.tkfm.recruitassistant.core.TagCatalog;
import com.tkfm.recruitassistant.util.Ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Scrollable tag catalog with up to five selectable tags. */
public class TagPickerView extends LinearLayout {

    public interface OnSelectionChanged {
        void onSelectionChanged(Set<String> selected);
    }

    private final TagCatalog catalog;
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final List<TextView> buttons = new ArrayList<>();
    private OnSelectionChanged listener;
    private int maxSelection = 5;
    private boolean built;

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
        refresh();
        dispatch();
    }

    public void clearSelection() {
        selected.clear();
        refresh();
        dispatch();
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
            TextView title = Ui.text(getContext(), group, 11,
                    Ui.withAlpha(Ui.TEXT_DIM, 0xCC), false);
            title.setLetterSpacing(0.05f);
            title.setPadding(Ui.dp(getContext(), 2), Ui.dp(getContext(), 13), 0,
                    Ui.dp(getContext(), 5));
            addView(title);

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
        } else {
            if (selected.size() >= maxSelection) {
                Toast.makeText(getContext(), "最多只能选择 " + maxSelection + " 个词条",
                        Toast.LENGTH_SHORT).show();
                return;
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
            button.setTextColor(on ? 0xFFFFFFFF : Ui.TEXT_DIM);
            button.setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
            button.setBackground(on
                    ? Ui.rounded(0xFF2F6DB8, 0xFF5B9BD5, 9, 1, getContext())
                    : Ui.ripple(getContext(), Ui.SURFACE_2, Ui.STROKE, 9));
            button.setAlpha(on ? 1f : 0.92f);
        }
    }

    private void dispatch() {
        if (listener != null) listener.onSelectionChanged(getSelection());
    }
}
