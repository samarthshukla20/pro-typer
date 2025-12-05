package com.samarthshukla.protyper;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MultiplayerLobbyActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String userId;
    // Removed unused gameSessionId field for clarity
    private ValueEventListener gameSessionListener;
    private ValueEventListener gameStartListener;

    private TextView statusTextView;
    private MaterialButton findMatchButton;
    private boolean findingMatch = false;

    // 🚨 FINAL HOST FIX FIELD: Temporarily stores the generated ID for the HOST path
    private String finalGameSessionIdForHost = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_lobby);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        statusTextView = findViewById(R.id.statusTextView);
        findMatchButton = findViewById(R.id.findMatchButton);

        signInAnonymously();
    }

    private void signInAnonymously() {
        statusTextView.setText("Signing in...");
        mAuth.signInAnonymously().addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    userId = mAuth.getCurrentUser().getUid();
                    statusTextView.setText("Signed in as: " + userId);
                    findMatchButton.setVisibility(View.VISIBLE);
                    findMatchButton.setOnClickListener(v -> findMatch());
                } else {
                    Toast.makeText(MultiplayerLobbyActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void findMatch() {
        if (findingMatch) {
            return;
        }
        findingMatch = true;
        statusTextView.setText("Finding a match...");
        findMatchButton.setEnabled(false);

        DatabaseReference matchmakingRef = mDatabase.child("matchmaking_queue");
        final String myUserId = userId;

        finalGameSessionIdForHost = null; // Reset local host ID tracker

        matchmakingRef.runTransaction(new Transaction.Handler() {

            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String opponentId = null;
                for (MutableData player : currentData.getChildren()) {
                    // Only consider players who are NOT me AND who are currently WAITING
                    if (!player.getKey().equals(userId) && player.child("gameSessionId").getValue() == null) {
                        opponentId = player.getKey();
                        break;
                    }
                }

                if (opponentId != null) {
                    // Match Found (HOST PATH)
                    String gameSessionId = UUID.randomUUID().toString();
                    final String finalOpponentId = opponentId;

                    // 🚨 HOST FIX 1: Store ID locally before committing the transaction
                    // Note: This sets the outer-scope variable which we will read in onComplete().
                    finalGameSessionIdForHost = gameSessionId;

                    // 1. Assign the ID to the Joiner (Opponent) and remove only the Host
                    currentData.child(finalOpponentId).child("gameSessionId").setValue(gameSessionId);
                    currentData.child(myUserId).setValue(null); // Host removes self from queue

                    // IMPORTANT: Do NOT create the full game_sessions node here (side-effecting write).
                    // We will create it once in onComplete() after the transaction commits.

                    return Transaction.success(currentData);
                } else {
                    // No match, join the queue and wait.
                    Map<String, Object> playerEntry = new HashMap<>();
                    playerEntry.put("userId", userId);
                    playerEntry.put("timestamp", ServerValue.TIMESTAMP);
                    currentData.child(userId).setValue(playerEntry);

                    return Transaction.success(currentData);
                }
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                findingMatch = false;
                if (error != null) {
                    Toast.makeText(MultiplayerLobbyActivity.this, "Transaction failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    findMatchButton.setEnabled(true);
                    statusTextView.setText("Ready to find match.");
                } else if (committed) {

                    // 1. 🚨 HOST PATH CHECK: If the local ID was set, I am the Host.
                    if (finalGameSessionIdForHost != null) {
                        // Host successfully initiated the match and removed their queue entry.
                        // Create the game session data ONCE (moved out of the transaction to avoid races).
                        final String gameSessionIdToCreate = finalGameSessionIdForHost;
                        finalGameSessionIdForHost = null; // Clear local variable

                        // Build the game session payload (paragraph etc.)
                        String randomParagraph = getRandomParagraphFromAssets();
                        Map<String, Object> gameData = new HashMap<>();
                        gameData.put("status", "in_progress");
                        gameData.put("paragraph_text", randomParagraph);
                        Map<String, String> playersMap = new HashMap<>();
                        playersMap.put(myUserId, "player1");

                        // Attempt to get opponent id from the currentData snapshot (it's the one we assigned)
                        // If not found, we'll still proceed but the other party will handle waiting/cleanup.
                        try {
                            // Look for the entry that was set to gameSessionId
                            for (DataSnapshot child : currentData.getChildren()) {
                                String key = child.getKey();
                                if (key != null && !key.equals(myUserId)) {
                                    String assignedGameId = child.child("gameSessionId").getValue(String.class);
                                    if (assignedGameId != null && assignedGameId.equals(gameSessionIdToCreate)) {
                                        playersMap.put(key, "player2");
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // best-effort: if we cannot read snapshot, continue — the joiner will still start.
                        }

                        gameData.put("players", playersMap);

                        // Create the game session node once
                        mDatabase.child("game_sessions").child(gameSessionIdToCreate).setValue(gameData)
                                .addOnCompleteListener(task -> {
                                    // Start the host game activity regardless of setValue success — if write failed,
                                    // the host will log and the joiner logic will wait or show an error.
                                    startGameActivity(gameSessionIdToCreate);
                                });

                        return;
                    }

                    // 2. JOINER/WAITER PATH:
                    DataSnapshot myDataSnapshot = currentData.child(userId);
                    String finalGameSessionId = myDataSnapshot.child("gameSessionId").getValue(String.class);

                    if (finalGameSessionId != null && !finalGameSessionId.isEmpty()) {
                        // I was the Joiner, and the Host set the gameSessionId in my entry.
                        startGameActivity(finalGameSessionId);
                    } else if (currentData.child(userId).exists()) {
                        // I am waiting (Waiter Path).
                        statusTextView.setText("Waiting for an opponent...");
                        listenForGameStart();
                    }
                    // If my entry was removed by the Host, my listener should have already fired (or will soon).
                }
            }
        });
    }


    private void listenForGameStart() {
        // First, cancel any existing listener before starting a new one
        if (gameStartListener != null) {
            mDatabase.child("matchmaking_queue").child(userId).removeEventListener(gameStartListener);
        }

        DatabaseReference myQueueRef = mDatabase.child("matchmaking_queue").child(userId);
        gameStartListener = myQueueRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String gameSessionId = snapshot.child("gameSessionId").getValue(String.class);

                // Check for a valid ID before starting the game.
                if (gameSessionId != null && !gameSessionId.isEmpty()) {
                    // Game session created by the opponent, start the game.

                    // 🚨 CRITICAL FIX: Remove the listener immediately to prevent re-triggering.
                    myQueueRef.removeEventListener(this);

                    // ⏲️ CRITICAL FIX: Introduce a short delay (500ms) for the Joiner
                    if (!isFinishing()) {
                        statusTextView.setText("Match found! Starting game...");
                        new Handler().postDelayed(() -> {
                            if (!isFinishing()) {
                                startGameActivity(gameSessionId);
                            }
                        }, 500);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MultiplayerLobbyActivity.this, "Error listening for match.", Toast.LENGTH_SHORT).show();
                findMatchButton.setEnabled(true);
            }
        });
    }

    private void startGameActivity(String gameSessionId) {
        if (gameSessionId == null || gameSessionId.isEmpty()) {
            Toast.makeText(MultiplayerLobbyActivity.this, "Error: Invalid game session ID. Please try again.", Toast.LENGTH_SHORT).show();
            statusTextView.setText("Ready to find match.");
            findMatchButton.setEnabled(true);
            return;
        }

        if (gameStartListener != null) {
            mDatabase.child("matchmaking_queue").child(userId).removeEventListener(gameStartListener);
        }

        // 🚨 CLEANUP: The Host's removal is in the transaction.
        // The Joiner's entry must be removed here, as the listener is stopped.
        mDatabase.child("matchmaking_queue").child(userId).removeValue();

        Intent intent = new Intent(MultiplayerLobbyActivity.this, MultiplayerGameActivity.class);
        intent.putExtra("gameSessionId", gameSessionId);
        intent.putExtra("userId", userId);
        startActivity(intent);
        finish();
    }

    private String getRandomParagraphFromAssets() {
        List<String> paragraphs = new ArrayList<>();
        try {
            InputStream is = getAssets().open("pg.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            reader.close();
            String fullText = builder.toString();
            String[] parts = fullText.split("(?m)^\\s*\\d+\\.\\s*");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    paragraphs.add(trimmed);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!paragraphs.isEmpty()) {
            Random random = new Random();
            return paragraphs.get(random.nextInt(paragraphs.size()));
        }
        return "The quick brown fox jumps over the lazy dog."; // Default text
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up listeners and queue entries
        if (gameStartListener != null) {
            mDatabase.child("matchmaking_queue").child(userId).removeEventListener(gameStartListener);
        }
        if (userId != null) {
            // Note: This cleanup is essential for preventing ghost users if the app is closed *while waiting*.
            mDatabase.child("matchmaking_queue").child(userId).removeValue();
        }
    }
}