package com.tkfm.recruitassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tkfm.recruitassistant.core.Character;
import com.tkfm.recruitassistant.core.MatchGroup;
import com.tkfm.recruitassistant.ui.FlowLayout;
import com.tkfm.recruitassistant.util.Ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The in-game layer: a draggable focus button and a bottom result sheet.  Both windows use
 * TYPE_ACCESSIBILITY_OVERLAY, so the assistant only needs the accessibility permission.
 */
public class AssistantOverlay {

    public interface Listener {
        void onAnalyzeRequested();

        void onCharacterSelected(MatchGroup group);

        void onDelayChanged(int delayMs);

        void onRecruitRequested();
    }

    /** What the result sheet shows below the header. */
    public static final class PanelSpec {
        public String hint = "";
        public int hintColor = Ui.TEXT_DIM;
        public boolean showRecruit;
        public boolean recruitEnabled = true;
    }

    private static final String PREFS = "assistant";
    private static final String KEY_X = "button_x";
    private static final String KEY_Y = "button_y";

    private final Context context;
    private final WindowManager windowManager;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final int screenWidth;
    private final int screenHeight;
    private int buttonSize;
    private int buttonAlpha;
    private final SharedPreferences prefs;

    private View buttonView;
    private ImageView buttonIcon;
    private ProgressBar buttonProgress;
    private WindowManager.LayoutParams buttonParams;
    private boolean buttonAttached;

    private View panelView;
    private boolean panelAttached;
    private int delayMs;

    private View bannerView;
    private boolean bannerAttached;
    private Runnable bannerTimeout;

    public AssistantOverlay(Context context, Listener listener, int delayMs) {
        this.context = context;
        this.listener = listener;
        this.delayMs = delayMs;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
            metrics.widthPixels = bounds.width();
            metrics.heightPixels = bounds.height();
            metrics.densityDpi = context.getResources().getDisplayMetrics().densityDpi;
        } else {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        buttonSize = dp(AssistantSettings.ballSize(context));
        buttonAlpha = AssistantSettings.ballAlpha(context);
    }

    public int getDelayMs() {
        return delayMs;
    }

    public boolean isPanelVisible() {
        return panelAttached;
    }

    public boolean isButtonVisible() {
        return buttonAttached;
    }

    public void showButton() {
        if (panelAttached) return;
        if (buttonView == null) buildButton();
        if (!buttonAttached) {
            try {
                windowManager.addView(buttonView, buttonParams);
                buttonAttached = true;
            } catch (Exception ignored) {
            }
        }
    }

    public void hideButton() {
        if (buttonAttached && buttonView != null) {
            try {
                windowManager.removeViewImmediate(buttonView);
            } catch (Exception ignored) {
            }
            buttonAttached = false;
        }
    }

    public void setButtonBusy(boolean busy) {
        if (buttonIcon != null) buttonIcon.setVisibility(busy ? View.GONE : View.VISIBLE);
        if (buttonProgress != null) {
            buttonProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
    }

    public void showPanel(List<MatchGroup> groups, PanelSpec spec) {
        hideButton();
        removePanel();
        panelView = buildPanel(groups, spec);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                Math.round(screenHeight * 0.48f),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;
        try {
            windowManager.addView(panelView, params);
            panelAttached = true;
        } catch (Exception ignored) {
            panelAttached = false;
        }
    }

    public void hidePanel() {
        removePanel();
        showButton();
    }

    public void hideAll() {
        removePanel();
        hideButton();
    }

    public void destroy() {
        hideAll();
        removeBanner();
    }

    /** Rebuilds the floating button after the size/opacity settings changed. */
    public void refreshAppearance() {
        boolean visible = buttonAttached;
        hideButton();
        buttonView = null;
        buttonIcon = null;
        buttonProgress = null;
        buttonSize = dp(AssistantSettings.ballSize(context));
        buttonAlpha = AssistantSettings.ballAlpha(context);
        if (visible) showButton();
    }

    public void resetButtonPosition() {
        prefs.edit().remove(KEY_X).remove(KEY_Y).apply();
        if (buttonView == null || buttonParams == null) return;
        buttonParams.x = clamp(screenWidth - buttonSize - dp(8), dp(2),
                screenWidth - buttonSize - dp(2));
        buttonParams.y = clamp(Math.round(screenHeight * 0.32f), dp(40),
                screenHeight - buttonSize - dp(60));
        try {
            windowManager.updateViewLayout(buttonView, buttonParams);
        } catch (Exception ignored) {
        }
    }

    public void toast(String text) {
        try {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    public void toastLong(String text) {
        try {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
        }
    }

    /**
     * Bottom banner used for the automatic-recruit summary.  Replaces the previous banner
     * instead of queueing like Toast, so two quick recruits both show their result.
     */
    public void showBanner(String text) {
        removeBanner();
        boolean guaranteed = text != null && text.contains("必出");
        TextView view = Ui.text(context, text, 12.5f,
                guaranteed ? 0xFFC0392B : 0xFF1F2430, guaranteed);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF4F6FB);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0xFFCBD3E3);
        view.setBackground(bg);
        view.setElevation(dp(10));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(100);
        try {
            windowManager.addView(view, params);
            bannerView = view;
            bannerAttached = true;
        } catch (Exception ignored) {
        }
        bannerTimeout = this::removeBanner;
        main.postDelayed(bannerTimeout, 3000);
    }

    private void removeBanner() {
        if (bannerTimeout != null) {
            main.removeCallbacks(bannerTimeout);
            bannerTimeout = null;
        }
        if (bannerAttached && bannerView != null) {
            try {
                windowManager.removeViewImmediate(bannerView);
            } catch (Exception ignored) {
            }
        }
        bannerAttached = false;
        bannerView = null;
    }

    private void removePanel() {
        if (panelAttached && panelView != null) {
            try {
                windowManager.removeViewImmediate(panelView);
            } catch (Exception ignored) {
            }
        }
        panelAttached = false;
        panelView = null;
    }

    private void buildButton() {
        FrameLayout box = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFFFFFFFF);
        bg.setStroke(dp(1), Ui.PRIMARY);
        box.setBackground(bg);
        box.setElevation(dp(8));
        box.setAlpha(buttonAlpha / 100f);

        buttonIcon = new ImageView(context);
        buttonIcon.setImageResource(R.drawable.ic_scan);
        buttonIcon.setColorFilter(0xFF1A2540);
        box.addView(buttonIcon, new FrameLayout.LayoutParams(
                dp(26), dp(26), Gravity.CENTER));

        buttonProgress = new ProgressBar(context);
        buttonProgress.setIndeterminate(true);
        buttonProgress.setIndeterminateTintList(ColorStateList.valueOf(0xFF1A2540));
        buttonProgress.setVisibility(View.GONE);
        box.addView(buttonProgress, new FrameLayout.LayoutParams(
                dp(28), dp(28), Gravity.CENTER));

        int savedX = prefs.getInt(KEY_X, screenWidth - buttonSize - dp(8));
        int savedY = prefs.getInt(KEY_Y, Math.round(screenHeight * 0.32f));
        buttonParams = new WindowManager.LayoutParams(
                buttonSize, buttonSize,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        buttonParams.gravity = Gravity.TOP | Gravity.START;
        buttonParams.x = clamp(savedX, dp(2), screenWidth - buttonSize - dp(2));
        buttonParams.y = clamp(savedY, dp(40), screenHeight - buttonSize - dp(60));

        box.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int downX;
            private int downY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downX = buttonParams.x;
                        downY = buttonParams.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - downRawX);
                        int dy = Math.round(event.getRawY() - downRawY);
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) dragging = true;
                        if (dragging) {
                            buttonParams.x = clamp(downX + dx, dp(2),
                                    screenWidth - buttonSize - dp(2));
                            buttonParams.y = clamp(downY + dy, dp(40),
                                    screenHeight - buttonSize - dp(60));
                            try {
                                windowManager.updateViewLayout(buttonView, buttonParams);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) {
                            int targetX = buttonParams.x + buttonSize / 2 < screenWidth / 2
                                    ? dp(4) : screenWidth - buttonSize - dp(4);
                            buttonParams.x = targetX;
                            try {
                                windowManager.updateViewLayout(buttonView, buttonParams);
                            } catch (Exception ignored) {
                            }
                            prefs.edit().putInt(KEY_X, buttonParams.x)
                                    .putInt(KEY_Y, buttonParams.y).apply();
                        } else {
                            listener.onAnalyzeRequested();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        buttonView = box;
    }

    private View buildPanel(List<MatchGroup> groups, PanelSpec spec) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF70D1322);
        float radius = dp(18);
        bg.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        bg.setStroke(dp(1), Ui.STROKE);
        root.setBackground(bg);
        root.setElevation(dp(14));
        root.setPadding(dp(12), dp(10), dp(12), dp(8));

        int unique = 0;
        Set<Integer> seen = new HashSet<>();
        for (MatchGroup g : groups) {
            for (Character c : g.characters) seen.add(c.no);
        }
        unique = seen.size();

        LinearLayout header = Ui.horizontal(context);
        LinearLayout titles = Ui.vertical(context);
        titles.addView(Ui.text(context, "征才分析", 16, Ui.TEXT, true));
        titles.addView(Ui.text(context, groups.size() + " 组组合 · " + unique + " 名候选",
                10.5f, Ui.TEXT_DIM, false));
        header.addView(titles, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (spec.showRecruit) {
            TextView recruit = Ui.text(context, "开始招募", 10.5f, Ui.ACCENT, true);
            recruit.setGravity(Gravity.CENTER);
            recruit.setPadding(dp(10), dp(5), dp(10), dp(5));
            recruit.setBackground(Ui.ripple(context, Ui.withAlpha(Ui.ACCENT, 0x1C),
                    Ui.withAlpha(Ui.ACCENT, 0x66), 9));
            if (!spec.recruitEnabled) {
                recruit.setEnabled(false);
                recruit.setAlpha(0.4f);
            } else {
                recruit.setOnClickListener(v -> {
                    v.setEnabled(false);
                    v.setAlpha(0.6f);
                    ((TextView) v).setText("招募中…");
                    listener.onRecruitRequested();
                });
            }
            LinearLayout.LayoutParams recruitLp = Ui.lp(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            recruitLp.setMarginEnd(dp(5));
            header.addView(recruit, recruitLp);
        }

        TextView delay = Ui.text(context, delayMs + "ms", 10.5f, Ui.PRIMARY, true);
        delay.setGravity(Gravity.CENTER);
        delay.setPadding(dp(9), dp(5), dp(9), dp(5));
        delay.setBackground(Ui.ripple(context, Ui.SURFACE_2, Ui.STROKE, 9));
        delay.setOnClickListener(v -> {
            int next = delayMs == 100 ? 150 : delayMs == 150 ? 250
                    : delayMs == 250 ? 400 : 100;
            delayMs = next;
            delay.setText(next + "ms");
            listener.onDelayChanged(next);
        });
        header.addView(delay, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = Ui.text(context, "×", 22, Ui.TEXT_DIM, false);
        close.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams closeLp = Ui.lp(dp(36), dp(36));
        closeLp.setMarginStart(dp(4));
        close.setOnClickListener(v -> hidePanel());
        header.addView(close, closeLp);
        root.addView(header);

        View divider = new View(context);
        divider.setBackgroundColor(Ui.STROKE);
        LinearLayout.LayoutParams dividerLp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerLp.topMargin = dp(8);
        root.addView(divider, dividerLp);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout list = Ui.vertical(context);
        list.setPadding(0, dp(8), 0, dp(6));
        scroll.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (groups.isEmpty()) {
            TextView empty = Ui.text(context, "没有匹配角色。可以关掉面板重新识别，或回应用内修正词条。",
                    12.5f, Ui.TEXT_DIM, false);
            empty.setPadding(dp(2), dp(16), dp(2), dp(16));
            list.addView(empty);
        } else {
            int index = 1;
            for (MatchGroup group : groups) {
                list.addView(buildRow(group, index++));
                Ui.addSpace(list, 8);
            }
        }

        TextView hint = Ui.text(context, spec.hint, 10.5f, spec.hintColor, false);
        hint.setPadding(dp(2), dp(2), dp(2), dp(2));
        root.addView(hint);
        return root;
    }

    private View buildRow(MatchGroup group, int index) {
        LinearLayout card = Ui.vertical(context);
        card.setPadding(dp(10), dp(9), dp(10), dp(10));
        int border = group.isUnique() ? Ui.withAlpha(Ui.RED, 0x66)
                : group.isRecommended() ? Ui.withAlpha(Ui.GREEN, 0x55) : Ui.STROKE;
        card.setBackground(Ui.rounded(0xFF121A2C, border, 13, 1, context));

        LinearLayout row = Ui.horizontal(context);
        FlowLayout tags = new FlowLayout(context);
        tags.setGap(dp(5), dp(5));
        for (String tag : group.combo) {
            tags.addView(Ui.pill(context, tag, 0xFFDCE5FF, 0xFF1B2740,
                    Ui.withAlpha(Ui.PRIMARY, 0x77), 7));
        }
        row.addView(tags, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        int badgeColor = group.isUnique() ? Ui.RED
                : group.isRecommended() ? Ui.GREEN : Ui.TEXT_DIM;
        TextView badge = Ui.text(context, group.badgeText(), 10.5f, badgeColor, true);
        badge.setPadding(dp(7), dp(3), dp(7), dp(3));
        badge.setBackground(Ui.rounded(Ui.withAlpha(badgeColor, 0x1F),
                Ui.withAlpha(badgeColor, 0x55), 7, 1, context));
        row.addView(badge);

        TextView number = Ui.text(context, "#" + index, 10, Ui.TEXT_DIM, false);
        LinearLayout.LayoutParams numberLp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        numberLp.setMarginStart(dp(7));
        row.addView(number, numberLp);
        card.addView(row);

        FlowLayout characters = new FlowLayout(context);
        characters.setGap(dp(6), dp(6));
        characters.setPadding(0, dp(7), 0, 0);
        for (Character character : group.characters) {
            TextView chip = Ui.text(context, character.star + "★ " + character.name,
                    12, Ui.starColor(character.star), character.star >= 2);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            int color = Ui.starColor(character.star);
            chip.setBackground(Ui.ripple(context, Ui.withAlpha(color, 0x18),
                    Ui.withAlpha(color, 0x55), 9));
            chip.setOnClickListener(v -> listener.onCharacterSelected(group));
            characters.addView(chip);
        }
        card.addView(characters);
        return card;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
