package com.samarthshukla.protyper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class PracticeSettingsActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "practice_mode";
    public static final String MODE_SINGLE_WORD = "single_word";
    public static final String MODE_PARAGRAPH = "paragraph";

    public static final String PREF_TIMER_SECONDS = "practice_timer_seconds";
    public static final int DEFAULT_TIMER_SECONDS = 60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice_settings);

        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvMode = findViewById(R.id.tvPracticeMode);
        RadioGroup timerGroup = findViewById(R.id.timerGroup);
        MaterialButton btnStart = findViewById(R.id.btnStartPractice);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) {
            mode = MODE_SINGLE_WORD;
        }

        tvMode.setText(MODE_PARAGRAPH.equals(mode) ? "PARAGRAPH" : "SINGLE WORD");

        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        int savedSeconds = prefs.getInt(PREF_TIMER_SECONDS, DEFAULT_TIMER_SECONDS);
        selectTimerRadio(timerGroup, savedSeconds);

        timerGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int seconds = getSecondsForRadioId(checkedId);
            if (seconds > 0) {
                prefs.edit().putInt(PREF_TIMER_SECONDS, seconds).apply();
            }
        });

        btnBack.setOnClickListener(v -> finish());

        String finalMode = mode;
        btnStart.setOnClickListener(v -> {
            Intent intent;

            if (MODE_PARAGRAPH.equals(finalMode)) {
                intent = new Intent(this, ParagraphActivity.class);
            } else {
                intent = new Intent(this, DifficultyActivity.class);
            }

            intent.putExtra(PREF_TIMER_SECONDS,
                    prefs.getInt(PREF_TIMER_SECONDS, DEFAULT_TIMER_SECONDS));
            startActivity(intent);
            finish();
        });
    }

    private void selectTimerRadio(RadioGroup group, int seconds) {
        int radioId;
        switch (seconds) {
            case 15:
                radioId = R.id.radio15;
                break;
            case 30:
                radioId = R.id.radio30;
                break;
            case 120:
                radioId = R.id.radio120;
                break;
            case 300:
                radioId = R.id.radio300;
                break;
            case 60:
            default:
                radioId = R.id.radio60;
                break;
        }
        group.check(radioId);
    }

    private int getSecondsForRadioId(int id) {
        if (id == R.id.radio15) return 15;
        if (id == R.id.radio30) return 30;
        if (id == R.id.radio60) return 60;
        if (id == R.id.radio120) return 120;
        if (id == R.id.radio300) return 300;
        return DEFAULT_TIMER_SECONDS;
    }
}
