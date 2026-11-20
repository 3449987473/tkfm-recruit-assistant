package com.tkfm.recruitassistant;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent assistant switches shared by the overlay, the service and the settings UI. */
public final class AssistantSettings {

    private static final String PREFS = "assistant";
    private static final String KEY_AUTO_RUN_ON_SCAN = "auto_run_on_scan";
    private static final String KEY_AUTO_RECRUIT = "auto_click_recruit";
    private static final String KEY_DELAY = "click_delay_ms";
    private static final String KEY_BALL_SIZE = "ball_size_dp";
    private static final String KEY_BALL_ALPHA = "ball_alpha_pct";

    private AssistantSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * When enabled a scan immediately selects tags/time for the best group; otherwise the
     * result sheet waits for the user to tap a group.  Leader groups never auto-run.
     */
    public static boolean autoRunOnScan(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_RUN_ON_SCAN, false);
    }

    /** Off by default: tapping a result must never spend recruit resources without consent. */
    public static boolean autoRecruit(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_RECRUIT, false);
    }

    public static int delay(Context context) {
        return prefs(context).getInt(KEY_DELAY, 150);
    }

    /** Floating button diameter in dp: 44 / 52 / 60. */
    public static int ballSize(Context context) {
        return prefs(context).getInt(KEY_BALL_SIZE, 52);
    }

    /** Floating button opacity percentage: 100 / 85 / 70. */
    public static int ballAlpha(Context context) {
        return prefs(context).getInt(KEY_BALL_ALPHA, 100);
    }

    public static void setAutoRunOnScan(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_RUN_ON_SCAN, value).apply();
    }

    public static void setAutoRecruit(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_RECRUIT, value).apply();
    }

    public static void setDelay(Context context, int value) {
        prefs(context).edit().putInt(KEY_DELAY, value).apply();
    }

    public static void setBallSize(Context context, int value) {
        prefs(context).edit().putInt(KEY_BALL_SIZE, value).apply();
    }

    public static void setBallAlpha(Context context, int value) {
        prefs(context).edit().putInt(KEY_BALL_ALPHA, value).apply();
    }
}
