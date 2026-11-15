
package com.samarthshukla.protyper;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DifficultyActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "AppPrefs";
    public static final String PREF_TIMER_SECONDS = "practice_timer_seconds";
    public static final String PREF_BACKSPACE_ENABLED = "backspace_enabled";

    public static final int DEFAULT_TIMER_SECONDS = 60;

    public static final String EXTRA_MODE = "practice_mode";
    public static final String MODE_SINGLE_WORD = "single_word";
    public static final String MODE_PARAGRAPH = "paragraph";

    private MaterialButtonToggleGroup difficultyGroup;
    private MaterialButtonToggleGroup durationGroup;
    private MaterialButtonToggleGroup durationGroupLong;
    private MaterialSwitch switchBackspace;

    private SharedPreferences preferences;

    private String practiceMode = MODE_SINGLE_WORD;
    private int selectedDuration = DEFAULT_TIMER_SECONDS;
    private boolean backspaceEnabled = true;

    private AdView bannerAd;
    private final Handler bannerRefreshHandler = new Handler();

    private final Runnable bannerRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (bannerAd != null) {
                bannerAd.loadAd(new AdRequest.Builder().build());
                bannerRefreshHandler.postDelayed(this, 30000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_difficulty);

        preferences = getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE
        );

        practiceMode = getIntent().getStringExtra(EXTRA_MODE);
        if (practiceMode == null) {
            practiceMode = MODE_SINGLE_WORD;
        }

        initializeViews();
        loadSavedPreferences();
        configureMode();
        setupDurationSelection();
        setupBackspaceSwitch();
        setupStartButton();

        MobileAds.initialize(this, initializationStatus -> {});
        loadBannerAd();
        bannerRefreshHandler.postDelayed(bannerRefreshRunnable, 30000);
    }

    private void initializeViews() {
        difficultyGroup = findViewById(R.id.difficultyGroup);
        durationGroup = findViewById(R.id.durationGroup);
        durationGroupLong = findViewById(R.id.durationGroupLong);
        switchBackspace = findViewById(R.id.switchBackspace);
    }

    private void loadSavedPreferences() {
        selectedDuration = preferences.getInt(
                PREF_TIMER_SECONDS, DEFAULT_TIMER_SECONDS
        );

        backspaceEnabled = preferences.getBoolean(
                PREF_BACKSPACE_ENABLED, true
        );

        selectDuration(selectedDuration);
        switchBackspace.setChecked(backspaceEnabled);
    }

    private void configureMode() {
        TextView title = findViewById(R.id.tvSetupTitle);
        LinearLayout difficultySection = findViewById(R.id.difficultySection);

        if (MODE_PARAGRAPH.equals(practiceMode)) {
            title.setText("Paragraph");
            difficultySection.setVisibility(View.GONE);
        } else {
            title.setText("Single Word");
            difficultySection.setVisibility(View.VISIBLE);
        }
    }

    private void setupDurationSelection() {

        durationGroup.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
                    if (!isChecked) return;

                    durationGroupLong.clearChecked();
                    selectedDuration = getDurationFromId(checkedId);
                }
        );

        durationGroupLong.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
                    if (!isChecked) return;

                    durationGroup.clearChecked();
                    selectedDuration = getDurationFromId(checkedId);
                }
        );
    }

    private int getDurationFromId(int id) {
        if (id == R.id.btnDuration15) return 15;
        if (id == R.id.btnDuration30) return 30;
        if (id == R.id.btnDuration60) return 60;
        if (id == R.id.btnDuration120) return 120;
        if (id == R.id.btnDuration300) return 300;

        // Zero represents no time limit.
        if (id == R.id.btnDurationUnlimited) return 0;

        return DEFAULT_TIMER_SECONDS;
    }

    private void selectDuration(int seconds) {

        durationGroup.clearChecked();
        durationGroupLong.clearChecked();

        switch (seconds) {
            case 15:
                durationGroup.check(R.id.btnDuration15);
                break;

            case 30:
                durationGroup.check(R.id.btnDuration30);
                break;

            case 120:
                durationGroupLong.check(R.id.btnDuration120);
                break;

            case 300:
                durationGroupLong.check(R.id.btnDuration300);
                break;

            case 0:
                durationGroupLong.check(R.id.btnDurationUnlimited);
                break;

            case 60:
            default:
                durationGroup.check(R.id.btnDuration60);
                break;
        }
    }

    private void setupBackspaceSwitch() {
        switchBackspace.setOnCheckedChangeListener(
                (buttonView, isChecked) -> backspaceEnabled = isChecked
        );
    }

    private void setupStartButton() {
        MaterialButton btnStart = findViewById(R.id.btnStartPractice);

        btnStart.setOnClickListener(v -> {

            // Save preferences for the next session.
            preferences.edit()
                    .putInt(PREF_TIMER_SECONDS, selectedDuration)
                    .putBoolean(PREF_BACKSPACE_ENABLED, backspaceEnabled)
                    .apply();

            Intent intent;

            if (MODE_PARAGRAPH.equals(practiceMode)) {
                intent = new Intent(
                        DifficultyActivity.this,
                        ParagraphActivity.class
                );
            } else {
                int checkedDifficulty = difficultyGroup.getCheckedButtonId();

                if (checkedDifficulty == R.id.btnEasy) {
                    intent = new Intent(this, EasyModeActivity.class);
                } else if (checkedDifficulty == R.id.btnHard) {
                    intent = new Intent(this, HardModeActivity.class);
                } else {
                    intent = new Intent(this, MediumModeActivity.class);
                }
            }

            intent.putExtra(PREF_TIMER_SECONDS, selectedDuration);
            intent.putExtra(PREF_BACKSPACE_ENABLED, backspaceEnabled);

            if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.JELLY_BEAN) {

                ActivityOptions options =
                        ActivityOptions.makeCustomAnimation(
                                this,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        );

                startActivity(intent, options.toBundle());

            } else {
                startActivity(intent);
            }
        });
    }

    private void loadBannerAd() {
        bannerAd = findViewById(R.id.bannerAd);

        if (bannerAd != null) {
            bannerAd.loadAd(new AdRequest.Builder().build());
        }
    }

    @Override
    protected void onDestroy() {
        bannerRefreshHandler.removeCallbacks(bannerRefreshRunnable);

        if (bannerAd != null) {
            bannerAd.destroy();
            bannerAd = null;
        }

        super.onDestroy();
    }
}
