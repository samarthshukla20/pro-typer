package com.samarthshukla.protyper;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.button.MaterialButton;
import com.samarthshukla.protyper.R;

public class DifficultyActivity extends AppCompatActivity {

    private AdView bannerAd;
    private Handler bannerRefreshHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_difficulty);

        // Initialize AdMob
        MobileAds.initialize(this, initializationStatus -> {});

        // Load Banner Ad
        loadBannerAd();

        // Start auto-refresh for Banner Ad
        startBannerAdRefresh();

        // Initialize buttons
        MaterialButton btnEasy = findViewById(R.id.btnEasy);
        MaterialButton btnMedium = findViewById(R.id.btnMedium);
        MaterialButton btnHard = findViewById(R.id.btnHard);

        // Button click listeners (directly start game without interstitial ads)
        btnEasy.setOnClickListener(v -> openMode(EasyModeActivity.class));
        btnMedium.setOnClickListener(v -> openMode(com.samarthshukla.protyper.MediumModeActivity.class));
        btnHard.setOnClickListener(v -> openMode(com.samarthshukla.protyper.HardModeActivity.class));
    }

    // Load Banner Ad
    private void loadBannerAd() {
        bannerAd = findViewById(R.id.bannerAd);
        if (bannerAd != null) {
            AdRequest adRequest = new AdRequest.Builder().build();
            bannerAd.loadAd(adRequest);
        }
    }

    // Auto-refresh Banner Ad every 30 seconds
    private void startBannerAdRefresh() {
        Runnable bannerRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (bannerAd != null) {
                    bannerAd.loadAd(new AdRequest.Builder().build());
                }
                bannerRefreshHandler.postDelayed(this, 30000);
            }
        };
        bannerRefreshHandler.post(bannerRefreshRunnable);
    }

    // Start Activity with Animation
    private void openMode(Class<?> activityClass) {
        Intent intent = new Intent(DifficultyActivity.this, activityClass);
        int durationSeconds = getIntent().getIntExtra(PracticeSettingsActivity.PREF_TIMER_SECONDS,
                PracticeSettingsActivity.DEFAULT_TIMER_SECONDS);
        intent.putExtra(PracticeSettingsActivity.PREF_TIMER_SECONDS, durationSeconds);
        int durationSeconds = getIntent().getIntExtra(PracticeSettingsActivity.PREF_TIMER_SECONDS,
                PracticeSettingsActivity.DEFAULT_TIMER_SECONDS);
        intent.putExtra(PracticeSettingsActivity.PREF_TIMER_SECONDS, durationSeconds);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            ActivityOptions options = ActivityOptions.makeCustomAnimation(
                    DifficultyActivity.this,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
            startActivity(intent, options.toBundle());
        } else {
            startActivity(intent);
        }
    }
}