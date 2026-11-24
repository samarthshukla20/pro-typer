
package com.samarthshukla.protyper;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

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

    private LinearLayout difficultySection;

    private TextView tvSetupTitle;
    private TextView tvSetupSubtitle;
    private TextView tvStrictModeDescription;

    private SharedPreferences preferences;

    private String practiceMode = MODE_SINGLE_WORD;

    private int selectedDuration = DEFAULT_TIMER_SECONDS;

    // true = Backspace enabled
    // false = Backspace disabled (Strict Mode ON)
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
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        // Receive the selected mode from HomeFragment.
        practiceMode = getIntent().getStringExtra(EXTRA_MODE);

        if (practiceMode == null) {
            practiceMode = MODE_SINGLE_WORD;
        }

        initializeViews();

        applySelectionColors();

        configureMode();

        loadSavedPreferences();

        setupDurationSelection();

        setupBackspaceSwitch();

        setupStartButton();

        MobileAds.initialize(this, initializationStatus -> {});

        loadBannerAd();

        bannerRefreshHandler.postDelayed(
                bannerRefreshRunnable,
                30000
        );
    }

    // -----------------------------------------
    // INITIALIZE VIEWS
    // -----------------------------------------

    private void initializeViews() {

        difficultyGroup = findViewById(R.id.difficultyGroup);

        durationGroup = findViewById(R.id.durationGroup);

        durationGroupLong = findViewById(R.id.durationGroupLong);

        switchBackspace = findViewById(R.id.switchBackspace);

        difficultySection = findViewById(R.id.difficultySection);

        tvSetupTitle = findViewById(R.id.tvSetupTitle);

        tvSetupSubtitle = findViewById(R.id.tvSetupSubtitle);

        tvStrictModeDescription =
                findViewById(R.id.tvStrictModeDescription);
    }

    // -----------------------------------------
    // SELECTION COLORS
    // -----------------------------------------

    private void applySelectionColors() {

        int selectedBackground = ContextCompat.getColor(
                this, R.color.setup_button_selected_bg
        );

        int selectedStroke = ContextCompat.getColor(
                this, R.color.setup_button_selected_stroke
        );

        int selectedText = ContextCompat.getColor(
                this, R.color.setup_button_selected_text
        );

        int unselectedBackground = ContextCompat.getColor(
                this, R.color.bg_main
        );

        int unselectedText = ContextCompat.getColor(
                this, R.color.text_primary
        );

        int unselectedStroke = ContextCompat.getColor(
                this, R.color.setup_button_stroke
        );

        MaterialButton[] selectionButtons = {
                findViewById(R.id.btnEasy),
                findViewById(R.id.btnMedium),
                findViewById(R.id.btnHard),

                findViewById(R.id.btnDuration15),
                findViewById(R.id.btnDuration30),
                findViewById(R.id.btnDuration60),

                findViewById(R.id.btnDuration120),
                findViewById(R.id.btnDuration300),
                findViewById(R.id.btnDurationUnlimited)
        };

        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] {}
        };

        for (MaterialButton button : selectionButtons) {

            if (button == null) continue;

            // Background
            button.setBackgroundTintList(
                    new ColorStateList(
                            states,
                            new int[] {
                                    selectedBackground,
                                    unselectedBackground
                            }
                    )
            );

            // Text
            button.setTextColor(
                    new ColorStateList(
                            states,
                            new int[] {
                                    selectedText,
                                    unselectedText
                            }
                    )
            );

            // Border
            button.setStrokeColor(
                    new ColorStateList(
                            states,
                            new int[] {
                                    selectedStroke,
                                    unselectedStroke
                            }
                    )
            );
        }
    }

    // -----------------------------------------
    // MODE CONFIGURATION
    // -----------------------------------------

    private void configureMode() {

        boolean isParagraph =
                MODE_PARAGRAPH.equals(practiceMode);

        tvSetupTitle.setText("Practice Setup");

        if (isParagraph) {

            tvSetupSubtitle.setText(
                    "Paragraph Mode · Configure your typing session."
            );

            // Paragraph mode does not use difficulty selection.
            difficultySection.setVisibility(View.GONE);

        } else {

            tvSetupSubtitle.setText(
                    "Single Word Mode · Configure your typing session."
            );

            difficultySection.setVisibility(View.VISIBLE);
        }
    }

    // -----------------------------------------
    // LOAD SAVED PREFERENCES
    // -----------------------------------------

    private void loadSavedPreferences() {

        selectedDuration = preferences.getInt(
                PREF_TIMER_SECONDS,
                DEFAULT_TIMER_SECONDS
        );

        backspaceEnabled = preferences.getBoolean(
                PREF_BACKSPACE_ENABLED,
                true
        );

        selectDuration(selectedDuration);

        // Strict Mode is the inverse of Backspace enabled.
        switchBackspace.setChecked(!backspaceEnabled);

        updateStrictModeDescription(!backspaceEnabled);
    }

    // -----------------------------------------
    // DURATION SELECTION
    // -----------------------------------------

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

        // Zero represents unlimited duration.
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

    // -----------------------------------------
    // STRICT MODE
    // -----------------------------------------

    private void setupBackspaceSwitch() {
        switchBackspace.setChecked(!backspaceEnabled);
        updateStrictModeDescription(switchBackspace.isChecked());

        switchBackspace.setOnCheckedChangeListener((buttonView, isChecked) -> {
            backspaceEnabled = !isChecked;
            updateStrictModeDescription(isChecked);
        });
    }

    private void updateStrictModeDescription(boolean enabled) {
        tvStrictModeDescription.setText(
                enabled
                        ? "ON · Backspace is disabled."
                        : "OFF · You can correct mistakes."
        );
    }

    // -----------------------------------------
    // START PRACTICE
    // -----------------------------------------

    private void setupStartButton() {

        MaterialButton btnStart =
                findViewById(R.id.btnStartPractice);

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

                int checkedDifficulty =
                        difficultyGroup.getCheckedButtonId();

                if (checkedDifficulty == R.id.btnEasy) {

                    intent = new Intent(
                            this,
                            EasyModeActivity.class
                    );

                } else if (checkedDifficulty == R.id.btnHard) {

                    intent = new Intent(
                            this,
                            HardModeActivity.class
                    );

                } else {

                    intent = new Intent(
                            this,
                            MediumModeActivity.class
                    );
                }
            }

            intent.putExtra(
                    PREF_TIMER_SECONDS,
                    selectedDuration
            );

            intent.putExtra(
                    PREF_BACKSPACE_ENABLED,
                    backspaceEnabled
            );

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

    // -----------------------------------------
    // ADMOB
    // -----------------------------------------

    private void loadBannerAd() {

        bannerAd = findViewById(R.id.bannerAd);

        if (bannerAd != null) {
            bannerAd.loadAd(new AdRequest.Builder().build());
        }
    }

    @Override
    protected void onDestroy() {

        bannerRefreshHandler.removeCallbacks(
                bannerRefreshRunnable
        );

        if (bannerAd != null) {
            bannerAd.destroy();
            bannerAd = null;
        }

        super.onDestroy();
    }
}
