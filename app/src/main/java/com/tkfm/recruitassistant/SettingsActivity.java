package com.tkfm.recruitassistant;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tkfm.recruitassistant.core.HistoryStore;
import com.tkfm.recruitassistant.util.Ui;

/**
 * Single settings screen: assistant workflow, floating button, data and permission status.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int[] DELAY_VALUES = {100, 150, 250, 400};
    private static final String[] DELAY_LABELS = {"100 ms", "150 ms", "250 ms", "400 ms"};

    private static final int[] BALL_SIZES = {44, 52, 60};
    private static final String[] BALL_SIZE_LABELS = {"小 (44dp)", "中 (52dp)", "大 (60dp)"};

    private static final int[] BALL_ALPHAS = {100, 85, 70};
    private static final String[] BALL_ALPHA_LABELS = {"100%", "85%", "70%"};

    private static final int[] HISTORY_MAX_VALUES = {20, 50, 80, 0};
    private static final String[] HISTORY_MAX_LABELS = {"20 条", "50 条", "80 条", "不限"};

    private LinearLayout content;
    private TextView projectionState;
    private TextView accessibilityState;
    private TextView batteryState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout bar = Ui.horizontal(this);
        bar.setPadding(dp(10), dp(12), dp(16), dp(6));
        TextView back = Ui.text(this, "←", 24, Ui.TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, Ui.lp(dp(44), dp(44)));
        bar.addView(Ui.text(this, "设置", 21, Ui.TEXT, true));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        content = Ui.vertical(this);
        content.setPadding(dp(16), 0, dp(16), dp(28));
        scroll.addView(content);
        root.addView(scroll, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        fillContent();
        return root;
    }

    private void fillContent() {
        content.removeAllViews();

        // ------------------------------------------------------------ assistant
        content.addView(section("助手行为"));
        LinearLayout assistant = card();
        assistant.addView(switchRow("扫描后立即执行",
                "点悬浮球扫描后，直接按最优结果自动点选词条和招募时间。含「领袖」词条时不会自动执行。",
                AssistantSettings.autoRunOnScan(this),
                (sw, value) -> AssistantSettings.setAutoRunOnScan(this, value)));
        assistant.addView(divider());
        assistant.addView(switchRow("自动点击「开始招募」",
                "选好词条/时间后自动点游戏里的「开始招募」，每次消耗一张招募券。",
                AssistantSettings.autoRecruit(this), this::confirmAutoRecruit));
        assistant.addView(divider());
        assistant.addView(spinnerRow("点击间隔", null, DELAY_LABELS,
                indexOf(DELAY_VALUES, AssistantSettings.delay(this)),
                index -> AssistantSettings.setDelay(this, DELAY_VALUES[index])));
        content.addView(assistant);

        content.addView(note("※ 含「领袖」词条时：扫描后不会自动点选，需手动点击结果；"
                + "无论哪种模式，最后一步「开始招募」都需手动按。"));

        // ------------------------------------------------------------ overlay
        content.addView(section("悬浮窗"));
        LinearLayout overlay = card();
        overlay.addView(spinnerRow("悬浮球大小", null, BALL_SIZE_LABELS,
                indexOf(BALL_SIZES, AssistantSettings.ballSize(this)),
                index -> {
                    AssistantSettings.setBallSize(this, BALL_SIZES[index]);
                    AssistantAccessibilityService service = AssistantAccessibilityService.get();
                    if (service != null) service.refreshOverlayAppearance();
                }));
        overlay.addView(divider());
        overlay.addView(spinnerRow("悬浮球透明度", null, BALL_ALPHA_LABELS,
                indexOf(BALL_ALPHAS, AssistantSettings.ballAlpha(this)),
                index -> {
                    AssistantSettings.setBallAlpha(this, BALL_ALPHAS[index]);
                    AssistantAccessibilityService service = AssistantAccessibilityService.get();
                    if (service != null) service.refreshOverlayAppearance();
                }));
        overlay.addView(divider());
        overlay.addView(actionRow("重置悬浮球位置", "把悬浮球放回屏幕右侧默认位置。", "重置", () -> {
            AssistantAccessibilityService service = AssistantAccessibilityService.get();
            if (service != null) service.resetOverlayPosition();
            toast("悬浮球位置已重置");
        }));
        content.addView(overlay);

        // ------------------------------------------------------------ data
        content.addView(section("数据"));
        LinearLayout data = card();
        data.addView(spinnerRow("招募记录上限", "超出后自动删除最旧的记录。",
                HISTORY_MAX_LABELS, indexOf(HISTORY_MAX_VALUES, HistoryStore.getMax(this)),
                index -> {
                    HistoryStore.setMax(this, HISTORY_MAX_VALUES[index]);
                    toast("记录上限已更新");
                }));
        content.addView(data);

        // ------------------------------------------------------------ permissions
        content.addView(section("权限与系统"));
        LinearLayout permissions = card();
        projectionState = addStatusRow(permissions, "屏幕录制",
                ProjectionService.isRunning() ? "停止" : "去启动", this::toggleProjection);
        permissions.addView(divider());
        accessibilityState = addStatusRow(permissions, "无障碍服务", "去开启",
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        permissions.addView(divider());
        batteryState = addStatusRow(permissions, "电池优化", "去设置",
                this::requestIgnoreBatteryOptimization);
        permissions.addView(divider());
        permissions.addView(actionRow("真我 / OPPO 后台设置",
                "在「耗电管理」中允许后台活动，避免录屏会话被系统中断。", "打开", this::openAppDetails));
        content.addView(permissions);

        // ------------------------------------------------------------ about
        content.addView(section("关于"));
        LinearLayout about = card();
        about.addView(actionRow("版本", versionText(), "查看更新",
                () -> openUrl("https://github.com/3449987473/tkfm-recruit-assistant/releases")));
        about.addView(divider());
        about.addView(actionRow("恢复默认设置", "重置助手、悬浮窗和记录上限；招募记录不会删除。",
                "恢复", this::confirmResetDefaults));
        about.addView(divider());
        about.addView(actionRow("清空所有数据", "删除全部招募记录与本机设置，不可撤销。",
                "清空", this::confirmClearAll));
        content.addView(about);

        refreshStatus();
    }

    // ------------------------------------------------------------------ rows

    private View switchRow(String title, String subtitle, boolean checked, OnSwitch onChange) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(dp(14), dp(12), dp(10), dp(12));
        LinearLayout texts = Ui.vertical(this);
        texts.addView(Ui.text(this, title, 14.5f, Ui.TEXT, true));
        TextView hint = Ui.text(this, subtitle, 11.5f, Ui.TEXT_DIM, false);
        hint.setLineSpacing(dp(2), 1f);
        hint.setPadding(0, dp(4), dp(8), 0);
        texts.addView(hint);
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        SwitchMaterial sw = new SwitchMaterial(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((button, value) -> onChange.onSwitch(sw, value));
        row.addView(sw);
        return row;
    }

    private View spinnerRow(String title, String subtitle, String[] labels, int selected,
                            OnIndexSelect onSelect) {
        LinearLayout row = Ui.vertical(this);
        row.setPadding(dp(14), dp(12), dp(14), dp(10));
        row.addView(Ui.text(this, title, 14.5f, Ui.TEXT, true));
        if (subtitle != null) {
            TextView hint = Ui.text(this, subtitle, 11.5f, Ui.TEXT_DIM, false);
            hint.setPadding(0, dp(3), 0, 0);
            row.addView(hint);
        }
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView,
                                @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Ui.TEXT);
                tv.setTextSize(14);
                return tv;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView,
                                        @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(Ui.TEXT);
                tv.setTextSize(14);
                tv.setBackgroundColor(Ui.SURFACE_2);
                tv.setPadding(dp(14), dp(10), dp(14), dp(10));
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != selected) onSelect.onSelect(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        row.addView(spinner, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View actionRow(String title, String subtitle, String action, Runnable onClick) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        LinearLayout texts = Ui.vertical(this);
        texts.addView(Ui.text(this, title, 14.5f, Ui.TEXT, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView hint = Ui.text(this, subtitle, 11.5f, Ui.TEXT_DIM, false);
            hint.setLineSpacing(dp(2), 1f);
            hint.setPadding(0, dp(4), dp(8), 0);
            texts.addView(hint);
        }
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(actionButton(action, onClick));
        return row;
    }

    private TextView actionButton(String label, Runnable onClick) {
        TextView button = Ui.text(this, label, 12.5f, Ui.PRIMARY, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(13), dp(6), dp(13), dp(6));
        button.setBackground(Ui.ripple(this, Ui.withAlpha(Ui.PRIMARY, 0x1E),
                Ui.withAlpha(Ui.PRIMARY, 0x66), 9));
        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    /** Status row: title on the left, state text + action on the right. Returns the state view. */
    private TextView addStatusRow(LinearLayout card, String title, String action, Runnable onClick) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.addView(Ui.text(this, title, 14.5f, Ui.TEXT, true),
                Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView state = Ui.text(this, "检查中", 12.5f, Ui.TEXT_DIM, false);
        state.setPadding(0, 0, dp(10), 0);
        row.addView(state);
        row.addView(actionButton(action, onClick));
        card.addView(row);
        return state;
    }

    private TextView section(String title) {
        TextView tv = Ui.text(this, title, 13, Ui.PRIMARY, true);
        tv.setLetterSpacing(0.08f);
        tv.setPadding(dp(4), dp(18), 0, dp(8));
        return tv;
    }

    private LinearLayout card() {
        LinearLayout card = Ui.vertical(this);
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.STROKE, 16, 1, this));
        return card;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.withAlpha(Ui.STROKE, 0x99));
        v.setLayoutParams(Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        ((LinearLayout.LayoutParams) v.getLayoutParams()).setMarginStart(dp(14));
        ((LinearLayout.LayoutParams) v.getLayoutParams()).setMarginEnd(dp(14));
        return v;
    }

    private TextView note(String text) {
        TextView tv = Ui.text(this, text, 11.5f, Ui.withAlpha(Ui.RED, 0xDD), false);
        tv.setLineSpacing(dp(3), 1f);
        tv.setPadding(dp(6), dp(9), dp(6), 0);
        return tv;
    }

    // ------------------------------------------------------------------ actions

    private void confirmAutoRecruit(SwitchMaterial sw, boolean value) {
        if (value && !AssistantSettings.autoRecruit(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("开启自动开始招募？")
                    .setMessage("开启后，选好词条/时间会直接点击游戏里的「开始招募」，"
                            + "每次都会消耗一张招募券。\n\n确认要开启吗？")
                    .setNegativeButton("保持关闭", (d, w) -> sw.setChecked(false))
                    .setPositiveButton("确认开启", (d, w) -> {
                        AssistantSettings.setAutoRecruit(this, true);
                        toast("已开启自动开始招募");
                    })
                    .show();
        } else {
            AssistantSettings.setAutoRecruit(this, value);
        }
    }

    private void toggleProjection() {
        if (ProjectionService.isRunning()) {
            ProjectionService.stop(this);
            toast("已停止游戏内助手");
            refreshStatus();
            return;
        }
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        toast("请在「自动识别」页启动游戏内助手");
    }

    private void requestIgnoreBatteryOptimization() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String pkg = getPackageName();
            if (pm != null && pm.isIgnoringBatteryOptimizations(pkg)) {
                toast("已经忽略电池优化");
                return;
            }
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                toast("无法打开电池优化设置");
            }
        }
    }

    private void openAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            toast("请在「耗电管理 / 电池」中允许后台活动");
        } catch (Exception e) {
            toast("无法打开系统设置");
        }
    }

    private void confirmResetDefaults() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("恢复默认设置？")
                .setMessage("助手开关、点击间隔、悬浮球大小/透明度与位置、记录上限都会恢复默认。"
                        + "招募记录不会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (d, w) -> {
                    getSharedPreferences("assistant", Context.MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("manual_state", Context.MODE_PRIVATE).edit().clear().apply();
                    HistoryStore.setMax(this, 80);
                    AssistantAccessibilityService service = AssistantAccessibilityService.get();
                    if (service != null) service.refreshOverlayAppearance();
                    fillContent();
                    toast("已恢复默认设置");
                })
                .show();
    }

    private void confirmClearAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("清空所有数据？")
                .setMessage("将删除全部招募记录，并重置本机设置。此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    new HistoryStore(this).clear();
                    getSharedPreferences("assistant", Context.MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("manual_state", Context.MODE_PRIVATE).edit().clear().apply();
                    AssistantAccessibilityService service = AssistantAccessibilityService.get();
                    if (service != null) service.refreshOverlayAppearance();
                    fillContent();
                    toast("已清空所有数据");
                })
                .show();
    }

    // ------------------------------------------------------------------ status

    private void refreshStatus() {
        if (projectionState == null) return;
        boolean projection = ProjectionService.isRunning();
        projectionState.setText(projection ? "已授权" : "未授权");
        projectionState.setTextColor(projection ? Ui.GREEN : Ui.RED);

        boolean accessibility = AssistantAccessibilityService.isEnabled(this);
        accessibilityState.setText(accessibility ? "已开启" : "未开启");
        accessibilityState.setTextColor(accessibility ? Ui.GREEN : Ui.RED);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryState.setText(battery ? "已豁免" : "未豁免");
        batteryState.setTextColor(battery ? Ui.GREEN : Ui.GOLD);
    }

    private String versionText() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (Exception e) {
            return "1.1.3";
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("无法打开浏览器");
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) return i;
        }
        return 0;
    }

    private interface OnSwitch {
        void onSwitch(SwitchMaterial view, boolean value);
    }

    private interface OnIndexSelect {
        void onSelect(int index);
    }
}
