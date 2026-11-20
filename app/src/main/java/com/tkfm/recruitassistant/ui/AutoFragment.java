package com.tkfm.recruitassistant.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tkfm.recruitassistant.AssistantAccessibilityService;
import com.tkfm.recruitassistant.CaptureService;
import com.tkfm.recruitassistant.DemoPanelActivity;
import com.tkfm.recruitassistant.SettingsActivity;
import com.tkfm.recruitassistant.MainActivity;
import com.tkfm.recruitassistant.ProjectionService;
import com.tkfm.recruitassistant.R;
import com.tkfm.recruitassistant.core.CharacterRepository;
import com.tkfm.recruitassistant.core.HistoryStore;
import com.tkfm.recruitassistant.core.MatchEngine;
import com.tkfm.recruitassistant.core.MatchGroup;
import com.tkfm.recruitassistant.core.OcrAnalyzer;
import com.tkfm.recruitassistant.util.Ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AutoFragment extends Fragment {

    private TagPickerView picker;
    private LinearLayout results;
    private LinearLayout recognizedCard;
    private LinearLayout rawCard;
    private TextView status;
    private TextView recognizedBadge;
    private TextView rawText;
    private TextView btnRaw;
    private ImageView preview;

    private MatchEngine engine;
    private HistoryStore history;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Intent> projectionLauncher;
    private ActivityResultLauncher<Intent> assistantProjectionLauncher;
    private BroadcastReceiver captureReceiver;
    private BroadcastReceiver projectionStateReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Bitmap currentBitmap;
    private String pendingPath;
    private boolean rawVisible;
    private TextView assistantStatus;
    private TextView btnAssistant;
    private TextView btnStopAssistant;
    private TextView btnBackgroundSettings;
    private TextView btnCapture;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auto, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        engine = new MatchEngine(CharacterRepository.get(context));
        history = new HistoryStore(context);
        picker = view.findViewById(R.id.autoPicker);
        results = view.findViewById(R.id.resultsContainer);
        recognizedCard = view.findViewById(R.id.recognizedCard);
        rawCard = view.findViewById(R.id.rawCard);
        status = view.findViewById(R.id.statusText);
        recognizedBadge = view.findViewById(R.id.recognizedBadge);
        rawText = view.findViewById(R.id.rawText);
        btnRaw = view.findViewById(R.id.btnRaw);
        preview = view.findViewById(R.id.previewImage);
        assistantStatus = view.findViewById(R.id.assistantStatus);
        btnAssistant = view.findViewById(R.id.btnAssistant);
        btnStopAssistant = view.findViewById(R.id.btnStopAssistant);
        btnBackgroundSettings = view.findViewById(R.id.btnBackgroundSettings);
        btnCapture = view.findViewById(R.id.btnCapture);

        picker.setOnSelectionChanged(this::renderSelection);

        assistantProjectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        Toast.makeText(requireContext(), "已取消录屏授权", Toast.LENGTH_SHORT).show();
                        updateAssistantStatus();
                        return;
                    }
                    ProjectionService.start(requireContext(), result.getResultCode(),
                            result.getData());
                    updateAssistantStatus();
                    if (!AssistantAccessibilityService.isEnabled(requireContext())) {
                        promptAccessibility();
                    } else {
                        handoffToGame();
                    }
                });

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) loadFromUri(uri);
                });

        projectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        status.setText("已取消屏幕录制权限，未进行截屏。");
                        return;
                    }
                    status.setText("3 秒后截取屏幕，请现在切换到游戏的全境征才页面…");
                    Toast.makeText(requireContext(), "3 秒后截屏，请切换到游戏", Toast.LENGTH_LONG).show();
                    // Start the foreground capture service while we are still visible, then
                    // leave the app so the game becomes the captured surface.
                    CaptureService.start(requireContext(), result.getResultCode(),
                            result.getData(), "auto", 3000);
                    Activity activity = getActivity();
                    if (activity != null) activity.moveTaskToBack(true);
                });

        btnCapture.setOnClickListener(v -> {
            if (ProjectionService.isRunning()) {
                Toast.makeText(requireContext(),
                        "游戏内助手运行中，请用悬浮球识别；或先停止助手。", Toast.LENGTH_LONG).show();
                return;
            }
            confirmLiveCapture();
        });
        view.findViewById(R.id.btnGallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));
        view.findViewById(R.id.btnSample).setOnClickListener(v -> loadSample());
        view.findViewById(R.id.btnDemo).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DemoPanelActivity.class)));
        btnAssistant.setOnClickListener(v -> startAssistant());
        btnStopAssistant.setOnClickListener(v -> {
            ProjectionService.stop(requireContext());
            updateAssistantStatus();
        });
        btnBackgroundSettings.setOnClickListener(v -> showBackgroundGuide());
        view.findViewById(R.id.btnAssistantSettings).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
        btnRaw.setOnClickListener(v -> {
            rawVisible = !rawVisible;
            rawCard.setVisibility(rawVisible ? View.VISIBLE : View.GONE);
            btnRaw.setText(rawVisible ? "收起详情" : "识别详情");
        });

        captureReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if ("demo".equals(intent.getStringExtra(CaptureService.EXTRA_SOURCE))) {
                    return; // the demo activity opens MainActivity with the path itself
                }
                String path = intent.getStringExtra(CaptureService.EXTRA_PATH);
                String error = intent.getStringExtra(CaptureService.EXTRA_ERROR);
                if (path != null) {
                    openCapturedImage(path);
                    try {
                        Intent open = new Intent(requireContext(), MainActivity.class);
                        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(open);
                    } catch (Exception ignored) {
                    }
                } else {
                    status.setText("截屏失败：" + (error == null ? "未知错误" : error));
                    Toast.makeText(requireContext(), "截屏失败", Toast.LENGTH_SHORT).show();
                }
            }
        };
        ContextCompat.registerReceiver(requireContext(), captureReceiver,
                new IntentFilter(CaptureService.ACTION_CAPTURE_DONE),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        projectionStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                updateAssistantStatus();
            }
        };
        ContextCompat.registerReceiver(requireContext(), projectionStateReceiver,
                new IntentFilter(ProjectionService.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        updateAssistantStatus();

        if (pendingPath != null) {
            String path = pendingPath;
            pendingPath = null;
            openCapturedImage(path);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAssistantStatus();
    }

    @Override
    public void onStart() {
        super.onStart();
        updateAssistantStatus();
    }

    private void startAssistant() {
        if (ProjectionService.isRunning()) {
            if (!AssistantAccessibilityService.isEnabled(requireContext())) {
                promptAccessibility();
            } else {
                handoffToGame();
            }
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("启动游戏内助手")
                .setMessage("接下来会请求一次屏幕录制权限。授权后回到游戏，点悬浮球即可分析；"
                        + "点结果里的角色会自动改词条和招募时间。\n\n"
                        + "首次使用还需要开启无障碍；录屏只在本次会话内有效，可在通知或本页停止。\n\n"
                        + "真我/OPPO 等系统请先在「后台运行设置」里允许后台活动，"
                        + "否则系统可能在一次识别后中断录屏会话。")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (d, w) -> {
                    MediaProjectionManager manager = (MediaProjectionManager)
                            requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    assistantProjectionLauncher.launch(manager.createScreenCaptureIntent());
                })
                .show();
    }

    private void promptAccessibility() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("开启无障碍服务")
                .setMessage("游戏内助手需要「无障碍」权限来显示悬浮球并自动点选词条。"
                        + "请在系统设置中找到「全境征才助手 · 游戏内助手」并开启。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("去开启", (d, w) -> {
                    try {
                        startActivity(new Intent(
                                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {
                    }
                })
                .show();
    }

    private void showBackgroundGuide() {
        Context context = requireContext();
        LinearLayout content = Ui.vertical(context);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 4),
                Ui.dp(context, 20), 0);
        TextView message = Ui.text(context,
                "真我/OPPO 等系统：设置 → 电池 → 应用耗电管理 → 全境征才助手 → "
                        + "允许后台活动；并在最近任务中锁定本应用。\n\n"
                        + "如果仍然在一次识别后失去录屏会话，请再允许忽略电池优化。",
                13, Ui.TEXT_DIM, false);
        message.setLineSpacing(Ui.dp(context, 3), 1f);
        content.addView(message);

        TextView appDetails = guideRow(context, "应用耗电管理（允许后台活动）");
        TextView battery = guideRow(context, "忽略电池优化（更强）");
        content.addView(appDetails);
        content.addView(battery);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("后台运行设置")
                .setView(content)
                .setNegativeButton("取消", null)
                .create();
        appDetails.setOnClickListener(v -> {
            dialog.dismiss();
            openAppDetails();
        });
        battery.setOnClickListener(v -> {
            dialog.dismiss();
            requestIgnoreBatteryOptimization();
        });
        dialog.show();
    }

    private TextView guideRow(Context context, String text) {
        TextView row = Ui.text(context, text, 14, Ui.PRIMARY, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 14), Ui.dp(context, 13),
                Ui.dp(context, 14), Ui.dp(context, 13));
        row.setBackground(Ui.ripple(context, Ui.SURFACE_2, Ui.STROKE, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(context, 10);
        row.setLayoutParams(lp);
        return row;
    }

    private void openAppDetails() {
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
            Toast.makeText(requireContext(),
                    "请在「耗电管理 / 电池」中允许后台活动", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开系统设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestIgnoreBatteryOptimization() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) requireContext()
                    .getSystemService(Context.POWER_SERVICE);
            String pkg = requireContext().getPackageName();
            if (pm != null && pm.isIgnoringBatteryOptimizations(pkg)) {
                Toast.makeText(requireContext(), "已经忽略电池优化", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(requireContext(), "无法打开电池优化设置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handoffToGame() {
        Toast.makeText(requireContext(), "助手已就绪，回到游戏点悬浮球", Toast.LENGTH_LONG).show();
        Activity activity = getActivity();
        if (activity != null) activity.moveTaskToBack(true);
    }

    private void updateAssistantStatus() {
        if (assistantStatus == null || !isAdded()) return;
        boolean accessibility = AssistantAccessibilityService.isEnabled(requireContext());
        boolean projection = ProjectionService.isRunning();
        String text = "无障碍：" + (accessibility ? "已开启" : "未开启")
                + " · 录屏：" + (projection ? "已授权" : "未授权");
        if (accessibility && projection) {
            text += "\n已就绪：回到游戏，点悬浮球分析。";
        } else if (projection) {
            text += "\n还差无障碍权限，开启后悬浮球才会出现。";
        } else {
            text += "\n点击下方按钮启动；录屏授权一次后本次会话内复用。";
        }
        assistantStatus.setText(text);
        assistantStatus.setTextColor(accessibility && projection
                ? ContextCompat.getColor(requireContext(), R.color.accent)
                : ContextCompat.getColor(requireContext(), R.color.text_dim));
        if (btnAssistant != null) {
            btnAssistant.setText(accessibility && projection
                    ? "助手已就绪（回到游戏）" : "启动游戏内助手");
        }
        if (btnStopAssistant != null) {
            btnStopAssistant.setVisibility(projection ? View.VISIBLE : View.GONE);
        }
        if (btnCapture != null) {
            btnCapture.setAlpha(projection ? 0.45f : 1f);
        }
    }

    private void confirmLiveCapture() {
        MediaProjectionManager manager = (MediaProjectionManager)
                requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("截屏识别")
                .setMessage("点击「开始」后，系统会请求一次屏幕录制权限。授权后 3 秒自动截屏，"
                        + "请在这 3 秒内切换到游戏的全境征才页面。\n\n只抓取一帧，图片仅保存在本机缓存。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始", (d, w) -> {
                    status.setText("等待屏幕录制授权…");
                    projectionLauncher.launch(manager.createScreenCaptureIntent());
                })
                .show();
    }

    private void loadSample() {
        try (InputStream in = requireContext().getAssets().open("sample_recruit.jpg")) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) {
                status.setText("示例图读取失败。");
                return;
            }
            analyzeBitmap(bitmap, "示例图");
        } catch (Exception e) {
            status.setText("示例图读取失败：" + e.getMessage());
        }
    }

    private void loadFromUri(Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) {
                status.setText("图片读取失败，请换一张图片。");
                return;
            }
            analyzeBitmap(bitmap, "图片");
        } catch (Exception e) {
            status.setText("图片读取失败：" + e.getMessage());
        }
    }

    public void openCapturedImage(String path) {
        if (path == null) return;
        if (picker == null) {
            pendingPath = path;
            return;
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) {
                status.setText("截图读取失败。");
                return;
            }
            analyzeBitmap(bitmap, "截屏");
        } catch (Exception e) {
            status.setText("截图读取失败：" + e.getMessage());
        }
    }

    private void analyzeBitmap(Bitmap bitmap, String source) {
        currentBitmap = bitmap;
        preview.setImageBitmap(bitmap);
        preview.setVisibility(View.VISIBLE);
        status.setText("正在识别" + source + "中的词条…");
        recognizedCard.setVisibility(View.VISIBLE);
        recognizedBadge.setText("识别中");
        results.removeAllViews();
        rawCard.setVisibility(View.GONE);
        rawVisible = false;
        btnRaw.setText("识别详情");

        OcrAnalyzer.recognize(requireContext(), bitmap, new OcrAnalyzer.Callback() {
            @Override
            public void onResult(OcrAnalyzer.OcrResult result) {
                if (!isAdded()) return;
                List<String> names = result.tagNames();
                rawText.setText(result.rawText.isEmpty() ? "（无文本）" : result.rawText);
                recognizedCard.setVisibility(View.VISIBLE);
                recognizedBadge.setText(names.size() + " 个");
                if (names.isEmpty()) {
                    status.setText("没有识别到词条。请确认截图包含完整的 5 个招募条件；"
                            + "也可以点「识别详情」查看 OCR 原文，或改用图片识别/手动计算。");
                    recognizedBadge.setText("0 个");
                    results.removeAllViews();
                    ResultsRenderer.render(requireContext(), results, Collections.emptyList(),
                            Collections.emptyList(), null);
                    return;
                }
                List<String> chosen = new ArrayList<>(names.subList(0, Math.min(5, names.size())));
                picker.setSelection(chosen);
                List<MatchGroup> groups = engine.analyze(new java.util.LinkedHashSet<>(chosen));
                if (names.size() == 5) {
                    status.setText("已识别 5 个词条并完成分析。点击结果中的角色可查看组合详情。");
                } else if (names.size() > 5) {
                    status.setText("识别到 " + names.size() + " 个候选词条，已取前 5 个。"
                            + "可在下方增删词条后重新计算。");
                } else {
                    status.setText("只识别到 " + names.size() + " 个词条（游戏面板应有 5 个），"
                            + "已用现有词条计算。请在下方补充或调整词条。");
                }
                if (names.size() == 5) history.add("自动", chosen, groups);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                status.setText("识别失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
                Toast.makeText(requireContext(), "OCR 识别失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderSelection(Set<String> selected) {
        if (engine == null) return;
        List<String> tags = new ArrayList<>(selected);
        if (currentBitmap != null) {
            status.setText("已选择 " + tags.size() + " 个词条，结果已重新计算。");
        }
        List<MatchGroup> groups = engine.analyze(selected);
        ResultsRenderer.render(requireContext(), results, groups, tags,
                (group, character) -> Toast.makeText(requireContext(),
                        character.displayName() + " · " + group.comboText(),
                        Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (captureReceiver != null) {
            Context context = getContext();
            if (context != null) {
                try {
                    context.unregisterReceiver(captureReceiver);
                } catch (Exception ignored) {
                }
            }
            captureReceiver = null;
        }
        if (projectionStateReceiver != null) {
            Context context = getContext();
            if (context != null) {
                try {
                    context.unregisterReceiver(projectionStateReceiver);
                } catch (Exception ignored) {
                }
            }
            projectionStateReceiver = null;
        }
    }
}
