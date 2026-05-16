package com.samarthshukla.protyper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 1. Find the Views
        ImageButton btnBack = findViewById(R.id.btnBack);
        SeekBar volumeSeekBar = findViewById(R.id.volumeSeekBar);
        MaterialSwitch themeSwitch = findViewById(R.id.themeSwitch);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        // 2. Back Button Logic
        btnBack.setOnClickListener(v -> finish());

        // 3. Volume Logic
        int savedVolume = prefs.getInt("volume", 100); // Default to 100 if not set
        volumeSeekBar.setProgress(savedVolume);

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Save the volume instantly as they drag it
                prefs.edit().putInt("volume", progress).apply();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 4. Theme Toggle Logic
        // Check what the current theme is so the switch displays correctly when the page opens
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        themeSwitch.setChecked(currentNightMode == Configuration.UI_MODE_NIGHT_YES);

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                prefs.edit().putBoolean("isDarkMode", true).apply();
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                prefs.edit().putBoolean("isDarkMode", false).apply();
            }
        });
    }
}