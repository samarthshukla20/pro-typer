package com.samarthshukla.protyper;

import android.animation.Animator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT_TYPE = "extra_result_type"; // "win"|"lose"|"draw"
    public static final String EXTRA_MY_WPM = "extra_my_wpm";
    public static final String EXTRA_MY_ACC = "extra_my_acc";
    public static final String EXTRA_OPP_WPM = "extra_opp_wpm";
    public static final String EXTRA_OPP_ACC = "extra_opp_acc";
    public static final String EXTRA_WINNER_ID = "extra_winner_id";
    public static final String EXTRA_YOUR_ID = "extra_your_id";

    private String userId;
    private int cachedTotalXp = 0;
    private int cachedLevel = 1;

    // --- NEW: FLAG TO DETECT FRIEND MATCHES ---
    private boolean isFriendMatch = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        TextView titleTv = findViewById(R.id.gameOverTitle);
        TextView messageTv = findViewById(R.id.gameOverMessageInside);
        TextView myAccuracyTv = findViewById(R.id.scoreTextInsideCard);
        TextView myWpmTv = findViewById(R.id.wpmTextView);
        Button menuBtn = findViewById(R.id.menuButton);
        Button retryBtn = findViewById(R.id.retryButton);

        Intent i = getIntent();
        String resultType = i.getStringExtra(EXTRA_RESULT_TYPE);
        int myWpm = i.getIntExtra(EXTRA_MY_WPM, 0);
        int myAcc = i.getIntExtra(EXTRA_MY_ACC, 0);
        userId = i.getStringExtra(EXTRA_YOUR_ID);

        // Retrieve the pre-fetched XP data & Friend Match Flag!
        cachedTotalXp = i.getIntExtra("cachedTotalXp", 0);
        cachedLevel = i.getIntExtra("cachedLevel", 1);
        isFriendMatch = i.getBooleanExtra("isFriendMatch", false);

        // Fallback if Intent misses the ID: Ask the unified XpManager!
        if (userId == null) {
            userId = XpManager.getGlobalUserId(this);
        }

        // Set Title & Subtitle
        if ("win".equals(resultType)) {
            titleTv.setText("You Win! 🏆");
            messageTv.setText("Great job — you finished first!");
        } else if ("lose".equals(resultType)) {
            titleTv.setText("You Lost 😔");
            messageTv.setText("Opponent did better this time.");
        } else if ("draw".equals(resultType)) {
            titleTv.setText("Draw 🤝");
            messageTv.setText("Both of you were evenly matched.");
        } else {
            titleTv.setText("Game Over");
            messageTv.setText("Match finished.");
        }

        // Set Stats
        myAccuracyTv.setText("Accuracy: " + myAcc + "%");
        myWpmTv.setText("WPM: " + myWpm);

        // --- NEW: DECIDE WHETHER TO REWARD XP OR SHOW UNRANKED UI ---
        if (userId != null) {
            if (isFriendMatch) {
                showFriendlyMatchUi(); // Kills the XP engine and hides the bar
            } else {
                animateXpAndSave(myWpm, myAcc, resultType); // Ranked match! Reward them!
            }
        }

        menuBtn.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });

        retryBtn.setOnClickListener(v -> {
            Intent lobbyIntent = new Intent(ResultActivity.this, MultiplayerLobbyActivity.class);
            startActivity(lobbyIntent);
            finish();
        });
    }

    // ==========================================
    // THE LEVEL UP ANIMATION ENGINE (XML VERSION)
    // ==========================================
    private void playLevelUpAnimation(int newLevel) {

        // --- CRASH PREVENTER ---
        if (isFinishing() || isDestroyed()) {
            return;
        }

        android.app.Dialog levelUpDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        levelUpDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        levelUpDialog.setContentView(R.layout.dialog_level_up);
        levelUpDialog.setCancelable(false);

        android.widget.TextView tvLevelSubtitle = levelUpDialog.findViewById(R.id.tvLevelSubtitle);
        tvLevelSubtitle.setText("You reached Level " + newLevel);

        levelUpDialog.show();

        android.view.View textContainer = levelUpDialog.findViewById(R.id.textContainer);
        textContainer.setScaleX(0.3f);
        textContainer.setScaleY(0.3f);
        textContainer.animate().scaleX(1.1f).scaleY(1.1f).setDuration(800)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .withEndAction(() -> textContainer.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()).start();

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isDestroyed() && !isFinishing() && levelUpDialog.isShowing()) {
                levelUpDialog.dismiss();
            }
        }, 3000);
    }

    // ==========================================
    // STRATEGIC REVIEW PROMPT ENGINE
    // ==========================================
    private void checkAndShowReviewDialog(int finalLevel, String resultType, boolean didLevelUp) {
        if (!"win".equalsIgnoreCase(resultType)) return; // Only ask when they are happy!

        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        int state = prefs.getInt("review_state", 0);

        if (state == 2) return; // State 2 = Done forever. Do not bother them.

        boolean shouldShow = false;
        android.content.SharedPreferences.Editor editor = prefs.edit();

        if (state == 0) {
            // State 0: Tracking the first 5 wins
            int winCount = prefs.getInt("mp_win_count", 0) + 1;
            editor.putInt("mp_win_count", winCount).apply();

            if (winCount == 5) {
                shouldShow = true;
            }
        } else if (state == 1) {
            // State 1: The Sprinter Backup Plan (Level 11)
            if (finalLevel >= 11) {
                shouldShow = true;
            }
        }

        if (shouldShow) {
            // Delay showing the dialog so it doesn't overlap with the Level Up explosion!
            long delay = didLevelUp ? 3500 : 1000;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showReviewDialog(state);
                }
            }, delay);
        }
    }

    private void showReviewDialog(int currentState) {
        androidx.appcompat.app.AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        android.view.View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_rate_app, null);
        builder.setView(dialogView);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setCancelable(false); // Force an answer

        com.google.android.material.button.MaterialButton btnYes = dialogView.findViewById(R.id.btnRateYes);
        com.google.android.material.button.MaterialButton btnNo = dialogView.findViewById(R.id.btnRateNo);

        btnYes.setOnClickListener(v -> {
            // State 2 = Lock it forever
            getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE).edit().putInt("review_state", 2).apply();
            dialog.dismiss();

            // Safely open the Google Play Store
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=" + getPackageName())));
            } catch (android.content.ActivityNotFoundException e) {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.samarthshukla.protyper&pcampaignid=web_share" + getPackageName())));
            }
        });

        btnNo.setOnClickListener(v -> {
            // If State 0 -> Go to State 1. If State 1 -> Go to State 2.
            int nextState = (currentState == 0) ? 1 : 2;
            getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE).edit().putInt("review_state", nextState).apply();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ==========================================
    // NEW: UNRANKED FRIENDLY MATCH UI
    // ==========================================
    private void showFriendlyMatchUi() {
        TextView baseXpText = findViewById(R.id.baseXpText);
        TextView resultXpText = findViewById(R.id.resultXpText);
        TextView milestoneXpText = findViewById(R.id.milestoneXpText);
        TextView xpEarnedText = findViewById(R.id.xpEarnedText);
        ProgressBar xpProgressBar = findViewById(R.id.xpProgressBar);
        TextView levelInfoText = findViewById(R.id.levelInfoText);

        // Hide all the competitive XP breakdown text
        if (baseXpText != null) baseXpText.setVisibility(View.GONE);
        if (resultXpText != null) resultXpText.setVisibility(View.GONE);
        if (milestoneXpText != null) milestoneXpText.setVisibility(View.GONE);
        if (xpProgressBar != null) xpProgressBar.setVisibility(View.GONE);
        if (levelInfoText != null) levelInfoText.setVisibility(View.GONE);

        // Replace the main XP text with a cool Unranked badge
        if (xpEarnedText != null) {
            xpEarnedText.setText("Friendly Match: Unranked");
            xpEarnedText.setTextColor(Color.parseColor("#8892B0")); // A sleek slate grey
        }
    }

    // ==========================================
    // MULTIPLAYER XP ANIMATION ENGINE (RANKED)
    // ==========================================
    private void animateXpAndSave(int wpm, int finalAccuracy, String resultType) {
        TextView baseXpText = findViewById(R.id.baseXpText);
        TextView resultXpText = findViewById(R.id.resultXpText);
        TextView milestoneXpText = findViewById(R.id.milestoneXpText);
        TextView xpEarnedText = findViewById(R.id.xpEarnedText);
        ProgressBar xpProgressBar = findViewById(R.id.xpProgressBar);
        TextView levelInfoText = findViewById(R.id.levelInfoText);

        if (xpEarnedText == null || xpProgressBar == null) return;

        // --- NEW: FETCH RECEIPT DETAILS FROM XpManager ---
        int baseXp = XpManager.getMultiplayerBaseXp(wpm, finalAccuracy);
        int resultXp = XpManager.getMultiplayerResultBonus(resultType);
        int milestoneXp = XpManager.getMultiplayerMilestoneBonus(finalAccuracy, resultType);

        final int totalEarnedXp = baseXp + resultXp + milestoneXp;

        // 2. Populate UI Receipt (Only show bonuses if > 0)
        if (baseXpText != null) {
            baseXpText.setText("Base XP: +" + baseXp);
        }
        if (resultXp > 0 && resultXpText != null) {
            resultXpText.setText("Match Result: +" + resultXp);
            resultXpText.setVisibility(View.VISIBLE);
        }
        if (milestoneXp > 0 && milestoneXpText != null) {
            milestoneXpText.setText("Accuracy Milestone: +" + milestoneXp);
            milestoneXpText.setVisibility(View.VISIBLE);
        }

        xpEarnedText.setText("Total: +" + totalEarnedXp + " XP");

        // 3. Run the Animation Engine
        int previousLevelXp = (cachedLevel > 1) ? XpManager.getXpRequiredForNextLevel(cachedLevel - 1) : 0;
        int nextLevelXp = XpManager.getXpRequiredForNextLevel(cachedLevel);

        int currentXpInThisLevel = cachedTotalXp - previousLevelXp;
        int maxXpForThisLevel = nextLevelXp - previousLevelXp;
        int newXpInThisLevel = currentXpInThisLevel + totalEarnedXp;

        xpProgressBar.setMax(maxXpForThisLevel);
        levelInfoText.setText("Level " + cachedLevel + " (" + currentXpInThisLevel + " / " + maxXpForThisLevel + ")");

        android.animation.ObjectAnimator animation = android.animation.ObjectAnimator.ofInt(xpProgressBar, "progress", currentXpInThisLevel, newXpInThisLevel);
        animation.setDuration(1200);

        animation.addUpdateListener(anim -> {
            int animatedValue = (int) anim.getAnimatedValue();
            if (animatedValue >= maxXpForThisLevel) {
                levelInfoText.setText("Level UP!");
                levelInfoText.setTextColor(android.graphics.Color.parseColor("#FFD700"));
            } else {
                levelInfoText.setText("Level " + cachedLevel + " (" + animatedValue + " / " + maxXpForThisLevel + ")");
            }
        });

        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                super.onAnimationEnd(animation);

                int oldLevel = cachedLevel;

                cachedTotalXp += totalEarnedXp;
                while (cachedTotalXp >= XpManager.getXpRequiredForNextLevel(cachedLevel)) {
                    cachedLevel++;
                }

                XpManager.saveXpToFirebase(userId, totalEarnedXp);

                boolean didLevelUp = (cachedLevel > oldLevel);

                if (didLevelUp) {
                    playLevelUpAnimation(cachedLevel);
                }

                // --- NEW: TRIGGER THE REVIEW LOGIC ---
                // We pass in if they leveled up so it knows how long to wait!
                checkAndShowReviewDialog(cachedLevel, resultType, didLevelUp);
            }
        });

        animation.start();
    }
}