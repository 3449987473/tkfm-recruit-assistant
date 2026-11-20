package com.tkfm.recruitassistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.tkfm.recruitassistant.core.AutoSelectPlanner;
import com.tkfm.recruitassistant.core.CharacterRepository;
import com.tkfm.recruitassistant.core.HistoryStore;
import com.tkfm.recruitassistant.core.MatchEngine;
import com.tkfm.recruitassistant.core.MatchGroup;
import com.tkfm.recruitassistant.core.OcrAnalyzer;
import com.tkfm.recruitassistant.core.TagCatalog;
import com.tkfm.recruitassistant.core.WorkflowPolicy;
import com.tkfm.recruitassistant.util.Ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hosts the floating assistant, performs OCR-based analysis and dispatches the tap
 * sequence that selects the requested tags and recruit time in the game.
 */
public class AssistantAccessibilityService extends AccessibilityService
        implements AssistantOverlay.Listener {

    private static final String TAG = "AssistantService";
    private static final String PREFS = "assistant";
    private static final String KEY_DELAY = "click_delay_ms";

    private static volatile AssistantAccessibilityService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private AssistantOverlay overlay;
    private MatchEngine matchEngine;
    private HistoryStore history;
    private SharedPreferences prefs;
    private BroadcastReceiver projectionReceiver;
    private int clickDelayMs = 150;

    /** Screen box of the in-game recruit button, cached for the in-sheet recruit action. */
    private volatile AutoSelectPlanner.Box lastStartBox;
    /** True when the latest scan contained the leader tag: never auto-run or auto-recruit. */
    private volatile boolean leaderScenario;

    public static AssistantAccessibilityService get() {
        return instance;
    }

    public static boolean isEnabled(Context context) {
        try {
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) context.getSystemService(
                            Context.ACCESSIBILITY_SERVICE);
            if (am == null || !am.isEnabled()) return false;
            String enabled = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            ComponentName me = new ComponentName(context.getApplicationContext(),
                    AssistantAccessibilityService.class);
            return enabled.contains(me.flattenToString())
                    || enabled.contains(me.flattenToShortString());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        clickDelayMs = AssistantSettings.delay(this);
        matchEngine = new MatchEngine(CharacterRepository.get(this));
        history = new HistoryStore(this);
        overlay = new AssistantOverlay(this, this, clickDelayMs);

        projectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean running = intent != null
                        && intent.getBooleanExtra(ProjectionService.EXTRA_RUNNING, false);
                if (overlay == null) return;
                if (running) {
                    if (!overlay.isPanelVisible() && !busy.get()) overlay.showButton();
                } else {
                    overlay.hideAll();
                }
            }
        };
        ContextCompat.registerReceiver(this, projectionReceiver,
                new IntentFilter(ProjectionService.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        if (ProjectionService.isRunning()) overlay.showButton();
        Log.i(TAG, "assistant connected, projection=" + ProjectionService.isRunning());
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "assistant destroyed");
        if (overlay != null) overlay.destroy();
        if (projectionReceiver != null) {
            try {
                unregisterReceiver(projectionReceiver);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    // --------------------------------------------------------------------- overlay

    @Override
    public void onAnalyzeRequested() {
        if (busy.get()) return;
        if (!ProjectionService.isRunning()) {
            toast("请先在应用内启动游戏内助手并授权录屏");
            openMain();
            return;
        }
        startAnalysis();
    }

    @Override
    public void onCharacterSelected(MatchGroup group) {
        if (busy.get()) return;
        if (group == null) return;
        if (!ProjectionService.isRunning()) {
            toast("录屏已停止，请回到应用重新启动助手");
            openMain();
            return;
        }
        // Leader groups always leave the final recruit tap to the user.
        boolean recruit = AssistantSettings.autoRecruit(this) && !leaderScenario;
        execute(group, recruit, false, null, null);
    }

    @Override
    public void onRecruitRequested() {
        if (busy.get()) return;
        AutoSelectPlanner.Box box = lastStartBox;
        if (box == null) {
            toast("未定位到开始招募按钮，请手动按");
            return;
        }
        if (!busy.compareAndSet(false, true)) return;
        overlay.hideAll();
        toast("正在点击开始招募…");
        main.postDelayed(() -> dispatchTaps(
                java.util.Collections.singletonList(
                        new AutoSelectPlanner.Tap(box.centerX(), box.centerY(), "recruit")),
                () -> {
                    busy.set(false);
                    overlay.setButtonBusy(false);
                    overlay.showButton();
                    overlay.toastLong("已点击开始招募");
                }), 150);
    }

    @Override
    public void onDelayChanged(int delayMs) {
        clickDelayMs = delayMs;
        AssistantSettings.setDelay(this, delayMs);
        Log.i(TAG, "click delay=" + delayMs);
    }

    // ------------------------------------------------------------------- analysis

    private void startAnalysis() {
        if (!busy.compareAndSet(false, true)) return;
        leaderScenario = false;
        overlay.setButtonBusy(true);
        overlay.hideAll();
        toast("正在识别征才词条…");
        // Modes 2/4 act immediately after the scan, so the start button must be located now.
        final boolean autoRun = AssistantSettings.autoRunOnScan(this);
        main.postDelayed(() -> ProjectionService.capture((bitmap, error) -> {
            if (bitmap == null) {
                finish("抓屏失败：" + (error == null ? "未知错误" : error));
                return;
            }
            OcrAnalyzer.recognize(this, bitmap, autoRun, new OcrAnalyzer.Callback() {
                @Override
                public void onResult(OcrAnalyzer.OcrResult result) {
                    List<OcrAnalyzer.DetectedTag> detected = filterTags(result, bitmap);
                    List<String> tags = new ArrayList<>();
                    StringBuilder boxes = new StringBuilder();
                    for (OcrAnalyzer.DetectedTag tag : detected) {
                        tags.add(tag.tag);
                        boxes.append(tag.tag).append(tag.box).append(' ');
                    }
                    Log.i(TAG, "analysis tags=" + tags + " boxes=" + boxes
                            + " dropped=" + (result.tags.size() - detected.size()));
                    if (tags.size() != 5) {
                        finish("分析失败：识别到 " + tags.size() + " 个词条，需要恰好 5 个");
                        return;
                    }
                    List<MatchGroup> groups = matchEngine.analyze(
                            new LinkedHashSet<>(tags));
                    history.add("自动", tags, groups);
                    busy.set(false);
                    overlay.setButtonBusy(false);

                    leaderScenario = WorkflowPolicy.containsLeader(tags);
                    boolean autoRecruit = AssistantSettings.autoRecruit(
                            AssistantAccessibilityService.this);
                    WorkflowPolicy.Action action =
                            WorkflowPolicy.decide(autoRun, autoRecruit, tags);
                    lastStartBox = toBox(result.startButtonBox);

                    AssistantOverlay.PanelSpec spec = new AssistantOverlay.PanelSpec();
                    spec.showRecruit = action == WorkflowPolicy.Action.AUTO_SELECT_CONFIRM
                            && !leaderScenario;
                    spec.recruitEnabled = lastStartBox != null;
                    if (leaderScenario) {
                        spec.hint = "含领袖词条，请手动点结果；最后一步请手动按招募";
                        spec.hintColor = Ui.withAlpha(Ui.RED, 0xDD);
                    } else if (action == WorkflowPolicy.Action.AUTO_SELECT_CONFIRM) {
                        spec.hint = spec.recruitEnabled
                                ? "已自动选好词条和时间，点上方「开始招募」"
                                : "未定位到开始招募按钮，请手动按";
                        spec.hintColor = spec.recruitEnabled
                                ? Ui.TEXT_DIM : Ui.withAlpha(Ui.RED, 0xDD);
                    } else if (action == WorkflowPolicy.Action.AUTO_SELECT_AND_RECRUIT) {
                        spec.hint = "正在自动选词条/时间并招募…";
                    } else {
                        spec.hint = autoRecruit
                                ? "点角色 = 自动改词条/时间并开始招募"
                                : "点角色 = 自动改词条/时间；招募键仍需自己按";
                    }
                    overlay.showPanel(groups, spec);
                    Log.i(TAG, "analysis groups=" + groups.size()
                            + " action=" + action + " leader=" + leaderScenario);

                    if (leaderScenario) {
                        overlay.toast("含领袖词条，请手动选择结果");
                        return;
                    }
                    if (action == WorkflowPolicy.Action.SHOW_ONLY) {
                        return;
                    }
                    if (groups.isEmpty()) {
                        overlay.toast("没有匹配角色");
                        return;
                    }
                    execute(groups.get(0), autoRecruit, true, bitmap, result);
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "ocr failed", e);
                    finish("识别失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
                }
            });
        }), 260);
    }

    /**
     * Runs the tap plan for one group.
     *
     * @param recruit  click the in-game recruit button after selecting tags/time
     * @param fromScan true for modes 2/4 (keep the result sheet during execution)
     * @param cachedBitmap / cachedResult reuse the scan frame instead of capturing again
     */
    private void execute(MatchGroup group, boolean recruit, boolean fromScan,
                         Bitmap cachedBitmap, OcrAnalyzer.OcrResult cachedResult) {
        if (!busy.compareAndSet(false, true)) return;
        if (!fromScan) {
            overlay.hideAll();
            toast("正在点选：" + group.comboText());
        }
        if (cachedBitmap != null && cachedResult != null) {
            // Acting within a second of the scan: the frame is still valid, skip a second OCR.
            main.postDelayed(() -> applyPlan(group, recruit, fromScan,
                    cachedBitmap, cachedResult), 120);
            return;
        }
        main.postDelayed(() -> ProjectionService.capture((bitmap, error) -> {
            if (bitmap == null) {
                finish("抓屏失败：" + (error == null ? "未知错误" : error));
                return;
            }
            OcrAnalyzer.recognize(this, bitmap, recruit, new OcrAnalyzer.Callback() {
                @Override
                public void onResult(OcrAnalyzer.OcrResult result) {
                    applyPlan(group, recruit, fromScan, bitmap, result);
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "auto-select ocr failed", e);
                    finish("点选前识别失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
                }
            });
        }), 260);
    }

    private void applyPlan(MatchGroup group, boolean recruit, boolean fromScan,
                           Bitmap bitmap, OcrAnalyzer.OcrResult result) {
        List<AutoSelectPlanner.TagHit> hits = new ArrayList<>();
        Set<String> selected = new LinkedHashSet<>();
        for (OcrAnalyzer.DetectedTag tag : filterTags(result, bitmap)) {
            hits.add(new AutoSelectPlanner.TagHit(tag.tag,
                    new AutoSelectPlanner.Box(tag.box.left, tag.box.top,
                            tag.box.right, tag.box.bottom)));
            boolean on = isTagSelected(bitmap, tag.box);
            if (on) selected.add(tag.tag);
            Log.d(TAG, "probe " + tag.tag + " raw=" + tag.raw
                    + " box=" + tag.box + " selected=" + on);
        }
        OcrAnalyzer.TimeMark time = pickTime(result.times, bitmap.getHeight());
        Integer hour = time == null ? null : time.value;
        AutoSelectPlanner.Box timeBox = time == null ? null
                : new AutoSelectPlanner.Box(time.box.left, time.box.top,
                        time.box.right, time.box.bottom);
        AutoSelectPlanner.Plan plan = AutoSelectPlanner.build(
                group.combo, hits, selected, hour, timeBox, true, true);
        AutoSelectPlanner.Box startBox = toBox(result.startButtonBox);
        if (startBox != null) lastStartBox = startBox;
        Log.i(TAG, "selected=" + selected + " hour=" + hour
                + " recruit=" + recruit + " fromScan=" + fromScan
                + " startBox=" + startBox + " plan=" + plan.summary()
                + " taps=" + plan.taps);
        if (!plan.missing.isEmpty()) {
            Log.w(TAG, "missing tag boxes: " + plan.missing);
            finish("没有找到词条位置：" + plan.missing);
            return;
        }
        if (plan.taps.isEmpty() && !recruit) {
            complete(group, false, fromScan, false);
            return;
        }
        Runnable recruitTap = () -> dispatchTaps(
                java.util.Collections.singletonList(
                        new AutoSelectPlanner.Tap(startBox.centerX(), startBox.centerY(), "recruit")),
                () -> complete(group, true, fromScan, true));
        // Mode 4 keeps the sheet open while selecting.  It covers the in-game recruit button
        // at the bottom of the screen, so remove it before dispatching that tap.
        Runnable closeThenRecruit = () -> {
            if (fromScan) {
                overlay.hideAll();
                main.postDelayed(recruitTap, 400);
            } else {
                main.postDelayed(recruitTap, 500);
            }
        };
        if (recruit && startBox != null) {
            if (plan.taps.isEmpty()) {
                closeThenRecruit.run();
            } else {
                dispatchTaps(plan.taps, closeThenRecruit);
            }
        } else {
            dispatchTaps(plan.taps, () -> complete(group, recruit, fromScan, false));
        }
    }

    private void complete(MatchGroup group, boolean recruit, boolean fromScan, boolean recruited) {
        busy.set(false);
        overlay.setButtonBusy(false);
        if (fromScan) {
            if (recruit) {
                // Mode 4: collapse the sheet and report the outcome.
                overlay.hidePanel();
                overlay.showBanner(recruited ? group.resultSummary()
                        : "已选好词条和时间，未找到开始招募按钮，请手动按");
            } else if (lastStartBox != null) {
                // Mode 2: sheet stays open, the recruit button is the next step.
                overlay.toast("已选好词条和时间，点上方「开始招募」");
            } else {
                overlay.toast("已选好词条和时间，未定位到开始招募按钮，请手动按");
            }
            return;
        }
        overlay.showButton();
        if (recruited) {
            overlay.toastLong("已点击开始招募");
        } else if (recruit) {
            overlay.toast("未找到开始招募按钮，请手动按");
        } else {
            overlay.toast("已选好词条和时间，请按招募");
        }
    }

    private static AutoSelectPlanner.Box toBox(Rect rect) {
        if (rect == null) return null;
        return new AutoSelectPlanner.Box(rect.left, rect.top, rect.right, rect.bottom);
    }

    /**
     * Ignore text that OCR matched below the recruit panel (footer counters, ads, or the
     * result sheet itself).  The real panel always sits in the upper three quarters.
     */
    private List<OcrAnalyzer.DetectedTag> filterTags(OcrAnalyzer.OcrResult result,
                                                     Bitmap bitmap) {
        List<OcrAnalyzer.DetectedTag> out = new ArrayList<>();
        int maxTop = Math.round(bitmap.getHeight() * 0.78f);
        int maxWidth = Math.round(bitmap.getWidth() * 0.35f);
        for (OcrAnalyzer.DetectedTag tag : result.tags) {
            if (tag.box == null || tag.box.height() <= 0) continue;
            if (tag.box.top < 0 || tag.box.top > maxTop) continue;
            if (tag.box.bottom > bitmap.getHeight()) continue;
            // A line-level fallback can span an entire row; real chips are narrow.
            if (tag.box.width() > maxWidth) continue;
            out.add(tag);
        }
        return out;
    }

    private OcrAnalyzer.TimeMark pickTime(List<OcrAnalyzer.TimeMark> times, int bitmapHeight) {
        OcrAnalyzer.TimeMark best = null;
        for (OcrAnalyzer.TimeMark mark : times) {
            if (mark.value < 1 || mark.value > 9) continue;
            if (mark.box.top > bitmapHeight * 0.45f) continue;
            if (best == null || mark.box.top < best.box.top) best = mark;
        }
        return best;
    }

    /**
     * The game marks a selected tag with a dark pixel just left of its label.  Sampling a
     * few points along that edge is more stable than the reference's single pixel.
     */
    private boolean isTagSelected(Bitmap bitmap, Rect box) {
        int x = box.left - Math.max(5, Math.max(3, box.height() / 8));
        int[] ys = {box.top, box.centerY(), box.bottom - 1};
        int dark = 0;
        int valid = 0;
        for (int y : ys) {
            if (x < 0 || y < 0 || x >= bitmap.getWidth() || y >= bitmap.getHeight()) continue;
            valid++;
            if (bitmap.getPixel(x, y) < -10000000) dark++;
        }
        return valid > 0 && dark * 2 >= valid;
    }

    // --------------------------------------------------------------------- taps

    private void dispatchTaps(List<AutoSelectPlanner.Tap> taps, Runnable onDone) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            finish("Android 7.0 以下不支持自动点选");
            return;
        }
        tapAt(taps, 0, onDone);
    }

    private void tapAt(List<AutoSelectPlanner.Tap> taps, int index, Runnable onDone) {
        if (index >= taps.size()) {
            onDone.run();
            return;
        }
        AutoSelectPlanner.Tap tap = taps.get(index);
        Path path = new Path();
        path.moveTo(tap.x, tap.y);
        GestureDescription description = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                .build();
        boolean accepted;
        try {
            accepted = dispatchGesture(description, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d(TAG, "tap done " + tap);
                    main.postDelayed(() -> tapAt(taps, index + 1, onDone),
                            AssistantSettings.delay(AssistantAccessibilityService.this));
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.w(TAG, "tap cancelled " + tap);
                    busy.set(false);
                    overlay.setButtonBusy(false);
                    overlay.showButton();
                    toast("自动点选被取消");
                }
            }, null);
        } catch (Exception e) {
            Log.e(TAG, "dispatchGesture failed", e);
            accepted = false;
        }
        if (!accepted) {
            busy.set(false);
            overlay.setButtonBusy(false);
            overlay.showButton();
            toast("无法执行点击，请确认无障碍服务已开启");
        }
    }

    // --------------------------------------------------------------------- misc

    /** Called by the settings screen after the floating button options changed. */
    public void refreshOverlayAppearance() {
        if (overlay != null) overlay.refreshAppearance();
    }

    public void resetOverlayPosition() {
        if (overlay != null) overlay.resetButtonPosition();
    }

    private void finish(String message) {
        busy.set(false);
        if (overlay != null) {
            overlay.setButtonBusy(false);
            if (ProjectionService.isRunning()) {
                overlay.showButton();
            } else {
                overlay.hideAll();
            }
            overlay.toast(message);
        }
        Log.w(TAG, message);
    }

    private void toast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void openMain() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "start MainActivity failed", e);
        }
    }
}
