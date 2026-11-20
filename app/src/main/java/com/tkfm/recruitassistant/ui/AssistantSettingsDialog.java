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

/** Assistant behaviour switches: tags, time, auto-recruit and click delay. */
public final class AssistantSettingsDialog {

    private AssistantSettingsDialog() {
    }

    public static void show(Context context) {
        int delay = AssistantSettings.delay(context);

        LinearLayout content = Ui.vertical(context);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), 0);

        SwitchMaterial tags = new SwitchMaterial(context);
        tags.setText("自动点选词条");
        tags.setChecked(AssistantSettings.autoTags(context));
        content.addView(tags);
        content.addView(hint(context, "点结果角色时，自动补选/取消该组合的词条。"));

        SwitchMaterial time = new SwitchMaterial(context);
        time.setText("自动调整招募时间（09:00）");
        time.setChecked(AssistantSettings.autoTime(context));
        content.addView(time);
        content.addView(hint(context, "按最短路径把时间调到 09:00：当前 <5 点下箭头，≥5 点上箭头。"));

        SwitchMaterial recruit = new SwitchMaterial(context);
        recruit.setText("自动点击「开始招募」");
        recruit.setChecked(AssistantSettings.autoRecruit(context));
        content.addView(recruit);
        TextView recruitHint = hint(context,
                "开启后，点结果角色会在选好词条/时间后自动点击游戏里的「开始招募」，"
                        + "会消耗招募资源。默认关闭。");
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
        content.addView(hint(context,
                "词条/时间/开始招募全部关闭时，点结果角色不会执行任何自动操作。"));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);

        new MaterialAlertDialogBuilder(context)
                .setTitle("助手设置")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    int index = Math.max(0, Math.min(values.length - 1,
                            delaySpinner.getSelectedItemPosition()));
                    save(context, tags.isChecked(), time.isChecked(),
                            recruit.isChecked(), values[index]);
                })
                .show();
    }

    private static void save(Context context, boolean autoTags, boolean autoTime,
                             boolean autoRecruit, int delay) {
        AssistantSettings.setAutoTags(context, autoTags);
        AssistantSettings.setAutoTime(context, autoTime);
        AssistantSettings.setDelay(context, delay);
        if (autoRecruit && !AssistantSettings.autoRecruit(context)) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("开启自动开始招募？")
                    .setMessage("开启后，点击结果角色会直接点击游戏里的「开始招募」，"
                            + "每次都会消耗招募资源。\n\n确认要开启吗？")
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
