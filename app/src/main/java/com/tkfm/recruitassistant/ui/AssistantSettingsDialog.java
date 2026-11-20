package com.tkfm.recruitassistant.ui;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tkfm.recruitassistant.AssistantSettings;
import com.tkfm.recruitassistant.util.Ui;

/**
 * Assistant behaviour: the two workflow dimensions (run on scan, auto recruit), the click
 * interval, and the leader exception.  "Select tags/time" is always on.
 */
public final class AssistantSettingsDialog {

    private AssistantSettingsDialog() {
    }

    public static void show(Context context) {
        int delay = AssistantSettings.delay(context);

        LinearLayout content = Ui.vertical(context);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), 0);

        SwitchMaterial runOnScan = new SwitchMaterial(context);
        runOnScan.setText("扫描后立即执行");
        runOnScan.setChecked(AssistantSettings.autoRunOnScan(context));
        content.addView(runOnScan);
        content.addView(hint(context,
                "点悬浮球扫描后，直接按最优结果自动点选词条和招募时间。"
                        + "含「领袖」词条时不会自动执行。"));

        SwitchMaterial recruit = new SwitchMaterial(context);
        recruit.setText("自动点击「开始招募」");
        recruit.setChecked(AssistantSettings.autoRecruit(context));
        content.addView(recruit);
        TextView recruitHint = hint(context,
                "选好词条/时间后自动点游戏里的「开始招募」，每次消耗一张招募券。"
                        + "含「领袖」时始终不自动招募。");
        recruitHint.setTextColor(Ui.withAlpha(Ui.RED, 0xDD));
        content.addView(recruitHint);

        TextView delayTitle = Ui.text(context, "点击间隔", 14, Ui.TEXT, true);
        delayTitle.setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 6));
        content.addView(delayTitle);

        final int[] values = {100, 150, 250, 400};
        String[] labels = {"100 ms", "150 ms", "250 ms", "400 ms"};
        Spinner delaySpinner = new Spinner(context, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        delaySpinner.setAdapter(adapter);
        int selected = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == delay) selected = i;
        }
        delaySpinner.setSelection(selected);
        content.addView(delaySpinner);
        content.addView(hint(context, "词条和时间始终自动点选。"));

        TextView leaderNote = Ui.text(context,
                "※ 含「领袖」词条时：扫描后不会自动点选，需手动点击结果；\n"
                        + "无论哪种模式，最后一步「开始招募」都需手动按。",
                11.5f, Ui.withAlpha(Ui.RED, 0xDD), false);
        leaderNote.setLineSpacing(Ui.dp(context, 2), 1f);
        leaderNote.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 4));
        content.addView(leaderNote);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);

        new MaterialAlertDialogBuilder(context)
                .setTitle("助手设置")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    int index = Math.max(0, Math.min(values.length - 1,
                            delaySpinner.getSelectedItemPosition()));
                    save(context, runOnScan.isChecked(), recruit.isChecked(), values[index]);
                })
                .show();
    }

    private static void save(Context context, boolean runOnScan, boolean autoRecruit, int delay) {
        AssistantSettings.setDelay(context, delay);
        AssistantSettings.setAutoRunOnScan(context, runOnScan);
        if (autoRecruit && !AssistantSettings.autoRecruit(context)) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("开启自动开始招募？")
                    .setMessage("开启后，选好词条/时间会直接点击游戏里的「开始招募」，"
                            + "每次都会消耗一张招募券。\n\n确认要开启吗？")
                    .setNegativeButton("保持关闭", (d, w) -> {
                        AssistantSettings.setAutoRecruit(context, false);
                        toast(context, "自动招募保持关闭");
                    })
                    .setPositiveButton("确认开启", (d, w) -> {
                        AssistantSettings.setAutoRecruit(context, true);
                        toast(context, "已开启自动开始招募");
                    })
                    .show();
        } else {
            AssistantSettings.setAutoRecruit(context, autoRecruit);
            toast(context, "助手设置已保存");
        }
    }

    private static TextView hint(Context context, String text) {
        TextView view = Ui.text(context, text, 12, Ui.TEXT_DIM, false);
        view.setLineSpacing(Ui.dp(context, 2), 1f);
        view.setPadding(0, 0, 0, Ui.dp(context, 8));
        return view;
    }

    private static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
