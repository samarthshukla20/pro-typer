package com.samarthshukla.protyper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private boolean doubleBackToExitPressedOnce = false;
    private Handler doubleBackHandler = new Handler();
    private BroadcastReceiver networkReceiver;
    private InterstitialAd interstitialAd;
    private com.google.android.gms.ads.AdView bannerAdView;
    private Handler bannerRefreshHandler = new Handler();
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    // Firebase Analytics instance and session start time variable
    private FirebaseAnalytics mFirebaseAnalytics;
    private long sessionStartTime;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SESSION_COUNT = "session_count";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);  // Use your main layout

        // Initialize Firebase Analytics and log app open event
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null);

        // (Optional) Initialize Navigation Drawer here

        // Initialize AdMob
        MobileAds.initialize(this, initializationStatus -> {
        });

        // Load Ads
        loadInterstitialAd();
        loadBannerAd();
        startBannerAdRefresh();

        // Initialize buttons
        MaterialButton btnSingleWordMode = findViewById(R.id.btnSingleWordMode);
        btnSingleWordMode.setText("SINGLE WORD MODE");
        MaterialButton btnParagraphMode = findViewById(R.id.btnParagraphMode);
        btnParagraphMode.setText("PARAGRAPH MODE");
        MaterialButton btnMultiplayerMode = findViewById(R.id.btnMultiplayerMode);
        btnMultiplayerMode.setText("MULTIPLAYER MODE");
        MaterialButton btnHistory = findViewById(R.id.btnHistory);
        btnHistory.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        MaterialButton btnExit = findViewById(R.id.btnExit);

        // New ImageButtons for How To Play and About
        ImageButton btnHowToPlay = findViewById(R.id.btnHowToPlay);
        btnHowToPlay.setOnClickListener(v -> showHowToPlayPopup());
        ImageButton btnAbout = findViewById(R.id.btnAbout);
        btnAbout.setOnClickListener(v -> showAdThenStart(AboutActivity.class));

        // Check Internet Connection
        checkInternetOnStart();

        // Button Click Listeners
        btnSingleWordMode.setOnClickListener(v -> showAdThenStart(DifficultyActivity.class));
        btnParagraphMode.setOnClickListener(v -> showAdThenStart(ParagraphActivity.class));
        btnMultiplayerMode.setOnClickListener(v -> showAdThenStart(MultiplayerLobbyActivity.class));
        btnExit.setOnClickListener(v -> showConfirmExitDialog());

        // Firebase Messaging: Retrieve the FCM token
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        // Log failure if needed
                        return;
                    }
                    String token = task.getResult();
                    // Use the token as needed (e.g., log or send to your server)
                    // Example: Log.d("FCM Token", token);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start session tracking
        sessionStartTime = System.currentTimeMillis();
        mFirebaseAnalytics.logEvent("session_start", null);
        checkInternetOnStart();

        // NEW: Check for notification permission (for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Allow Notifications")
                        .setMessage("This app would like to send you notifications. Would you like to allow notifications?")
                        .setCancelable(true)
                        .setPositiveButton("Yes", (dialog, which) -> {
                            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                                    NOTIFICATION_PERMISSION_REQUEST_CODE);
                        })
                        .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        }

        // Register network change receiver
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isInternetAvailable()) {
                    showRetryInternetDialog();
                }
            }
        };
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
        }
        // End session tracking and log session duration
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        Bundle bundle = new Bundle();
        bundle.putLong("session_duration_ms", sessionDuration);
        mFirebaseAnalytics.logEvent("session_end", bundle);
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            showAdThenFinish();
            return;
        }
        doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit.", Toast.LENGTH_SHORT).show();
        doubleBackHandler.postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }


    private void checkInternetOnStart() {
        if (!isInternetAvailable()) {
            showRetryInternetDialog();
        }
    }

    private void showRetryInternetDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Whoops!!")
                .setMessage("It seems you're offline. Check your internet connection and try again!")
                .setCancelable(false)
                .setPositiveButton("Retry", (dialog, which) -> {
                    if (isInternetAvailable()) {
                        restartActivity();
                    } else {
                        showRetryInternetDialog();
                    }
                })
                .show();
    }

    private void showConfirmExitDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exit")
                .setMessage("You're about to leave. Do you really want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> showAdThenFinish())
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
        return false;
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, getString(R.string.Interstitial), adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                interstitialAd = null;
                                loadInterstitialAd(); // Preload for next time
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                                interstitialAd = null;
                                loadInterstitialAd(); // Preload again if it fails
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError adError) {
                        interstitialAd = null;
                        // Optional: try again after delay instead of instantly looping
                        new Handler().postDelayed(MainActivity.this::loadInterstitialAd, 30000);
                    }
                });
    }

    private void loadBannerAd() {
        bannerAdView = findViewById(R.id.bannerAdView);
        AdRequest adRequest = new AdRequest.Builder().build();
        bannerAdView.loadAd(adRequest);
    }

    private void startBannerAdRefresh() {
        Runnable bannerRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (bannerAdView != null) {
                    bannerAdView.loadAd(new AdRequest.Builder().build());
                }
                bannerRefreshHandler.postDelayed(this, 45000);
            }
        };
        bannerRefreshHandler.post(bannerRefreshRunnable);
    }

    private void showAdThenStart(Class<?> activity) {
        if (interstitialAd != null) {
            InterstitialAd adToShow = interstitialAd;
            interstitialAd = null;
            adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadInterstitialAd();
                    startActivity(new Intent(MainActivity.this, activity));
                }

                @Override
                public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                    loadInterstitialAd();
                    startActivity(new Intent(MainActivity.this, activity));
                }
            });
            adToShow.show(MainActivity.this);
        } else {
            startActivity(new Intent(MainActivity.this, activity));
        }
    }


    private void showAdThenFinish() {
        if (interstitialAd != null) {
            InterstitialAd adToShow = interstitialAd;
            interstitialAd = null; // Prevent reuse
            adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadInterstitialAd();
                    finish();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                    loadInterstitialAd();
                    finish();
                }
            });
            adToShow.show(MainActivity.this);
        } else {
            finish();
        }
    }

    private void restartActivity() {
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }


    private void showHowToPlayPopup() {
        // Inflate the custom layout for the popup
        View popupView = LayoutInflater.from(this).inflate(R.layout.layout_how_to_play_popup, null);
        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(popupView)
                .create();
        dialog.setCanceledOnTouchOutside(true);

        TextView tvHeading = popupView.findViewById(R.id.tvHeadingPopup);
        View buttonContainer = popupView.findViewById(R.id.buttonContainer);
        TextView tvInstruction = popupView.findViewById(R.id.tvInstructionPopup);
        MaterialButton btnSingleWord = popupView.findViewById(R.id.btnSingleWordPopup);
        MaterialButton btnParagraph = popupView.findViewById(R.id.btnParagraphPopup);

        // Set heading
        tvHeading.setText("How To Play");

        // Set instructions for single word mode
        btnSingleWord.setOnClickListener(view -> {
            String instruction = "SINGLE WORD MODE INSTRUCTIONS:\n\n" +
                    "1. Choose your difficulty: Easy, Medium, or Hard.\n" +
                    "2. Type the displayed word correctly to earn points.\n" +
                    "3. For each correct word, 1 point is given.\n" +
                    "4. Accumulate points before the timer runs out.\n" +
                    "5. Beat your high score!\n\n" +
                    "Good luck!";
            tvInstruction.setText(instruction);
            buttonContainer.setVisibility(View.GONE);
        });

        // Set instructions for paragraph mode
        btnParagraph.setOnClickListener(view -> {
            String instruction = "PARAGRAPH MODE INSTRUCTIONS:\n\n" +
                    "1. A paragraph will be displayed.\n" +
                    "2. Type the entire paragraph exactly as shown before the timer runs out.\n" +
                    "3. Once the whole paragraph is typed or the timer ends, the game will get over.\n" +
                    "4. Your Accuracy and Words Per Minute (WPM) will be shown to you.\n\n" +
                    "Good luck!";
            tvInstruction.setText(instruction);
            buttonContainer.setVisibility(View.GONE);
        });

        dialog.show();
    }
}