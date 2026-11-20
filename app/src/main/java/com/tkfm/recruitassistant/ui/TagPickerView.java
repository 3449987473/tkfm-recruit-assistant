package com.tkfm.recruitassistant.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
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
        for (String group : catalog.groupOrder()) {
            LinearLayout header = Ui.horizontal(getContext());
            header.setPadding(0, Ui.dp(getContext(), 14), 0, Ui.dp(getContext(), 7));
            TextView title = Ui.text(getContext(), group, 13, Ui.PRIMARY, true);
            title.setLetterSpacing(0.06f);
            header.addView(title);
            TextView rule = Ui.text(getContext(), "  ·  " + catalog.tagsInGroup(group).size() + " 项",
                    11, Ui.TEXT_DIM, false);
            header.addView(rule);
            addView(header);

            FlowLayout flow = new FlowLayout(getContext());
            flow.setGap(Ui.dp(getContext(), 7), Ui.dp(getContext(), 7));
            for (String tag : catalog.tagsInGroup(group)) {
                TextView button = makeTagButton(tag);
                flow.addView(button);
                buttons.add(button);
            }
            addView(flow);
        }
    }

    private TextView makeTagButton(String tag) {
        TextView tv = new TextView(getContext());
        tv.setText(tag);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT);
        tv.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 7),
                Ui.dp(getContext(), 12), Ui.dp(getContext(), 7));
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
                    ? Ui.rounded(0xFF27365E, Ui.PRIMARY, 10, 1, getContext())
                    : Ui.ripple(getContext(), Ui.SURFACE_2, Ui.STROKE, 10));
            button.setAlpha(on ? 1f : 0.92f);
        }
    }

    private void dispatch() {
        if (listener != null) listener.onSelectionChanged(getSelection());
    }
}
