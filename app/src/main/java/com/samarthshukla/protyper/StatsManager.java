package com.samarthshukla.protyper;

import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;

public class StatsManager {

    // 1. MULTIPLAYER ENGINE (Tracks Wins, Matches, and Speeds)
    public static void saveMultiplayerStats(String userId, int currentWpm, boolean isWin) {
        if (userId == null || userId.isEmpty()) return;
        DatabaseReference statsRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        statsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Safely grab existing stats (or default to 0 if brand new player)
                int highestWpm = snapshot.hasChild("highest_wpm") ? snapshot.child("highest_wpm").getValue(Integer.class) : 0;
                int totalMatches = snapshot.hasChild("total_matches") ? snapshot.child("total_matches").getValue(Integer.class) : 0;
                int multiplayerMatches = snapshot.hasChild("multiplayer_matches") ? snapshot.child("multiplayer_matches").getValue(Integer.class) : 0;
                int matchesWon = snapshot.hasChild("matches_won") ? snapshot.child("matches_won").getValue(Integer.class) : 0;
                int totalWpmSum = snapshot.hasChild("total_wpm_sum") ? snapshot.child("total_wpm_sum").getValue(Integer.class) : 0;

                // Calculate the new math
                if (currentWpm > highestWpm) highestWpm = currentWpm; // NEW RECORD!
                totalMatches++;
                multiplayerMatches++;
                if (isWin) matchesWon++;
                totalWpmSum += currentWpm; // Used to calculate Average WPM later

                // Package it up and fire it off to Firebase
                HashMap<String, Object> updates = new HashMap<>();
                updates.put("highest_wpm", highestWpm);
                updates.put("total_matches", totalMatches);
                updates.put("multiplayer_matches", multiplayerMatches);
                updates.put("matches_won", matchesWon);
                updates.put("total_wpm_sum", totalWpmSum);

                statsRef.updateChildren(updates);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // 2. SOLO ENGINE (Protects Win Rate, updates everything else)
    public static void saveSoloStats(String userId, int currentWpm) {
        if (userId == null || userId.isEmpty()) return;
        DatabaseReference statsRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        statsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int highestWpm = snapshot.hasChild("highest_wpm") ? snapshot.child("highest_wpm").getValue(Integer.class) : 0;
                int totalMatches = snapshot.hasChild("total_matches") ? snapshot.child("total_matches").getValue(Integer.class) : 0;
                int totalWpmSum = snapshot.hasChild("total_wpm_sum") ? snapshot.child("total_wpm_sum").getValue(Integer.class) : 0;

                if (currentWpm > highestWpm) highestWpm = currentWpm;
                totalMatches++;
                totalWpmSum += currentWpm;

                HashMap<String, Object> updates = new HashMap<>();
                updates.put("highest_wpm", highestWpm);
                updates.put("total_matches", totalMatches);
                updates.put("total_wpm_sum", totalWpmSum);

                statsRef.updateChildren(updates);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}