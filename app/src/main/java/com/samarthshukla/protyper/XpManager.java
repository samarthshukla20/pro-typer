package com.samarthshukla.protyper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

public class XpManager {

    // ==========================================
    // 1. SINGLE PLAYER MODE
    // ==========================================
    // difficultyMode: 1 = Easy, 2 = Medium, 3 = Hard
    public static int calculateSinglePlayerXp(int difficultyMode, int correctWords) {
        int xpPerWord = 0;
        if (difficultyMode == 1) xpPerWord = 1;
        else if (difficultyMode == 2) xpPerWord = 3;
        else if (difficultyMode == 3) xpPerWord = 5;

        return correctWords * xpPerWord;
    }

    // ==========================================
    // 2. PARAGRAPH MODE
    // ==========================================
    public static int calculateParagraphXp(int wpm, int accuracy, boolean isCompleted) {
        // Safe math: multiplying first before dividing prevents Java from rounding down to 0
        int totalXp = (int) ((wpm * (float) accuracy) / 100f);

        // Completion Bonus
        if (isCompleted) {
            totalXp += 20;
        }

        // Accuracy Bonus Bracket
        if (accuracy > 80) {
            if (wpm >= 0 && wpm <= 30) {
                totalXp += 15;
            } else if (wpm >= 31 && wpm <= 60) {
                totalXp += 30;
            } else if (wpm >= 61 && wpm <= 90) {
                totalXp += 45;
            } else if (wpm >= 91) {
                totalXp += 50;
            }
        }

        return totalXp;
    }

    // ==========================================
    // 3. MULTIPLAYER MODE
    // ==========================================
    // result: "win", "draw", or "lose"
    public static int calculateMultiplayerXp(int wpm, int accuracy, String result) {
        int totalXp = (int) ((wpm * (float) accuracy) / 100f);
        String safeResult = result != null ? result.toLowerCase() : "lose";

        // Result Bonus
        if (safeResult.equals("win")) {
            totalXp += 75;
        } else if (safeResult.equals("draw")) {
            totalXp += 30;
        }
        // Note: Lose adds 0, so we just skip it.

        // Milestone Bonus (> 80% accuracy)
        if (accuracy > 80) {
            if (safeResult.equals("win")) {
                totalXp += 50;
            } else if (safeResult.equals("draw")) {
                totalXp += 30;
            } else if (safeResult.equals("lose")) {
                totalXp += 10;
            }
        }

        return totalXp;
    }

    // ==========================================
    // 4. ADS
    // ==========================================
    public static int getAdBonusXp() {
        return 10;
    }

    // ==========================================
    // 5. PROGRESSION MATH (LEVELS & TITLES)
    // ==========================================

    // Calculates how much total XP is required to reach the NEXT level.
    // Example: Level 1 -> 2 requires 500 XP. Level 2 -> 3 requires 1,000 XP.
    public static int getXpRequiredForNextLevel(int currentLevel) {
        return currentLevel * 500;
    }

    // Determines the player's title based on their current level
    public static String getTitleForLevel(int level) {
        if (level >= 1 && level <= 9) {
            return "Novice";
        } else if (level >= 10 && level <= 19) {
            return "Intermediate";
        } else if (level >= 20 && level <= 29) {
            return "Speedster";
        } else if (level >= 30 && level <= 49) {
            return "Pro Typer";
        } else if (level >= 50) {
            return "Grandmaster";
        }
        return "Beginner"; // Fallback safety
    }

    // ==========================================
    // 6. GLOBAL FIREBASE SAVER
    // ==========================================
    public static void saveXpToFirebase(String userId, int xpToAdd) {
        if (userId == null || xpToAdd <= 0) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Integer currentXp = currentData.child("total_xp").getValue(Integer.class);
                if (currentXp == null) currentXp = 0;

                Integer currentLevel = currentData.child("level").getValue(Integer.class);
                if (currentLevel == null) currentLevel = 1;

                int newTotalXp = currentXp + xpToAdd;

                int newLevel = currentLevel;
                while (newTotalXp >= getXpRequiredForNextLevel(newLevel)) {
                    newLevel++;
                }

                String newTitle = getTitleForLevel(newLevel);

                currentData.child("total_xp").setValue(newTotalXp);
                currentData.child("level").setValue(newLevel);
                currentData.child("current_title").setValue(newTitle);

                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                // Background save complete!
            }
        });
    }

    // ==========================================
    // 7. GLOBAL PLAYER ID (UNIFIED IDENTITY FIX)
    // ==========================================
    public static String getGlobalUserId(android.content.Context context) {
        // 1. FIRST PRIORITY: Check if they are logged in via Firebase Auth
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            return com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        // 2. FALLBACK: If they are not logged in, use the local Guest ID
        android.content.SharedPreferences prefs = context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        String userId = prefs.getString("global_user_id", null);

        // If this is their first time opening the app as a guest, generate an ID
        if (userId == null) {
            userId = "PLAYER_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 15);
            prefs.edit().putString("global_user_id", userId).apply();
        }
        return userId;
    }

}