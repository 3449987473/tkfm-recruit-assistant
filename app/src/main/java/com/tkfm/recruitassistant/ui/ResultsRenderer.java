package com.tkfm.recruitassistant.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tkfm.recruitassistant.R;
import com.tkfm.recruitassistant.core.Character;
import com.tkfm.recruitassistant.core.MatchEngine;
import com.tkfm.recruitassistant.core.MatchGroup;
import com.tkfm.recruitassistant.util.Ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Renders grouped results exactly in the reference's "组合 → 角色" shape. */
public final class ResultsRenderer {

    public interface OnCharacterClick {
        void onCharacterClick(MatchGroup group, Character character);
    }

    private ResultsRenderer() {
    }

    public static void render(Context context, LinearLayout container, List<MatchGroup> groups,
                              List<String> selectedTags, OnCharacterClick click) {
        container.removeAllViews();
        if (groups.isEmpty()) {
            container.addView(emptyCard(context, selectedTags));
            return;
        }

        Set<Integer> uniq = new HashSet<>();
        for (MatchGroup g : groups) {
            for (Character c : g.characters) uniq.add(c.no);
        }

        LinearLayout summary = Ui.horizontal(context);
        summary.setPadding(Ui.dp(context, 2), 0, Ui.dp(context, 2), Ui.dp(context, 7));
        summary.addView(Ui.text(context, groups.size() + " 组组合", 17, Ui.TEXT, true));
        summary.addView(Ui.text(context, "  ·  " + uniq.size() + " 名候选", 13,
                Ui.TEXT_DIM, false));
        container.addView(summary);

        for (MatchGroup group : groups) {
            container.addView(groupRow(context, group, groups));
            Ui.addSpace(container, 7);
        }
    }

    /** One compact row: tag combo on the left, character list on the right. */
    private static View groupRow(Context context, MatchGroup group, List<MatchGroup> allGroups) {
        int border = group.isUnique() ? Ui.withAlpha(Ui.RED, 0x66)
                : group.isRecommended() ? Ui.withAlpha(Ui.GREEN, 0x55) : Ui.STROKE;
        LinearLayout card = Ui.horizontal(context);
        card.setGravity(Gravity.TOP);
        card.setPadding(Ui.dp(context, 10), Ui.dp(context, 9), Ui.dp(context, 10),
                Ui.dp(context, 9));
        card.setBackground(Ui.rounded(Ui.SURFACE, border, 13, 1, context));

        LinearLayout left = Ui.vertical(context);
        left.setPadding(0, 0, Ui.dp(context, 10), 0);
        for (String tag : group.combo) {
            TextView pill = Ui.pill(context, tag, 0xFFFFFFFF, 0xFF2F6DB8, 0, 6);
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            left.addView(pill);
            Ui.addSpace(left, 3);
        }
        int badgeColor = group.isUnique() ? Ui.RED
                : group.isRecommended() ? Ui.GREEN : Ui.TEXT_DIM;
        TextView badge = Ui.text(context, group.isUnique() ? "必出"
                        : group.isRecommended() ? "推荐" : group.characters.size() + " 候选",
                10.5f, badgeColor, group.isUnique() || group.isRecommended());
        badge.setPadding(0, Ui.dp(context, 2), 0, 0);
        left.addView(badge);
        card.addView(left, Ui.lp(Ui.dp(context, 74), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout right = Ui.vertical(context);
        for (Character c : group.characters) {
            TextView row = Ui.text(context, c.star + "★ " + c.name, 12.5f,
                    Ui.starColor(c.star), c.star >= 2);
            row.setPadding(0, Ui.dp(context, 3), 0, Ui.dp(context, 3));
            row.setOnClickListener(v -> showCharacter(context, c, group, allGroups));
            right.addView(row);
        }
        card.addView(right, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private static void showCharacter(Context context, Character c, MatchGroup group,
                                      List<MatchGroup> allGroups) {
        LinearLayout content = Ui.vertical(context);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), 0);

        TextView star = Ui.text(context, c.star + "★ " + c.name + "   " + c.rarity(), 18,
                Ui.starColor(c.star), true);
        content.addView(star);

        MatchGroup bestGroup = MatchEngine.bestGroupFor(allGroups, c);
        List<String> best = bestGroup == null ? group.combo : bestGroup.combo;
        boolean guaranteed = bestGroup != null && bestGroup.isUnique();
        TextView comboLabel = Ui.text(context, guaranteed ? "必出组合" : "最佳组合",
                12, guaranteed ? Ui.RED : Ui.TEXT_DIM, guaranteed);
        comboLabel.setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 6));
        content.addView(comboLabel);
        FlowLayout comboFlow = new FlowLayout(context);
        for (String tag : best) {
            comboFlow.addView(Ui.pill(context, tag, 0xFFDCE5FF, 0xFF253158,
                    Ui.withAlpha(Ui.PRIMARY, 0x88), 8));
        }
        content.addView(comboFlow);

        TextView tagsLabel = Ui.text(context, "角色词条", 12, Ui.TEXT_DIM, false);
        tagsLabel.setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 6));
        content.addView(tagsLabel);
        FlowLayout tagsFlow = new FlowLayout(context);
        if (c.tags.isEmpty()) {
            tagsFlow.addView(Ui.text(context, "无词条（不会出现在候选里）", 13, Ui.TEXT_DIM, false));
        } else {
            for (String tag : c.tags) {
                String note = c.notes.get(tag);
                String label = note == null ? tag : tag + "(" + note + ")";
                int color = note == null ? Ui.TEXT : 0xFF7FB8FF;
                tagsFlow.addView(Ui.pill(context, label, color, Ui.SURFACE_2, Ui.STROKE, 8));
            }
        }
        content.addView(tagsFlow);

        if (c.star == 3) {
            TextView hint = Ui.text(context, "3★ 领袖角色只有在词条组合包含「领袖」时才会进入候选。",
                    12, Ui.TEXT_DIM, false);
            hint.setPadding(0, Ui.dp(context, 14), 0, 0);
            content.addView(hint);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle("角色详情")
                .setView(content)
                .setPositiveButton("关闭", null)
                .show();
    }

    private static View emptyCard(Context context, List<String> selectedTags) {
        LinearLayout card = Ui.vertical(context);
        card.setPadding(Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16),
                Ui.dp(context, 18));
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.STROKE, 16, 1, context));
        TextView title = Ui.text(context, selectedTags.isEmpty() ? "还没有选择词条"
                : "没有匹配的角色", 15, Ui.TEXT, true);
        card.addView(title);
        TextView body = Ui.text(context, selectedTags.isEmpty()
                        ? "从下面的词条库中选择 1~5 个你在游戏中看到的词条，结果会实时计算。"
                        : "试试减少条件，或确认是否点选了「领袖」——3★ 领袖角色必须显式选择「领袖」才会出现。",
                13, Ui.TEXT_DIM, false);
        body.setPadding(0, Ui.dp(context, 7), 0, 0);
        body.setLineSpacing(Ui.dp(context, 3), 1f);
        card.addView(body);
        return card;
    }
}
