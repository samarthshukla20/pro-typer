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
    private ProgressBar xpProgressBar;
    private com.google.android.material.card.MaterialCardView
            cardTierNovice, cardTierApprentice, cardTierExpert, cardTierMaster;

    private DatabaseReference userRef;
    private ValueEventListener userXpListener;

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

        // Bind Circular Utility Buttons
        android.widget.ImageButton btnHowToPlay = view.findViewById(R.id.btnHowToPlay);
        android.widget.ImageButton btnAbout = view.findViewById(R.id.btnAbout);

        MainActivity mainActivity = (MainActivity) requireActivity();

        // Apply our custom squish animation to the rows so they feel tactile
        mainActivity.applySquishAnimation(btnHowToPlay);
        mainActivity.applySquishAnimation(btnAbout);

        btnHowToPlay.setOnClickListener(v -> mainActivity.showHowToPlayPopup());
        btnAbout.setOnClickListener(v -> mainActivity.showAdThenStart(AboutActivity.class));

        // Start listening to Firebase for XP data
        loadPlayerDashboard();

        return view;
    }

    private void loadPlayerDashboard() {
        String userId = XpManager.getGlobalUserId(requireContext());
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        userXpListener = new ValueEventListener() {
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

                int previousLevelXp = (currentLevel > 1) ? XpManager.getXpRequiredForNextLevel(currentLevel - 1) : 0;
                int nextLevelXp = XpManager.getXpRequiredForNextLevel(currentLevel);

                int xpInThisLevel = totalXp - previousLevelXp;
                int xpRequiredForNext = nextLevelXp - previousLevelXp;

                String title = XpManager.getTitleForLevel(currentLevel);

                tvPlayerTitle.setText(title);
                highlightActiveTier(title, totalXp);

                if (tvRankUpHint == null) return;
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

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        userRef.addValueEventListener(userXpListener);
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
        // (title comes from XpManager.getTitleForLevel())
        com.google.android.material.card.MaterialCardView activeCard;
        if (title.equalsIgnoreCase("Apprentice"))     activeCard = cardTierApprentice;
        else if (title.equalsIgnoreCase("Expert"))    activeCard = cardTierExpert;
        else if (title.equalsIgnoreCase("Master"))    activeCard = cardTierMaster;
        else                                          activeCard = cardTierNovice;

        activeCard.setCardBackgroundColor(activeBackground);
        activeCard.setStrokeColor(activeStroke);
        activeCard.setStrokeWidth(4);

        // Tier XP boundaries (must match XpManager definitions)
        int noviceMax     = 500;
        int apprenticeMax = 1500;
        int expertMax     = 4000;

        // Determine how much XP the player has within each tier
        int noviceStart     = 0;
        int apprenticeStart = 0;
        int expertStart     = 0;
        int masterStart     = 0;

        // IF player is in Novice tier
        if (title.equalsIgnoreCase("Novice")) {
            noviceStart     = totalXp;       // current progress shown
            apprenticeStart = 0;             // not yet entered
            expertStart     = 0;
            masterStart     = 0;
        }
        // IF player is in Apprentice tier
        else if (title.equalsIgnoreCase("Apprentice")) {
            noviceStart     = noviceMax;     // fully completed
            apprenticeStart = totalXp;       // current progress shown
            expertStart     = 0;
            masterStart     = 0;
        }
        // IF player is in Expert tier
        else if (title.equalsIgnoreCase("Expert")) {
            noviceStart     = noviceMax;
            apprenticeStart = apprenticeMax;
            expertStart     = totalXp;       // current progress shown
            masterStart     = 0;
        }
        // IF player is in Master tier
        else {
            noviceStart     = noviceMax;
            apprenticeStart = apprenticeMax;
            expertStart     = expertMax;
            masterStart     = totalXp;       // current progress shown
        }

        // Set the range text on each tier card
        tvRangeNovice.setText(noviceStart + "–" + noviceMax);
        tvRangeApprentice.setText(apprenticeStart + "–" + apprenticeMax);
        tvRangeExpert.setText(expertStart + "–" + expertMax);
        tvRangeMaster.setText(masterStart + "+");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Prevent memory leaks when navigating away
        if (userRef != null && userXpListener != null) {
            userRef.removeEventListener(userXpListener);
        }
    }
}