package com.samarthshukla.protyper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
    private TextView tvRangeNovice, tvRangeApprentice, tvRangeExpert, tvRangeMaster;

    // --- NEW: STATS TEXTVIEWS ---
    private TextView tvProfileTopSpeed, tvProfileAvgSpeed, tvProfileMatches, tvProfileWinRate;

    private ProgressBar xpProgressBar;
    private com.google.android.material.card.MaterialCardView
            cardTierNovice, cardTierApprentice, cardTierExpert, cardTierMaster;

    private DatabaseReference userRef;
    private ValueEventListener userListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Bind ID Card Elements
        tvPlayerTitle = view.findViewById(R.id.tvPlayerTitle);
        tvRankUpHint   = view.findViewById(R.id.tvRankUpHint);
        tvXpEarned     = view.findViewById(R.id.tvXpEarned);
        tvNextRank     = view.findViewById(R.id.tvNextRank);
        tvXpRemaining  = view.findViewById(R.id.tvXpRemaining);
        xpProgressBar = view.findViewById(R.id.xpProgressBar);

        tvRangeNovice     = view.findViewById(R.id.tvRangeNovice);
        tvRangeApprentice = view.findViewById(R.id.tvRangeApprentice);
        tvRangeExpert     = view.findViewById(R.id.tvRangeExpert);
        tvRangeMaster     = view.findViewById(R.id.tvRangeMaster);

        // Bind Rank Tier Cards
        cardTierNovice     = view.findViewById(R.id.cardTierNovice);
        cardTierApprentice = view.findViewById(R.id.cardTierApprentice);
        cardTierExpert     = view.findViewById(R.id.cardTierExpert);
        cardTierMaster     = view.findViewById(R.id.cardTierMaster);

        // --- NEW: BIND STATS VIEWS ---
        tvProfileTopSpeed = view.findViewById(R.id.tvProfileTopSpeed);
        tvProfileAvgSpeed = view.findViewById(R.id.tvProfileAvgSpeed);
        tvProfileMatches  = view.findViewById(R.id.tvProfileMatches);
        tvProfileWinRate  = view.findViewById(R.id.tvProfileWinRate);

        // Bind Circular Utility Buttons
        android.widget.ImageButton btnHowToPlay = view.findViewById(R.id.btnHowToPlay);
        android.widget.ImageButton btnAbout = view.findViewById(R.id.btnAbout);

        MainActivity mainActivity = (MainActivity) requireActivity();

        // Apply our custom squish animation to the rows so they feel tactile
        mainActivity.applySquishAnimation(btnHowToPlay);
        mainActivity.applySquishAnimation(btnAbout);

        btnHowToPlay.setOnClickListener(v -> mainActivity.showHowToPlayPopup());
        btnAbout.setOnClickListener(v -> mainActivity.showAdThenStart(AboutActivity.class));

        // Start listening to Firebase for XP and Stats data
        loadPlayerDashboard();

        return view;
    }

    private void loadPlayerDashboard() {
        String userId = XpManager.getGlobalUserId(requireContext());
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (tvPlayerTitle == null) return;

                int totalXp = 0;
                int currentLevel = 1;

                if (snapshot.exists()) {
                    Integer dbXp = snapshot.child("total_xp").getValue(Integer.class);
                    Integer dbLevel = snapshot.child("level").getValue(Integer.class);
                    if (dbXp != null) totalXp = dbXp;
                    if (dbLevel != null) currentLevel = dbLevel;
                }

                // 1. PROCESS XP & TIERS
                int previousLevelXp = (currentLevel > 1) ? XpManager.getXpRequiredForNextLevel(currentLevel - 1) : 0;
                int nextLevelXp = XpManager.getXpRequiredForNextLevel(currentLevel);

                int xpInThisLevel = totalXp - previousLevelXp;
                int xpRequiredForNext = nextLevelXp - previousLevelXp;

                String title = XpManager.getTitleForLevel(currentLevel);

                tvPlayerTitle.setText(title);
                highlightActiveTier(title, totalXp);

                if (tvRankUpHint != null) {
                    String nextRankName = XpManager.getTitleForLevel(currentLevel + 1);
                    int xpToGo = nextLevelXp - totalXp;

                    tvRankUpHint.setText("Level " + currentLevel + " · Rank up at " + nextLevelXp + " XP");
                    tvXpEarned.setText(totalXp + " XP earned");
                    tvNextRank.setText(nextLevelXp + " XP · " + nextRankName);
                    tvXpRemaining.setText(xpToGo + " XP to go");

                    xpProgressBar.setMax(xpRequiredForNext);

                    android.animation.ObjectAnimator.ofInt(xpProgressBar, "progress", xpProgressBar.getProgress(), xpInThisLevel)
                            .setDuration(800)
                            .start();
                }

                // --- NEW: 2. PROCESS LIFETIME STATS ---
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

                    // Color code the Win Rate for extra aesthetic
                    if (winRate >= 50) {
                        tvProfileWinRate.setTextColor(android.graphics.Color.parseColor("#39FF14")); // Neon Green for good
                    } else if (winRate > 0) {
                        tvProfileWinRate.setTextColor(android.graphics.Color.parseColor("#FF007A")); // Neon Pink for bad
                    } else {
                        tvProfileWinRate.setTextColor(android.graphics.Color.parseColor("#FFFFFF")); // White if 0
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        userRef.addValueEventListener(userListener);
    }

    private void highlightActiveTier(String title, int totalXp) {
        // Define active and inactive styles
        int activeBackground   = 0xFF6C63FF;
        int inactiveBackground = 0xFF0A192F;
        int activeStroke       = 0xFFFFFFFF;
        int inactiveStroke     = 0xFF1E3A5F;

        // Reset all 4 cards to inactive first
        com.google.android.material.card.MaterialCardView[] allCards = {
                cardTierNovice, cardTierApprentice, cardTierExpert, cardTierMaster
        };
        for (com.google.android.material.card.MaterialCardView card : allCards) {
            card.setCardBackgroundColor(inactiveBackground);
            card.setStrokeColor(inactiveStroke);
            card.setStrokeWidth(2);
        }

        // Highlight the matching card based on title string
        com.google.android.material.card.MaterialCardView activeCard;
        if (title.equalsIgnoreCase("Apprentice"))     activeCard = cardTierApprentice;
        else if (title.equalsIgnoreCase("Expert"))    activeCard = cardTierExpert;
        else if (title.equalsIgnoreCase("Master"))    activeCard = cardTierMaster;
        else                                          activeCard = cardTierNovice;

        activeCard.setCardBackgroundColor(activeBackground);
        activeCard.setStrokeColor(activeStroke);
        activeCard.setStrokeWidth(4);

        // Tier XP boundaries
        int noviceMax     = 500;
        int apprenticeMax = 1500;
        int expertMax     = 4000;

        int noviceStart     = 0;
        int apprenticeStart = 0;
        int expertStart     = 0;
        int masterStart     = 0;

        if (title.equalsIgnoreCase("Novice")) {
            noviceStart = totalXp;
        } else if (title.equalsIgnoreCase("Apprentice")) {
            noviceStart = noviceMax;
            apprenticeStart = totalXp;
        } else if (title.equalsIgnoreCase("Expert")) {
            noviceStart = noviceMax;
            apprenticeStart = apprenticeMax;
            expertStart = totalXp;
        } else {
            noviceStart = noviceMax;
            apprenticeStart = apprenticeMax;
            expertStart = expertMax;
            masterStart = totalXp;
        }

        tvRangeNovice.setText(noviceStart + "–" + noviceMax);
        tvRangeApprentice.setText(apprenticeStart + "–" + apprenticeMax);
        tvRangeExpert.setText(expertStart + "–" + expertMax);
        tvRangeMaster.setText(masterStart + "+");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Prevent memory leaks when navigating away
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
    }
}