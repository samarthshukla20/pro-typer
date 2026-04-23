package com.samarthshukla.protyper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends Activity {

    private View progressFill;
    private TextView progressText;
    private Handler handler = new Handler();
    private int duration = 2000; // 4 seconds
    private long startTime;
    private Runnable progressUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        XpManager.getGlobalUserId(this);

        progressFill = findViewById(R.id.progress_fill);
        progressText = findViewById(R.id.progress_text);

        FrameLayout container = findViewById(R.id.progress_container);
        startTime = System.currentTimeMillis();

        progressUpdater = new Runnable() {
            @Override
            public void run() {
                float elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min(elapsed / duration, 0.98f);

                int containerWidth = container.getWidth();
                if (containerWidth > 0) {
                    progressFill.getLayoutParams().width = (int) (containerWidth * progress);
                    progressFill.requestLayout();
                }

                int percent = (int) (progress * 100);
                progressText.setText(percent + "%");

                // Interpolate text color from white to black
                int startColor = 0xFFFFFFFF;
                int endColor = 0xFF000000;

                int r = (int) ((1 - progress) * ((startColor >> 16) & 0xFF) + progress * ((endColor >> 16) & 0xFF));
                int g = (int) ((1 - progress) * ((startColor >> 8) & 0xFF) + progress * ((endColor >> 8) & 0xFF));
                int b = (int) ((1 - progress) * (startColor & 0xFF) + progress * (endColor & 0xFF));
                int interpolatedColor = 0xFF000000 | (r << 16) | (g << 8) | b;
                progressText.setTextColor(interpolatedColor);

                if (progress < 0.98f) {
                    handler.postDelayed(this, 16);
                }
            }
        };
        handler.post(progressUpdater);

        handler.postDelayed(() -> {
            View root = findViewById(R.id.splash_root);
            AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
            fadeOut.setDuration(500);
            fadeOut.setFillAfter(true);
            root.startAnimation(fadeOut);

            fadeOut.setAnimationListener(new AlphaAnimation.AnimationListener() {
                @Override
                public void onAnimationStart(android.view.animation.Animation animation) {
                }

                @Override
                public void onAnimationEnd(android.view.animation.Animation animation) {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }

                @Override
                public void onAnimationRepeat(android.view.animation.Animation animation) {
                }
            });

        }, duration);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);
        super.onDestroy();
    }
}