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

        // 1. Calculate Breakdown Math
        int baseXp = (int) ((wpm * (float) finalAccuracy) / 100f);
        int resultXp = 0;
        int milestoneXp = 0;

        if ("win".equals(resultType)) resultXp = 75;
        else if ("draw".equals(resultType)) resultXp = 30;

        if (finalAccuracy > 80) {
            if ("win".equals(resultType)) milestoneXp = 50;
            else if ("draw".equals(resultType)) milestoneXp = 30;
            else if ("lose".equals(resultType)) milestoneXp = 10;
        }

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
                levelInfoText.setTextColor(Color.parseColor("#FFD700"));
            } else {
                levelInfoText.setText("Level " + cachedLevel + " (" + animatedValue + " / " + maxXpForThisLevel + ")");
            }
        });

        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                // Save globally using our Manager!
                XpManager.saveXpToFirebase(userId, totalEarnedXp);
            }
        });

        animation.start();
    }
}