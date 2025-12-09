package com.samarthshukla.protyper;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
    private ValueEventListener gameStartListener;
    private TextView statusTextView;
    private MaterialButton findMatchButton;
    private MaterialButton rulesButton;
    private boolean findingMatch = false;
    private String finalGameSessionIdForHost = null;
    // Animated "..." on status text
    private Handler statusHandler = new Handler();
    private Runnable statusDotsRunnable;
    private boolean animateStatusDots = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_lobby);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        statusTextView = findViewById(R.id.statusTextView);
        findMatchButton = findViewById(R.id.findMatchButton);
        rulesButton = findViewById(R.id.rulesButton);

        statusTextView.setText("Signing in...");

        rulesButton.setOnClickListener(v -> showRulesDialog());

        signInAnonymously();
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously().addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    userId = mAuth.getCurrentUser().getUid();
                    statusTextView.setText("Ready to find a match");
                    findMatchButton.setVisibility(View.VISIBLE);
                    findMatchButton.setEnabled(true);
                    findMatchButton.setOnClickListener(v -> findMatch());
                } else {
                    Toast.makeText(MultiplayerLobbyActivity.this,
                            "Authentication failed.", Toast.LENGTH_SHORT).show();
                    statusTextView.setText("Authentication failed. Please restart.");
                }
            }
        });
    }

    private void findMatch() {
        if (findingMatch) return;
        findingMatch = true;

        findMatchButton.setEnabled(false);
        statusTextView.setText("Finding a match");
        startStatusDotsAnimation(); // animate "..." on status text

        DatabaseReference matchmakingRef = mDatabase.child("matchmaking_queue");
        final String myUserId = userId;

        finalGameSessionIdForHost = null;

        matchmakingRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String opponentId = null;
                for (MutableData player : currentData.getChildren()) {
                    if (!player.getKey().equals(userId)
                            && player.child("gameSessionId").getValue() == null) {
                        opponentId = player.getKey();
                        break;
                    }
                }

                if (opponentId != null) {
                    // HOST PATH
                    String gameSessionId = UUID.randomUUID().toString();
                    final String finalOpponentId = opponentId;

                    finalGameSessionIdForHost = gameSessionId;

                    // Assign gameSessionId to opponent, remove host from queue
                    currentData.child(finalOpponentId).child("gameSessionId")
                            .setValue(gameSessionId);
                    currentData.child(myUserId).setValue(null);

                    return Transaction.success(currentData);
                } else {
                    // No match -> join queue
                    Map<String, Object> playerEntry = new HashMap<>();
                    playerEntry.put("userId", userId);
                    playerEntry.put("timestamp", ServerValue.TIMESTAMP);
                    currentData.child(userId).setValue(playerEntry);
                    return Transaction.success(currentData);
                }
            }

            @Override
            public void onComplete(@Nullable DatabaseError error,
                                   boolean committed,
                                   @Nullable DataSnapshot currentData) {

                findingMatch = false;

                if (error != null) {
                    stopStatusDotsAnimation();
                    Toast.makeText(MultiplayerLobbyActivity.this,
                            "Matchmaking failed: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    statusTextView.setText("Matchmaking failed. Try again.");
                    findMatchButton.setEnabled(true);
                    return;
                }

                if (!committed || currentData == null) {
                    stopStatusDotsAnimation();
                    statusTextView.setText("Matchmaking failed. Try again.");
                    findMatchButton.setEnabled(true);
                    return;
                }

                // HOST: we have created a session id locally
                if (finalGameSessionIdForHost != null) {
                    String gameSessionIdToCreate = finalGameSessionIdForHost;
                    finalGameSessionIdForHost = null;

                    String randomParagraph = getRandomParagraphFromAssets();

                    Map<String, Object> gameData = new HashMap<>();
                    gameData.put("status", "in_progress");
                    gameData.put("paragraph_text", randomParagraph);

                    Map<String, String> playersMap = new HashMap<>();
                    playersMap.put(myUserId, "player1");

                    // Best-effort: find opponent from queue snapshot
                    try {
                        for (DataSnapshot child : currentData.getChildren()) {
                            String key = child.getKey();
                            if (key != null && !key.equals(myUserId)) {
                                String assigned = child.child("gameSessionId")
                                        .getValue(String.class);
                                if (assigned != null
                                        && assigned.equals(gameSessionIdToCreate)) {
                                    playersMap.put(key, "player2");
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    gameData.put("players", playersMap);

                    mDatabase.child("game_sessions").child(gameSessionIdToCreate)
                            .setValue(gameData)
                            .addOnCompleteListener(task -> {
                                stopStatusDotsAnimation();
                                statusTextView.setText("Match found! Starting game...");
                                startGameActivity(gameSessionIdToCreate);
                            });

                    return;
                }

                // JOINER / WAITER
                DataSnapshot myDataSnapshot = currentData.child(userId);
                String finalGameSessionId =
                        myDataSnapshot.child("gameSessionId").getValue(String.class);

                if (finalGameSessionId != null && !finalGameSessionId.isEmpty()) {
                    stopStatusDotsAnimation();
                    statusTextView.setText("Match found! Starting game...");
                    startGameActivity(finalGameSessionId);
                } else if (currentData.child(userId).exists()) {
                    statusTextView.setText("Waiting for opponent");
                    startStatusDotsAnimation();
                    listenForGameStart();
                } else {
                    // Entry removed but no session yet; just wait.
                    statusTextView.setText("Waiting for match to start");
                    startStatusDotsAnimation();
                    listenForGameStart();
                }
            }
        });
    }

    private void listenForGameStart() {
        if (gameStartListener != null) {
            mDatabase.child("matchmaking_queue")
                    .child(userId)
                    .removeEventListener(gameStartListener);
        }

        DatabaseReference myQueueRef =
                mDatabase.child("matchmaking_queue").child(userId);

        gameStartListener = myQueueRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String gameSessionId =
                        snapshot.child("gameSessionId").getValue(String.class);

                if (gameSessionId != null && !gameSessionId.isEmpty()) {
                    myQueueRef.removeEventListener(this);
                    stopStatusDotsAnimation();
                    statusTextView.setText("Match found! Starting game...");
                    new Handler().postDelayed(() -> startGameActivity(gameSessionId), 400);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                stopStatusDotsAnimation();
                Toast.makeText(MultiplayerLobbyActivity.this,
                        "Error listening for match.", Toast.LENGTH_SHORT).show();
                statusTextView.setText("Error finding match. Try again.");
                findMatchButton.setEnabled(true);
            }
        });
    }

    private void startGameActivity(String gameSessionId) {
        if (gameSessionId == null || gameSessionId.isEmpty()) {
            Toast.makeText(this,
                    "Error: Invalid game session ID. Please try again.",
                    Toast.LENGTH_SHORT).show();
            statusTextView.setText("Ready to find a match");
            findMatchButton.setEnabled(true);
            return;
        }

        if (gameStartListener != null) {
            mDatabase.child("matchmaking_queue").child(userId)
                    .removeEventListener(gameStartListener);
        }

        // Remove this user from queue
        mDatabase.child("matchmaking_queue").child(userId).removeValue();

        Intent intent = new Intent(this, MultiplayerGameActivity.class);
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
        return "The quick brown fox jumps over the lazy dog.";
    }

    private void showRulesDialog() {
        String rulesText =
                "• Both players type the same paragraph for 120 seconds.\n\n" +
                        "• First to finish correctly wins immediately.\n\n" +
                        "• If time runs out, higher accuracy wins. If tied, higher WPM wins.\n\n" +
                        "• Leaving the room gives the win to the other player.\n\n" +
                        "• If someone disconnects, the other player can wait or claim victory.";

        new AlertDialog.Builder(this)
                .setTitle("Match Rules")
                .setMessage(rulesText)
                .setPositiveButton("Got it", null)
                .show();
    }

    // Animate "Finding a match...", "Waiting for opponent..." etc.
    private void startStatusDotsAnimation() {
        animateStatusDots = true;
        if (statusDotsRunnable != null) {
            statusHandler.removeCallbacks(statusDotsRunnable);
        }

        statusDotsRunnable = new Runnable() {
            int dotCount = 0;
            String baseText = statusTextView.getText().toString();

            @Override
            public void run() {
                if (!animateStatusDots) return;

                String cleanBase = baseText.replace("...", "");
                StringBuilder sb = new StringBuilder(cleanBase);
                for (int i = 0; i < dotCount; i++) sb.append(".");
                statusTextView.setText(sb.toString());

                dotCount = (dotCount + 1) % 4;
                statusHandler.postDelayed(this, 500);
            }
        };
        statusHandler.post(statusDotsRunnable);
    }

    private void stopStatusDotsAnimation() {
        animateStatusDots = false;
        if (statusDotsRunnable != null) {
            statusHandler.removeCallbacks(statusDotsRunnable);
            statusDotsRunnable = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStatusDotsAnimation();

        if (gameStartListener != null && userId != null) {
            mDatabase.child("matchmaking_queue").child(userId)
                    .removeEventListener(gameStartListener);
        }
        if (userId != null) {
            mDatabase.child("matchmaking_queue").child(userId).removeValue();
        }
    }
}
