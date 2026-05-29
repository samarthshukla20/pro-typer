package com.samarthshukla.protyper;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;  // <-- Added import
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.material.color.DynamicColors;
import java.util.Date;

public class AppName extends Application implements Application.ActivityLifecycleCallbacks {
    private AppOpenAd appOpenAd;
    private boolean isAdShowing = false;
    private long loadTime = 0;
    private Activity currentActivity;
    private static final String AD_UNIT_ID = "ca-app-pub-6807409350203174/5867917691";  // Replace with real Ad Unit ID
    private static final long AD_TIMEOUT = 2 * 60 * 60 * 1000; // Refresh ads every 2 hours

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply dynamic colors
        DynamicColors.applyToActivitiesIfAvailable(this);

        // --- READ SAVED THEME PREFERENCE ---
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        // 1. Is the app set to follow the system? (True by default for new installs)
        boolean isSystemDefault = prefs.getBoolean("isSystemDefault", true);

        if (isSystemDefault) {
            // Tell the app to dynamically follow the phone's system theme.
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            // Note: We intentionally omit UiModeManager here so the Splash Screen naturally follows the system.
        } else {
            // 2. The user has turned off System Default. Respect their explicit Dark Mode choice!
            boolean isDarkMode = prefs.getBoolean("isDarkMode", false);
            if (isDarkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
        // ----------------------------------------

        // Initialize the Mobile Ads SDK
        MobileAds.initialize(this);

        // Register for activity lifecycle callbacks
        registerActivityLifecycleCallbacks(this);

        // Load the first ad
        loadAd();
    }

    private void loadAd() {
        if (appOpenAd == null) {
            AdRequest adRequest = new AdRequest.Builder().build();
            AppOpenAd.load(this, AD_UNIT_ID, adRequest, new AppOpenAd.AppOpenAdLoadCallback() {
                @Override
                public void onAdLoaded(AppOpenAd ad) {
                    // Handle the loaded ad
                }

                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    // Handle the error
                }
            });

        }
    }

    private void showAdIfAvailable() {
        if (appOpenAd != null && !isAdShowing && isAdFresh()) {
            isAdShowing = true;
            appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    isAdShowing = false;
                    appOpenAd = null;
                    loadAd(); // Load the next ad
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isAdShowing = false;
                    loadAd();
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    appOpenAd = null;
                }
            });
            appOpenAd.show(currentActivity);
        } else {
            // If no ad is available, attempt to load one
            loadAd();
        }
    }

    private boolean isAdFresh() {
        // The ad is considered fresh for 2 hours
        return (new Date().getTime() - loadTime) < AD_TIMEOUT;
    }

    // Show ad when app is opened or resumed
    @Override
    public void onActivityResumed(Activity activity) {
        currentActivity = activity;
        showAdIfAvailable();
    }

    // Show ad when user switches between activities
    @Override
    public void onActivityStarted(Activity activity) {
        currentActivity = activity;
        showAdIfAvailable();
    }

    @Override
    public void onActivityPaused(Activity activity) { }

    @Override
    public void onActivityStopped(Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityDestroyed(Activity activity) { }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        // Force portrait orientation for every activity
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}