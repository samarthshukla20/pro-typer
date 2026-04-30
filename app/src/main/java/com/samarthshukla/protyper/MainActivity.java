package com.samarthshukla.protyper;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private boolean doubleBackToExitPressedOnce = false;
    private Handler doubleBackHandler = new Handler();
    private BroadcastReceiver networkReceiver;
    private InterstitialAd interstitialAd;
    private com.google.android.gms.ads.AdView bannerAdView;
    private Handler bannerRefreshHandler = new Handler();
    private FirebaseAnalytics mFirebaseAnalytics;
    private long sessionStartTime;

    private TextView tvMainTitle;
    private Handler typeHandler = new Handler();
    private String targetText = "PRO TYPER";
    private int charIndex = 0;
    private boolean isDeleting = false;
    private boolean cursorVisible = true;
    private int pauseTicks = 0;

    // --- NEW: FRAGMENT ARCHITECTURE ---
    private Fragment homeFragment = new HomeFragment();
    private Fragment profileFragment = new ProfileFragment();
    private Fragment activeFragment = homeFragment;
    private FragmentManager fragmentManager = getSupportFragmentManager();

    // Track active tab for the 'Back' button
    private String currentTab = "HOME";
    // ----------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null);

        MobileAds.initialize(this, initializationStatus -> {});
        loadInterstitialAd();
        loadBannerAd();
        startBannerAdRefresh();

        tvMainTitle = findViewById(R.id.tvMainTitle);

        // --- NEW: SMART FRAGMENT INITIALIZATION ---
        if (savedInstanceState == null) {
            fragmentManager.beginTransaction().add(R.id.fragment_container, profileFragment, "PROFILE").hide(profileFragment).commit();
            fragmentManager.beginTransaction().add(R.id.fragment_container, homeFragment, "HOME").commit();
            activeFragment = homeFragment;
            currentTab = "HOME";
        } else {
            homeFragment = fragmentManager.findFragmentByTag("HOME");
            profileFragment = fragmentManager.findFragmentByTag("PROFILE");

            currentTab = savedInstanceState.getString("CURRENT_TAB", "HOME");
            if (currentTab.equals("PROFILE")) {
                activeFragment = profileFragment;
                tvMainTitle.setVisibility(View.GONE); // Remember to keep title hidden!
            } else {
                activeFragment = homeFragment;
                tvMainTitle.setVisibility(View.VISIBLE); // Remember to show title!
            }
        }
        // ------------------------------------------

        setupBottomNavigation();
        setupThemeAndBackground();
        startTypewriterAnimation();

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) { String token = task.getResult(); }
        });
    }

    private void setupBottomNavigation() {
        View tabHistory = findViewById(R.id.tabHistory);
        View tabHome = findViewById(R.id.tabHome);
        View tabProfile = findViewById(R.id.tabProfile);
        View navIndicator = findViewById(R.id.navIndicator);

        android.widget.ImageView iconHistory = findViewById(R.id.iconHistory);
        android.widget.ImageView iconHome = findViewById(R.id.iconHome);
        android.widget.ImageView iconProfile = findViewById(R.id.iconProfile);

        int colorSelected = android.graphics.Color.parseColor("#FFFFFF");
        int colorUnselected = android.graphics.Color.parseColor("#64B5F6");

        // Snap to the CORRECT tab on initial load/theme change
        tabHome.post(() -> {
            // 1. Calculate the target width manually
            int newPillWidth = tabHome.getWidth() - 48;

            // 2. Set the layout params
            android.view.ViewGroup.LayoutParams params = navIndicator.getLayoutParams();
            params.width = newPillWidth;
            navIndicator.setLayoutParams(params);

            // 3. Use our manual width for the centering math!
            float centerOffset;
            if (currentTab.equals("PROFILE")) {
                centerOffset = tabProfile.getX() + (tabProfile.getWidth() / 2f) - (newPillWidth / 2f);
                navIndicator.setX(centerOffset);
                iconProfile.setColorFilter(colorSelected);
                iconHome.setColorFilter(colorUnselected);
                iconHistory.setColorFilter(colorUnselected);
            } else {
                centerOffset = tabHome.getX() + (tabHome.getWidth() / 2f) - (newPillWidth / 2f);
                navIndicator.setX(centerOffset);
                iconHome.setColorFilter(colorSelected);
                iconProfile.setColorFilter(colorUnselected);
                iconHistory.setColorFilter(colorUnselected);
            }
        });

        tabHome.setOnClickListener(v -> {
            if (activeFragment != homeFragment) {
                fragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .hide(activeFragment).show(homeFragment).commit();
                activeFragment = homeFragment;
                currentTab = "HOME";
            }
            tvMainTitle.setVisibility(View.VISIBLE);

            float targetX = tabHome.getX() + (tabHome.getWidth() / 2f) - (navIndicator.getWidth() / 2f);
            navIndicator.animate().cancel();
            navIndicator.animate().x(targetX).setDuration(300)
                    .setInterpolator(new android.view.animation.OvershootInterpolator()).start();

            iconHome.setColorFilter(colorSelected);
            iconProfile.setColorFilter(colorUnselected);

            // Subtle click bump
            iconHome.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction(() ->
                    iconHome.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            ).start();
        });

        tabProfile.setOnClickListener(v -> {
            if (activeFragment != profileFragment) {
                fragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .hide(activeFragment).show(profileFragment).commit();
                activeFragment = profileFragment;
                currentTab = "PROFILE";
            }
            tvMainTitle.setVisibility(View.GONE);

            float targetX = tabProfile.getX() + (tabProfile.getWidth() / 2f) - (navIndicator.getWidth() / 2f);
            navIndicator.animate().cancel();
            navIndicator.animate().x(targetX).setDuration(300)
                    .setInterpolator(new android.view.animation.OvershootInterpolator()).start();

            iconProfile.setColorFilter(colorSelected);
            iconHome.setColorFilter(colorUnselected);

            // Subtle click bump
            iconProfile.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction(() ->
                    iconProfile.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            ).start();
        });

        tabHistory.setOnClickListener(v -> {
            float targetX = tabHistory.getX() + (tabHistory.getWidth() / 2f) - (navIndicator.getWidth() / 2f);
            navIndicator.animate().cancel();
            navIndicator.animate().x(targetX).setDuration(300)
                    .setInterpolator(new android.view.animation.OvershootInterpolator()).start();

            iconHistory.setColorFilter(colorSelected);

            // Subtle click bump
            iconHistory.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction(() ->
                    iconHistory.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            ).start();

            tvMainTitle.setVisibility(View.VISIBLE);

            new Handler().postDelayed(() -> {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }, 200);
        });
    }

    private void setupThemeAndBackground() {
        int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        String[] bgColors;

        if (isDarkMode) {
            bgColors = new String[]{"#020C1B", "#0A192F", "#112240", "#233554"};
        } else {
            bgColors = new String[]{"#E3F2FD", "#BBDEFB", "#90CAF9", "#64B5F6"};
        }

        View rootLayout = findViewById(R.id.drawer_layout);
        startFullScreenGroovyWave(rootLayout, bgColors);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sessionStartTime = System.currentTimeMillis();
        mFirebaseAnalytics.logEvent("session_start", null);
        checkInternetOnStart();



        // Notification checks
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

        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isInternetAvailable()) { showRetryInternetDialog(); }
            }
        };
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Only run this when the app actually regains focus and is fully drawn on the screen!
        if (hasFocus) {
            View navIndicator = findViewById(R.id.navIndicator);
            View tabHome = findViewById(R.id.tabHome);
            View tabProfile = findViewById(R.id.tabProfile);
            View tabHistory = findViewById(R.id.tabHistory);

            android.widget.ImageView iconHistory = findViewById(R.id.iconHistory);
            android.widget.ImageView iconHome = findViewById(R.id.iconHome);
            android.widget.ImageView iconProfile = findViewById(R.id.iconProfile);

            int colorSelected = android.graphics.Color.parseColor("#FFFFFF");
            int colorUnselected = android.graphics.Color.parseColor("#64B5F6"); // Make sure this matches your unselected icon color!

            // Double check that the layout actually has a width before doing math
            if (navIndicator != null && tabHome != null && tabHome.getWidth() > 0) {
                int newPillWidth = tabHome.getWidth() - 48;

                android.view.ViewGroup.LayoutParams params = navIndicator.getLayoutParams();
                if (params.width != newPillWidth) {
                    params.width = newPillWidth;
                    navIndicator.setLayoutParams(params);
                }

                float centerOffset;
                if ("PROFILE".equals(currentTab)) {
                    centerOffset = tabProfile.getX() + (tabProfile.getWidth() / 2f) - (newPillWidth / 2f);
                    iconProfile.setColorFilter(colorSelected);
                    iconHome.setColorFilter(colorUnselected);
                    iconHistory.setColorFilter(colorUnselected);
                } else {
                    centerOffset = tabHome.getX() + (tabHome.getWidth() / 2f) - (newPillWidth / 2f);
                    iconHome.setColorFilter(colorSelected);
                    iconProfile.setColorFilter(colorUnselected);
                    iconHistory.setColorFilter(colorUnselected);
                }

                // Cancel any lingering animations and lock the pill exactly in place
                navIndicator.animate().cancel();
                navIndicator.setX(centerOffset);
            }
        }
    }


    // ==========================================
    // UTILITY METHODS DELEGATED TO FRAGMENTS
    // ==========================================

    @SuppressLint("ClickableViewAccessibility")
    public void applySquishAnimation(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }

    public void showAdThenStart(Class<?> activity) {
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

    public void showHowToPlayPopup() {
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

        tvHeading.setText("How To Play");

        btnSingleWord.setOnClickListener(view -> {
            String instruction = "SINGLE WORD MODE INSTRUCTIONS:\n\n1. Choose your difficulty.\n2. Type the displayed word correctly.\n3. 1 point is given per word.\n4. Beat your high score!";
            tvInstruction.setText(instruction);
            buttonContainer.setVisibility(View.GONE);
        });

        btnParagraph.setOnClickListener(view -> {
            String instruction = "PARAGRAPH MODE INSTRUCTIONS:\n\n1. A paragraph will be displayed.\n2. Type the entire paragraph exactly.\n3. Your Accuracy and Words Per Minute (WPM) will be shown.";
            tvInstruction.setText(instruction);
            buttonContainer.setVisibility(View.GONE);
        });

        dialog.show();
    }


    // ==========================================
    // ANIMATION ENGINES
    // ==========================================

    private void startTypewriterAnimation() {
        if (tvMainTitle == null) return;
        typeHandler.post(new Runnable() {
            @Override
            public void run() {
                int delay = 150;
                if (!isDeleting) {
                    if (charIndex < targetText.length()) {
                        charIndex++; cursorVisible = true; delay = 150;
                    } else {
                        if (pauseTicks < 6) {
                            pauseTicks++; cursorVisible = !cursorVisible; delay = 500;
                        } else {
                            pauseTicks = 0; isDeleting = true; cursorVisible = true; delay = 150;
                        }
                    }
                } else {
                    if (charIndex > 0) {
                        charIndex--; cursorVisible = true; delay = 50;
                    } else {
                        if (pauseTicks < 2) {
                            pauseTicks++; cursorVisible = !cursorVisible; delay = 500;
                        } else {
                            pauseTicks = 0; isDeleting = false; cursorVisible = true; delay = 150;
                        }
                    }
                }

                String displayText = targetText.substring(0, charIndex);
                if (cursorVisible) { displayText += "|"; }
                else if (displayText.isEmpty()) { displayText = " "; }

                tvMainTitle.setText(displayText);
                typeHandler.postDelayed(this, delay);
            }
        });
    }

    private void startFullScreenGroovyWave(View backgroundView, String[] hexColors) {
        if (backgroundView == null || hexColors.length < 3) return;

        class GroovyBackgroundDrawable extends android.graphics.drawable.Drawable {
            private android.graphics.Paint[] paints;
            private android.graphics.Path path = new android.graphics.Path();
            private float timeOffset = 0f;

            public GroovyBackgroundDrawable() {
                paints = new android.graphics.Paint[hexColors.length];
                for (int i = 0; i < hexColors.length; i++) {
                    paints[i] = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    paints[i].setColor(android.graphics.Color.parseColor(hexColors[i]));
                    paints[i].setStyle(android.graphics.Paint.Style.FILL);
                }
            }

            @Override
            public void draw(@NonNull android.graphics.Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                float width = bounds.width(); float height = bounds.height();
                canvas.drawRect(bounds, paints[0]);

                for (int i = 1; i < paints.length; i++) {
                    path.reset(); path.moveTo(0, height); path.lineTo(0, height * 0.1f);
                    float amplitude = height * 0.12f;
                    float frequency = (float) (Math.PI / width) * 1.2f;
                    float speedModifier = i * 0.7f; float phaseShift = timeOffset * speedModifier;
                    float verticalOffset = (height * 0.22f) * i;

                    for (float x = 0; x <= width + 30; x += 30) {
                        float y = (float) Math.sin((x * frequency) + phaseShift) * amplitude + verticalOffset;
                        path.lineTo(x, y);
                    }
                    path.lineTo(width, height); path.close();
                    canvas.drawPath(path, paints[i]);
                }
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
            public void updateTime(float time) { this.timeOffset = time; invalidateSelf(); }
        }

        GroovyBackgroundDrawable waveDrawable = new GroovyBackgroundDrawable();
        backgroundView.setBackground(waveDrawable);
        backgroundView.post(() -> {
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, (float) (Math.PI * 100));
            animator.setDuration(200000); animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            animator.addUpdateListener(anim -> waveDrawable.updateTime((float) anim.getAnimatedValue()));
            animator.start();
        });
    }

    // ==========================================
    // LIFECYCLE & BACKGROUND MAINTENANCE
    // ==========================================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (typeHandler != null) { typeHandler.removeCallbacksAndMessages(null); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkReceiver != null) { unregisterReceiver(networkReceiver); }
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        Bundle bundle = new Bundle(); bundle.putLong("session_duration_ms", sessionDuration);
        mFirebaseAnalytics.logEvent("session_end", bundle);
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) { showAdThenFinish(); return; }
        doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit.", Toast.LENGTH_SHORT).show();
        doubleBackHandler.postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save which tab we are currently looking at before a theme change
        outState.putString("CURRENT_TAB", currentTab);
    }

    private void checkInternetOnStart() { if (!isInternetAvailable()) { showRetryInternetDialog(); } }
    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) { NetworkInfo netInfo = cm.getActiveNetworkInfo(); return netInfo != null && netInfo.isConnected(); }
        return false;
    }
    private void showRetryInternetDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("Whoops!!").setMessage("It seems you're offline. Check your internet connection and try again!")
                .setCancelable(false).setPositiveButton("Retry", (dialog, which) -> {
                    if (isInternetAvailable()) { restartActivity(); } else { showRetryInternetDialog(); }
                }).show();
    }
    private void showConfirmExitDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("Exit").setMessage("You're about to leave. Do you really want to exit?")
                .setCancelable(false).setPositiveButton("Yes", (dialog, which) -> showAdThenFinish()).setNegativeButton("No", (dialog, which) -> dialog.dismiss()).show();
    }

    private void loadInterstitialAd() {
        InterstitialAd.load(this, getString(R.string.Interstitial), new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) { interstitialAd = ad; }
            @Override public void onAdFailedToLoad(LoadAdError adError) { interstitialAd = null; new Handler().postDelayed(MainActivity.this::loadInterstitialAd, 30000); }
        });
    }

    private void loadBannerAd() {
        bannerAdView = findViewById(R.id.bannerAdView);
        bannerAdView.loadAd(new AdRequest.Builder().build());
    }

    private void startBannerAdRefresh() {
        Runnable bannerRefreshRunnable = new Runnable() {
            @Override public void run() {
                if (bannerAdView != null) { bannerAdView.loadAd(new AdRequest.Builder().build()); }
                bannerRefreshHandler.postDelayed(this, 45000);
            }
        };
        bannerRefreshHandler.post(bannerRefreshRunnable);
    }

    private void showAdThenFinish() {
        if (interstitialAd != null) {
            InterstitialAd adToShow = interstitialAd; interstitialAd = null;
            adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdDismissedFullScreenContent() { finish(); }
                @Override public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) { finish(); }
            });
            adToShow.show(MainActivity.this);
        } else { finish(); }
    }

    private void restartActivity() {
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent); finish();
    }
}