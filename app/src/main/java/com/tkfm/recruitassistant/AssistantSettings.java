package com.tkfm.recruitassistant;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent assistant switches shared by the overlay, the service and the settings UI. */
public final class AssistantSettings {

    private static final String PREFS = "assistant";
    private static final String KEY_AUTO_TAGS = "auto_select_tags";
    private static final String KEY_AUTO_TIME = "auto_adjust_time";
    private static final String KEY_AUTO_RECRUIT = "auto_click_recruit";
    private static final String KEY_DELAY = "click_delay_ms";

    private AssistantSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean autoTags(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_TAGS, true);
    }

    public static boolean autoTime(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_TIME, true);
    }

    /** Off by default: tapping a result must never spend recruit resources without consent. */
    public static boolean autoRecruit(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_RECRUIT, false);
    }

    public static int delay(Context context) {
        return prefs(context).getInt(KEY_DELAY, 150);
    }

    public static void setAutoTags(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_TAGS, value).apply();
    }

    public static void setAutoTime(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_TIME, value).apply();
    }

    public static void setAutoRecruit(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_RECRUIT, value).apply();
    }

    public static void setDelay(Context context, int value) {
        prefs(context).edit().putInt(KEY_DELAY, value).apply();
    }
}
