package com.tkfm.recruitassistant.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.tkfm.recruitassistant.R;

public final class Ui {

    public static final int BG = 0xFF0B0E18;
    public static final int SURFACE = 0xFF141A2B;
    public static final int SURFACE_2 = 0xFF1B2236;
    public static final int STROKE = 0xFF2A3350;
    public static final int PRIMARY = 0xFF7C9CFF;
    public static final int PRIMARY_DARK = 0xFF2A3966;
    public static final int ACCENT = 0xFF4ADEA0;
    public static final int GOLD = 0xFFF0C15A;
    public static final int PINK = 0xFFE88BB0;
    public static final int TEXT = 0xFFEDF1FF;
    public static final int TEXT_DIM = 0xFF9AA5C4;
    public static final int RED = 0xFFFF6B6B;
    public static final int GREEN = 0xFF4ADE80;

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static int sp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static int color(Context context, int res) {
        return ContextCompat.getColor(context, res);
    }

    public static GradientDrawable rounded(int fill, int stroke, float radiusDp, float strokeDp,
                                           Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(context, strokeDp), stroke);
        return d;
    }

    public static RippleDrawable ripple(Context context, int fill, int stroke, float radiusDp) {
        GradientDrawable base = rounded(fill, stroke, radiusDp, stroke == 0 ? 0 : 1, context);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, null);
    }

    public static TextView text(Context context, CharSequence value, float sizeSp, int color,
                                boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static void margin(View v, int l, int t, int r, int b) {
        if (v.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) v.getLayoutParams()).setMargins(l, t, r, b);
        }
    }

    public static TextView pill(Context context, CharSequence text, int textColor, int fill,
                                int stroke, float radius) {
        TextView tv = text(context, text, 12, textColor, false);
        tv.setPadding(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4));
        tv.setBackground(rounded(fill, stroke, radius, stroke == 0 ? 0 : 1, context));
        return tv;
    }

    public static int starColor(int star) {
        switch (star) {
            case 3: return GOLD;
            case 2: return PINK;
            default: return 0xFFB7C0D8;
        }
    }

    public static String formatTime(long millis) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm",
                java.util.Locale.CHINA);
        return fmt.format(new java.util.Date(millis));
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static void addSpace(LinearLayout parent, int dp) {
        View v = new View(parent.getContext());
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(parent.getContext(), dp)));
    }

    public static int dpFromRes(Context context, int dimenRes) {
        return context.getResources().getDimensionPixelSize(dimenRes);
    }
}
