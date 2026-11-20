package com.tkfm.recruitassistant.ui;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tkfm.recruitassistant.util.Ui;

/** Rules / about content, kept in code so the whole app works offline. */
public final class RulesDialog {

    private RulesDialog() {
    }

    public static void show(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), 0);

        content.addView(section(context, "匹配规则"));
        content.addView(body(context,
                "1. 从已选词条中枚举所有 1~3 项组合（最多 5 个输入词条）。\n"
                        + "2. 角色必须包含组合中的全部词条。\n"
                        + "3. 领袖门槛：组合未包含「领袖」时，所有 3★ 领袖角色一律排除。\n"
                        + "4. 产生相同角色列表的不同组合会合并，保留词条数最少的一组（含「菁英」的组合优先）。\n"
                        + "5. 结果按参考模块的评分排序：SSR 池 > SR 池 > R 池 > 单角色组合；"
                        + "同池内再按 SR 比例与候选数量微调。"));

        content.addView(section(context, "结果标记"));
        content.addView(body(context,
                "必出：该组合只剩一个候选角色，选它即可确定出该角色。\n"
                        + "推荐：该组合的候选全部为 SR 及以上，且不止一个角色。\n"
                        + "3★ 金色 / 2★ 粉色 / 1★·0★ 灰色，与游戏内显示一致。"));

        content.addView(section(context, "自动识别"));
        content.addView(body(context,
                "使用 ML Kit 中文 OCR 识别截图中的词条。先识别整张图，若不足 5 个词条，"
                        + "再裁剪中部图例区域重试并合并结果。繁体 / 简体与常见 OCR 异体字均可映射到同一词条。\n\n"
                        + "截屏识别只抓取一帧，图片保存在应用缓存目录，不联网、不上传。示例图与模拟面板"
                        + "可用于在没有游戏的环境下验证完整流程。"));

        content.addView(section(context, "数据说明"));
        content.addView(body(context,
                "内置 77 名角色：3★ 28 名、2★ 14 名、1★ 10 名、0★ 25 名；"
                        + "33 个词条覆盖职业、种族、胸围、体型、属性、能力与特殊标签。\n\n"
                        + "数据整理自公开资料。本应用为独立的、非官方实现，与游戏运营方及其他"
                        + "第三方工具作者均无关联；角色与词条数据的版权归游戏运营方所有。"));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);

        new MaterialAlertDialogBuilder(context)
                .setTitle("全境征才 · 规则说明")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .show();
    }

    public static void showAbout(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), 0);
        content.addView(section(context, "全境征才助手 1.1.3"));
        content.addView(body(context,
                "一个独立实现的离线全境征才计算器。\n\n"
                        + "· 手动计算：33 词条 × 77 角色，离线匹配\n"
                        + "· 自动识别：屏幕截图 + 中文 OCR\n"
                        + "· 招募记录：本机保存最近 80 次分析\n\n"
                        + "技术栈：Java + AndroidX + ML Kit + MediaProjection + "
                        + "AccessibilityService。\n\n"
                        + "本工具仅供学习与个人查询使用，请遵守游戏服务条款。"));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(context)
                .setTitle("关于")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private static TextView section(Context context, String text) {
        TextView tv = Ui.text(context, text, 14, Ui.PRIMARY, true);
        tv.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 5));
        return tv;
    }

    private static TextView body(Context context, String text) {
        TextView tv = Ui.text(context, text, 13, Ui.TEXT_DIM, false);
        tv.setLineSpacing(Ui.dp(context, 3), 1f);
        tv.setTextIsSelectable(true);
        return tv;
    }
}
