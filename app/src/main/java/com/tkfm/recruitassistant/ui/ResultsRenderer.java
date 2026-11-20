package com.tkfm.recruitassistant.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
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
        int maxStar = 0;
        for (MatchGroup g : groups) {
            for (Character c : g.characters) uniq.add(c.no);
            maxStar = Math.max(maxStar, g.maxStar());
        }

        LinearLayout summary = Ui.horizontal(context);
        summary.setPadding(Ui.dp(context, 2), 0, Ui.dp(context, 2), Ui.dp(context, 8));
        TextView count = Ui.text(context, groups.size() + " 组组合", 20, Ui.TEXT, true);
        summary.addView(count);
        TextView detail = Ui.text(context, "  ·  " + uniq.size() + " 名候选  ·  最高 "
                + maxStar + "★", 13, Ui.TEXT_DIM, false);
        summary.addView(detail);
        container.addView(summary);

        for (int i = 0; i < groups.size(); i++) {
            MatchGroup group = groups.get(i);
            container.addView(groupCard(context, group, groups, i + 1, click));
            Ui.addSpace(container, 9);
        }
    }

    private static View groupCard(Context context, MatchGroup group, List<MatchGroup> allGroups,
                                  int index, OnCharacterClick click) {
        LinearLayout card = Ui.vertical(context);
        card.setPadding(Ui.dp(context, 13), Ui.dp(context, 12), Ui.dp(context, 13),
                Ui.dp(context, 13));
        int badgeColor = group.isUnique() ? Ui.RED
                : group.isRecommended() ? Ui.GREEN : Ui.TEXT_DIM;
        int border = group.isUnique() ? Ui.withAlpha(Ui.RED, 0x66)
                : group.isRecommended() ? Ui.withAlpha(Ui.GREEN, 0x55) : Ui.STROKE;
        card.setBackground(Ui.rounded(Ui.SURFACE, border, 16, 1, context));

        LinearLayout header = Ui.horizontal(context);
        TextView badge = Ui.text(context, group.badgeText(), 12, badgeColor, true);
        badge.setPadding(Ui.dp(context, 8), Ui.dp(context, 3), Ui.dp(context, 8),
                Ui.dp(context, 3));
        badge.setBackground(Ui.rounded(Ui.withAlpha(badgeColor, 0x22),
                Ui.withAlpha(badgeColor, 0x66), 8, 1, context));
        header.addView(badge);

        TextView star = Ui.text(context, "  最高 " + group.maxStar() + "★", 12,
                Ui.starColor(group.maxStar()), true);
        header.addView(star);

        View spacer = new View(context);
        header.addView(spacer, Ui.lp(0, 1, 1));

        TextView idx = Ui.text(context, "#" + index, 11, Ui.TEXT_DIM, false);
        header.addView(idx);
        card.addView(header);

        FlowLayout combos = new FlowLayout(context);
        combos.setPadding(0, Ui.dp(context, 9), 0, Ui.dp(context, 2));
        for (String tag : group.combo) {
            TextView pill = Ui.pill(context, tag, 0xFFDCE5FF, 0xFF253158,
                    Ui.withAlpha(Ui.PRIMARY, 0x88), 8);
            combos.addView(pill);
        }
        card.addView(combos);

        FlowLayout chars = new FlowLayout(context);
        chars.setPadding(0, Ui.dp(context, 7), 0, 0);
        for (Character c : group.characters) {
            chars.addView(characterChip(context, c, v -> {
                showCharacter(context, c, group, allGroups);
            }));
        }
        card.addView(chars);
        return card;
    }

    private static TextView characterChip(Context context, Character character,
                                          View.OnClickListener listener) {
        int color = Ui.starColor(character.star);
        TextView tv = new TextView(context);
        tv.setText(character.star + "★ " + character.name);
        tv.setTextSize(12.5f);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT, character.star >= 2 ? Typeface.BOLD : Typeface.NORMAL);
        tv.setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10),
                Ui.dp(context, 6));
        tv.setBackground(Ui.ripple(context, Ui.withAlpha(color, 0x18),
                Ui.withAlpha(color, 0x55), 9));
        tv.setOnClickListener(listener);
        return tv;
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
