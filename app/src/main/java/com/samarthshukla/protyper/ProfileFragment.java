package com.samarthshukla.protyper;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileFragment extends Fragment {

    private TextView tvPlayerTitle, tvRankUpHint, tvXpEarned, tvNextRank, tvXpRemaining;
    private TextView tvRangeKeystroke, tvRangeSprinter, tvRangeVelocity, tvRangeSupersonic, tvRangeLightspeed;
    private android.widget.ImageView ivCurrentBadge;

    private TextView tvProfileTopSpeed, tvProfileAvgSpeed, tvProfileMatches, tvProfileWinRate;
    private ProgressBar xpProgressBar;
    private com.google.android.material.card.MaterialCardView cardTierKeystroke, cardTierSprinter, cardTierVelocity, cardTierSupersonic, cardTierLightspeed;
    private HorizontalScrollView rankScrollView; // --- NEW: SCROLL VIEW VARIABLE ---

    private DatabaseReference userRef;
    private ValueEventListener userListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Bind ID Card Elements
        tvPlayerTitle = view.findViewById(R.id.tvPlayerTitle);
        ivCurrentBadge = view.findViewById(R.id.ivCurrentBadge);
        tvRankUpHint   = view.findViewById(R.id.tvRankUpHint);
        tvXpEarned     = view.findViewById(R.id.tvXpEarned);
        tvXpRemaining  = view.findViewById(R.id.tvXpRemaining);
        xpProgressBar = view.findViewById(R.id.xpProgressBar);

        // --- NEW: BIND THE SCROLL VIEW ---
        rankScrollView = view.findViewById(R.id.rankScrollView);

        tvRangeKeystroke  = view.findViewById(R.id.tvRangeKeystroke);
        tvRangeSprinter   = view.findViewById(R.id.tvRangeSprinter);
        tvRangeVelocity   = view.findViewById(R.id.tvRangeVelocity);
        tvRangeSupersonic = view.findViewById(R.id.tvRangeSupersonic);
        tvRangeLightspeed = view.findViewById(R.id.tvRangeLightspeed);

        // Bind Rank Tier Cards
        cardTierKeystroke  = view.findViewById(R.id.cardTierKeystroke);
        cardTierSprinter   = view.findViewById(R.id.cardTierSprinter);
        cardTierVelocity   = view.findViewById(R.id.cardTierVelocity);
        cardTierSupersonic = view.findViewById(R.id.cardTierSupersonic);
        cardTierLightspeed = view.findViewById(R.id.cardTierLightspeed);

        tvProfileTopSpeed = view.findViewById(R.id.tvProfileTopSpeed);
        tvProfileAvgSpeed = view.findViewById(R.id.tvProfileAvgSpeed);
        tvProfileMatches  = view.findViewById(R.id.tvProfileMatches);
        tvProfileWinRate  = view.findViewById(R.id.tvProfileWinRate);

        android.widget.ImageButton btnHowToPlay = view.findViewById(R.id.btnHowToPlay);
        android.widget.ImageButton btnAbout = view.findViewById(R.id.btnAbout);

        MainActivity mainActivity = (MainActivity) requireActivity();
        mainActivity.applySquishAnimation(btnHowToPlay);
        mainActivity.applySquishAnimation(btnAbout);

        btnHowToPlay.setOnClickListener(v -> mainActivity.showHowToPlayPopup());
        btnAbout.setOnClickListener(v -> mainActivity.showAdThenStart(AboutActivity.class));

        loadPlayerDashboard();

        View cardKeystroke = view.findViewById(R.id.cardTierKeystroke);
        View cardSprinter = view.findViewById(R.id.cardTierSprinter);
        View cardVelocity = view.findViewById(R.id.cardTierVelocity);
        View cardSupersonic = view.findViewById(R.id.cardTierSupersonic);
        View cardLightspeed = view.findViewById(R.id.cardTierLightspeed);

        cardKeystroke.setOnClickListener(v -> showBadgeDialog(
                R.drawable.ic_keystroke, "Keystroke", "Levels 1 - 10", android.graphics.Color.parseColor("#64B5F6"),
                "The starting line. You are learning the ropes and getting your fingers warmed up for the arena."
        ));

        cardSprinter.setOnClickListener(v -> showBadgeDialog(
                R.drawable.ic_sprinter, "Sprinter", "Levels 11 - 20", android.graphics.Color.parseColor("#64B5F6"),
                "You're picking up the pace! Your muscle memory is locking in and your WPM is rising."
        ));

        cardVelocity.setOnClickListener(v -> showBadgeDialog(
                R.drawable.ic_velocity, "Velocity", "Levels 21 - 30", android.graphics.Color.parseColor("#FF7043"),
                "Blistering speed. You are now faster than the average typist and leaving opponents in the dust."
        ));

        cardSupersonic.setOnClickListener(v -> showBadgeDialog(
                R.drawable.ic_supersonic, "Supersonic", "Levels 31 - 40", android.graphics.Color.parseColor("#FFD700"),
                "Elite tier. Your hands move faster than sound. Only the most dedicated reach this rank."
        ));

        cardLightspeed.setOnClickListener(v -> showBadgeDialog(
                R.drawable.ic_lightspeed, "Lightspeed", "Level 40+", android.graphics.Color.parseColor("#00E5FF"),
                "The pinnacle of Pro Typer. You type at the speed of light. You are a legendary competitor."
        ));

        return view;
    }

    private void loadPlayerDashboard() {
        String userId = XpManager.getGlobalUserId(requireContext());
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || tvPlayerTitle == null) return;

                int totalXp = 0;
                int currentLevel = 1;

                if (snapshot.exists()) {
                    Integer dbXp = snapshot.child("total_xp").getValue(Integer.class);
                    Integer dbLevel = snapshot.child("level").getValue(Integer.class);
                    if (dbXp != null) totalXp = dbXp;
                    if (dbLevel != null) currentLevel = dbLevel;
                }

                int previousLevelXp = (currentLevel > 1) ? XpManager.getXpRequiredForNextLevel(currentLevel - 1) : 0;
                int nextLevelXp = XpManager.getXpRequiredForNextLevel(currentLevel);

                int xpInThisLevel = totalXp - previousLevelXp;
                int xpRequiredForNext = nextLevelXp - previousLevelXp;

                String title = XpManager.getTitleForLevel(currentLevel);

                tvPlayerTitle.setText(title);
                highlightActiveTier(title);

                if (tvRankUpHint != null) {
                    int xpToGo = nextLevelXp - totalXp;
                    tvRankUpHint.setText("Level " + currentLevel + " · Rank up at " + nextLevelXp + " XP");
                    tvXpEarned.setText(totalXp + " XP earned");
                    tvXpRemaining.setText(xpToGo + " XP to go");

                    xpProgressBar.setMax(xpRequiredForNext);

                    android.animation.ObjectAnimator.ofInt(xpProgressBar, "progress", xpProgressBar.getProgress(), xpInThisLevel)
                            .setDuration(800)
                            .start();
                }

                int highestWpm = 0;
                int totalMatches = 0;
                int totalWpmSum = 0;
                int multiplayerMatches = 0;
                int matchesWon = 0;

                if (snapshot.hasChild("highest_wpm")) highestWpm = snapshot.child("highest_wpm").getValue(Integer.class);
                if (snapshot.hasChild("total_matches")) totalMatches = snapshot.child("total_matches").getValue(Integer.class);
                if (snapshot.hasChild("total_wpm_sum")) totalWpmSum = snapshot.child("total_wpm_sum").getValue(Integer.class);
                if (snapshot.hasChild("multiplayer_matches")) multiplayerMatches = snapshot.child("multiplayer_matches").getValue(Integer.class);
                if (snapshot.hasChild("matches_won")) matchesWon = snapshot.child("matches_won").getValue(Integer.class);

                int avgWpm = (totalMatches > 0) ? (totalWpmSum / totalMatches) : 0;
                int winRate = (multiplayerMatches > 0) ? (int) (((float) matchesWon / multiplayerMatches) * 100) : 0;

                if (tvProfileTopSpeed != null) {
                    tvProfileTopSpeed.setText(highestWpm + " WPM");
                    tvProfileAvgSpeed.setText(avgWpm + " WPM");
                    tvProfileMatches.setText(String.valueOf(totalMatches));
                    tvProfileWinRate.setText(winRate + " %");

                    int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    boolean isDarkMode = (currentNightMode == Configuration.UI_MODE_NIGHT_YES);

                    if (winRate >= 50) {
                        tvProfileWinRate.setTextColor(android.graphics.Color.parseColor("#39FF14"));
                    } else if (winRate > 0) {
                        tvProfileWinRate.setTextColor(android.graphics.Color.parseColor("#FF007A"));
                    } else {
                        tvProfileWinRate.setTextColor(isDarkMode ? 0xFFFFFFFF : 0xFF000000);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        userRef.addValueEventListener(userListener);
    }

    private void highlightActiveTier(String title) {
        if (!isAdded() || getContext() == null) return;

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = (currentNightMode == Configuration.UI_MODE_NIGHT_YES);

        int activeBackground;
        int inactiveBackground;
        int inactiveStroke;
        int activeStroke = 0xFF0284C7;

        if (isDarkMode) {
            activeBackground   = 0xFF1F2A38;
            inactiveBackground = 0xFF1F2A38;
            inactiveStroke     = 0xFF334155;
        } else {
            activeBackground   = 0xF2E3F0FF;
            inactiveBackground = 0xF2E3F0FF;
            inactiveStroke     = 0xFFE2E8F0;
        }

        com.google.android.material.card.MaterialCardView[] allCards = {
                cardTierKeystroke, cardTierSprinter, cardTierVelocity,
                cardTierSupersonic, cardTierLightspeed
        };

        for (com.google.android.material.card.MaterialCardView card : allCards) {
            card.setCardBackgroundColor(inactiveBackground);
            card.setStrokeColor(inactiveStroke);
            card.setStrokeWidth(2);
        }

        com.google.android.material.card.MaterialCardView activeCard;
        int badgeResId = R.drawable.ic_keystroke;

        if (title.equalsIgnoreCase("Sprinter")) {
            activeCard = cardTierSprinter;
            badgeResId = R.drawable.ic_sprinter;
        } else if (title.equalsIgnoreCase("Velocity")) {
            activeCard = cardTierVelocity;
            badgeResId = R.drawable.ic_velocity;
        } else if (title.equalsIgnoreCase("Supersonic")) {
            activeCard = cardTierSupersonic;
            badgeResId = R.drawable.ic_supersonic;
        } else if (title.equalsIgnoreCase("Lightspeed")) {
            activeCard = cardTierLightspeed;
            badgeResId = R.drawable.ic_lightspeed;
        } else {
            activeCard = cardTierKeystroke;
        }

        activeCard.setCardBackgroundColor(activeBackground);
        activeCard.setStrokeColor(activeStroke);
        activeCard.setStrokeWidth(10);

        if (ivCurrentBadge != null) {
            ivCurrentBadge.setImageResource(badgeResId);
        }

        tvRangeKeystroke.setText("Lv. 1–10");
        tvRangeSprinter.setText("Lv. 11–20");
        tvRangeVelocity.setText("Lv. 21–30");
        tvRangeSupersonic.setText("Lv. 31–40");
        tvRangeLightspeed.setText("Lv. 40+");

        // --- NEW: AUTO-CENTER THE SCROLL VIEW! ---
        if (rankScrollView != null) {
            // We use .post() to wait a tiny millisecond to guarantee the UI has drawn the cards
            // before we try to calculate their mathematical widths!
            rankScrollView.post(() -> {
                int cardCenter = activeCard.getLeft() + (activeCard.getWidth() / 2);
                int scrollCenter = rankScrollView.getWidth() / 2;
                int targetScrollX = cardCenter - scrollCenter;

                // Smoothly slide over to the active card
                rankScrollView.smoothScrollTo(targetScrollX, 0);
            });
        }
        // ------------------------------------------
    }

    private void showBadgeDialog(int imageResId, String title, String range, int color, String description) {
        if (!isAdded() || getContext() == null) return;

        android.view.View dialogView = android.view.LayoutInflater.from(getContext()).inflate(R.layout.dialog_badge_info, null);

        android.widget.ImageView badgeImage = dialogView.findViewById(R.id.dialogBadgeImage);
        android.widget.TextView badgeTitle = dialogView.findViewById(R.id.dialogBadgeTitle);
        android.widget.TextView badgeRange = dialogView.findViewById(R.id.dialogBadgeRange);
        android.widget.TextView badgeDesc = dialogView.findViewById(R.id.dialogBadgeDescription);

        badgeImage.setImageResource(imageResId);
        badgeTitle.setText(title);
        badgeTitle.setTextColor(color);
        badgeRange.setText(range);
        badgeDesc.setText(description);

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                .setView(dialogView)
                .setBackground(androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.card_background))
                .create();

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
    }
}