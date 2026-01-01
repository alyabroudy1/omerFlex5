package com.omarflex6.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * A dynamic background view that simulates the PS4 "Flow" effect
 * using a slowly shifting gradient.
 */
public class FlowBackgroundView extends View {

    private Paint paint;
    private LinearGradient gradient;
    private int width;
    private int height;

    // PS4 Blue Palette
    private final int COLOR_TOP_START = Color.parseColor("#00539B"); // PS Blue
    private final int COLOR_BOTTOM_START = Color.parseColor("#000000"); // Dark
    private final int COLOR_TOP_END = Color.parseColor("#00A6F8"); // Lighter Blue

    // Animated values
    private float shiftFraction = 0f;
    private ValueAnimator animator;

    public FlowBackgroundView(Context context) {
        super(context);
        init();
    }

    public FlowBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FlowBackgroundView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setDither(true);
        startAnimation();
    }

    private void startAnimation() {
        // Animate a float from 0 to 1 repeatedly
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(15000L); // 15 seconds for a slow, calming wave
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            shiftFraction = (float) animation.getAnimatedValue();
            invalidate(); // Redraw
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (width == 0 || height == 0)
            return;

        // Calculate dynamic colors based on shiftFraction
        // We blend between COLOR_TOP_START and COLOR_TOP_END for the top color
        int currentTopColor = blendColors(COLOR_TOP_START, COLOR_TOP_END, shiftFraction);

        // Create a gradient from Top-Left to Bottom-Right
        gradient = new LinearGradient(
                0, 0, width, height,
                new int[] { currentTopColor, COLOR_BOTTOM_START },
                null,
                Shader.TileMode.CLAMP);

        paint.setShader(gradient);
        canvas.drawRect(0, 0, width, height, paint);
    }

    private int blendColors(int color1, int color2, float ratio) {
        final float inverseR = 1f - ratio;
        float r = (Color.red(color1) * inverseR) + (Color.red(color2) * ratio);
        float g = (Color.green(color1) * inverseR) + (Color.green(color2) * ratio);
        float b = (Color.blue(color1) * inverseR) + (Color.blue(color2) * ratio);
        return Color.rgb((int) r, (int) g, (int) b);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}
