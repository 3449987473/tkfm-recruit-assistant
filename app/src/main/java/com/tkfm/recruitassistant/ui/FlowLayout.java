package com.tkfm.recruitassistant.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.tkfm.recruitassistant.util.Ui;

/** A minimal wrapping layout (no Flexbox dependency). */
public class FlowLayout extends ViewGroup {

    private int gap;
    private int lineGap;

    public FlowLayout(Context context) {
        super(context);
        init(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        gap = Ui.dp(context, 7);
        lineGap = Ui.dp(context, 7);
    }

    public void setGap(int gapPx, int lineGapPx) {
        this.gap = gapPx;
        this.lineGap = lineGapPx;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int maxWidth = widthMode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : widthSize;
        int available = maxWidth - getPaddingLeft() - getPaddingRight();

        int x = 0;
        int y = getPaddingTop();
        int lineHeight = 0;
        int maxUsed = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                    heightMeasureSpec);
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            if (x > 0 && x + gap + cw > available) {
                x = 0;
                y += lineHeight + lineGap;
                lineHeight = 0;
            }
            x += (x == 0 ? 0 : gap) + cw;
            lineHeight = Math.max(lineHeight, ch);
            maxUsed = Math.max(maxUsed, x);
        }
        int height = y + lineHeight + getPaddingBottom();
        setMeasuredDimension(resolveSize(Math.max(maxUsed + getPaddingLeft() + getPaddingRight(), widthMode == MeasureSpec.EXACTLY ? widthSize : 0), widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int available = getWidth() - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = getPaddingTop();
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            if (x > 0 && x + gap + cw > available) {
                x = 0;
                y += lineHeight + lineGap;
                lineHeight = 0;
            }
            int left = getPaddingLeft() + x + (x == 0 ? 0 : gap);
            child.layout(left, y, left + cw, y + ch);
            x = left - getPaddingLeft() + cw;
            lineHeight = Math.max(lineHeight, ch);
        }
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
}
