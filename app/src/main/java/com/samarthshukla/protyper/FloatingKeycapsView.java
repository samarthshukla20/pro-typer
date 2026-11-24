
package com.samarthshukla.protyper;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.appcompat.content.res.AppCompatResources;

public class FloatingKeycapsView extends View {

    // Blue, pink and grey keycap variants.
    private final int[] keycapColors = {
            R.drawable.keycap_pt_blue,
            R.drawable.keycap_pt_pink,
            R.drawable.keycap_pt_grey
    };

    // Normalized positions, so they adapt to screen size.
    private final float[][] positions = {
            {0.08f, 0.12f}, {0.29f, 0.08f}, {0.73f, 0.12f},
            {0.91f, 0.23f}, {0.16f, 0.36f}, {0.48f, 0.28f},
            {0.81f, 0.43f}, {0.06f, 0.65f}, {0.32f, 0.57f},
            {0.67f, 0.66f}, {0.91f, 0.78f}, {0.22f, 0.87f},
            {0.49f, 0.91f}, {0.76f, 0.91f}, {0.52f, 0.12f},
            {0.94f, 0.52f}, {0.11f, 0.88f}, {0.39f, 0.42f},
            {0.68f, 0.28f}, {0.86f, 0.09f},
            {0.18f, 0.22f}, {0.57f, 0.18f},
            {0.95f, 0.36f}, {0.42f, 0.48f},
            {0.13f, 0.52f}, {0.75f, 0.55f},
            {0.35f, 0.72f}, {0.58f, 0.82f},
            {0.88f, 0.92f}, {0.03f, 0.32f},
            {0.63f, 0.39f}, {0.25f, 0.96f}
    };

    private final float[] sizes = {
            0.85f, 0.95f, 1.0f, 0.8f, 1.1f,
            0.9f, 0.85f, 0.95f, 1.0f, 0.9f,
            0.8f, 1.05f, 0.9f, 0.85f, 1.2f,
            0.95f, 0.9f, 0.85f, 1.25f, 0.8f,
            0.75f, 0.85f, 0.7f, 0.8f,
            0.9f, 0.75f, 0.85f, 0.7f,
            0.8f, 0.9f, 0.75f, 0.85f
    };

    private final float[] rotations = {
            -12, 8, -7, 14, 10, -9, 12,
            -15, 6, -8, 13, -10, 7, -12,
            9, -14, 11, -6, 8, -11,
            -8, 12, -14, 6,
            -10, 15, -5, 9,
            -12, 7, -9, 13
    };

    private Drawable[] keycapDrawables;

    private ValueAnimator animator;
    private float animationProgress = 0f;
    private float density;

    public FloatingKeycapsView(Context context) {
        super(context);
        init();
    }

    public FloatingKeycapsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloatingKeycapsView(Context context, AttributeSet attrs,
                               int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        // Load all three drawable variants.
        keycapDrawables = new Drawable[keycapColors.length];

        for (int i = 0; i < keycapColors.length; i++) {
            Drawable drawable = AppCompatResources.getDrawable(
                    getContext(),
                    keycapColors[i]
            );

            if (drawable != null) {
                keycapDrawables[i] = drawable.mutate();
            }
        }

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        setClickable(false);
        setFocusable(false);
    }

    private float dp(float value) {
        return value * density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        if (width == 0 || height == 0 || keycapDrawables == null) {
            return;
        }

        for (int i = 0; i < positions.length; i++) {
            drawKeycap(canvas, i, width, height);
        }
    }

    private void drawKeycap(Canvas canvas, int index,
                            float width, float height) {

        float phase = animationProgress * 2f * (float) Math.PI;

        float offsetY = (float) Math.sin(
                phase + index * 0.85f
        ) * dp(9);

        float offsetX = (float) Math.cos(
                phase * 0.7f + index
        ) * dp(4);

        float centerX = positions[index][0] * width + offsetX;
        float centerY = positions[index][1] * height + offsetY;

        float keySize = dp(45) * sizes[index];

        // Cycle through blue, pink and grey keycaps.
        Drawable keycapDrawable =
                keycapDrawables[index % keycapDrawables.length];

        if (keycapDrawable == null) {
            return;
        }

        int save = canvas.save();

        canvas.rotate(rotations[index], centerX, centerY);

        int left = Math.round(centerX - keySize / 2f);
        int top = Math.round(centerY - keySize / 2f);
        int right = Math.round(centerX + keySize / 2f);
        int bottom = Math.round(centerY + keySize / 2f);

        keycapDrawable.setBounds(left, top, right, bottom);

        // 235 = approximately 92% opacity.
        keycapDrawable.setAlpha(50);

        keycapDrawable.draw(canvas);

        canvas.restoreToCount(save);
    }

    private void startFloatingAnimation() {
        if (animator != null && animator.isRunning()) {
            return;
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(12000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });

        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startFloatingAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        super.onDetachedFromWindow();
    }
}
