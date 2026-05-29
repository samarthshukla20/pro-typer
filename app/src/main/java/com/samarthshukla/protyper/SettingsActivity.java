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
        MaterialSwitch systemDefaultSwitch = findViewById(R.id.systemDefaultSwitch); // NEW
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

        // ==========================================
        // 4. DISPLAY / THEME TOGGLE LOGIC
        // ==========================================

        // A. Read saved preferences (System Default is TRUE by default on new installs)
        boolean isSystemDefault = prefs.getBoolean("isSystemDefault", true);
        boolean isDarkMode = prefs.getBoolean("isDarkMode", false);

        // B. Set initial UI states when the page opens
        systemDefaultSwitch.setChecked(isSystemDefault);
        themeSwitch.setEnabled(!isSystemDefault); // Grays out Dark Mode if System Default is ON

        if (isSystemDefault) {
            // Visually match the switch to whatever the phone is currently doing
            int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            themeSwitch.setChecked(currentNightMode == Configuration.UI_MODE_NIGHT_YES);
        } else {
            themeSwitch.setChecked(isDarkMode);
        }

        // C. Listener for the SYSTEM DEFAULT Switch
        systemDefaultSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isSystemDefault", isChecked).apply();
            themeSwitch.setEnabled(!isChecked); // Lock or Unlock the Dark Mode switch

            if (isChecked) {
                // Apply System Default Instantly
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    android.app.UiModeManager uiManager = (android.app.UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
                    uiManager.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO);
                }

                // Visually update the grayed-out Dark Mode switch to match the system
                int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                themeSwitch.setChecked(currentNightMode == Configuration.UI_MODE_NIGHT_YES);
            } else {
                // Revert to whatever their manual Dark Mode preference was
                boolean savedDarkMode = prefs.getBoolean("isDarkMode", false);
                themeSwitch.setChecked(savedDarkMode);

                if (savedDarkMode) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        android.app.UiModeManager uiManager = (android.app.UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
                        uiManager.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_YES);
                    }
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        android.app.UiModeManager uiManager = (android.app.UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
                        uiManager.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_NO);
                    }
                }
            }
        });

        // D. Listener for the MANUAL DARK MODE Switch
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Failsafe: Don't do anything if System Default is currently controlling the theme
            if (systemDefaultSwitch.isChecked()) return;

            prefs.edit().putBoolean("isDarkMode", isChecked).apply();

            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    android.app.UiModeManager uiManager = (android.app.UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
                    uiManager.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_YES);
                }
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    android.app.UiModeManager uiManager = (android.app.UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
                    uiManager.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_NO);
                }
            }
        });
    }
}