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

    private TextView tvPlayerTitle, tvPlayerLevel, tvPlayerXp;
    private ProgressBar xpProgressBar;
    private DatabaseReference userRef;
    private ValueEventListener userXpListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Bind ID Card Elements
        tvPlayerTitle = view.findViewById(R.id.tvPlayerTitle);
        tvPlayerLevel = view.findViewById(R.id.tvPlayerLevel);
        tvPlayerXp = view.findViewById(R.id.tvPlayerXp);
        xpProgressBar = view.findViewById(R.id.xpProgressBar);

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
                tvPlayerLevel.setText("Level " + currentLevel);
                tvPlayerXp.setText(xpInThisLevel + " / " + xpRequiredForNext + " XP");

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Prevent memory leaks when navigating away
        if (userRef != null && userXpListener != null) {
            userRef.removeEventListener(userXpListener);
        }
    }
}