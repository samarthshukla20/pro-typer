package com.samarthshukla.protyper;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MultiplayerGameActivity extends AppCompatActivity {

    private static final String TAG = "MultiplayerGameActivity";

    private TextView wordDisplay, timerText;
    private EditText inputField;
    private ProgressBar myProgressBar, opponentProgressBar;
    private DatabaseReference gameRef;
    private String gameSessionId, userId, opponentId;
    private String currentParagraph = "";

    private CountDownTimer timer;
    private static final int TIME_LIMIT = 120000; // 120 seconds
    private long gameStartTime;
    private boolean isGameOver = false;
    private boolean isGameStarted = false;

    private ValueEventListener opponentListener;
    private ValueEventListener finalScoreListener;
    private ValueEventListener playersListener;
    private ValueEventListener opponentPresenceListener;
    private ValueEventListener gameResultListener;   // NEW: listens for winnerId / reason

    // New fields for debounce / reconnect window
    private Handler disconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable disconnectRunnable;
    private AlertDialog disconnectDialog;
    private static final int OPPONENT_RECONNECT_TIMEOUT_MS = 30000; // 30s

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_game);

        gameSessionId = getIntent().getStringExtra("gameSessionId");
        userId = getIntent().getStringExtra("userId");

        if (gameSessionId == null || userId == null) {
            Toast.makeText(this, "Failed to start game: Missing data.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        wordDisplay = findViewById(R.id.multiplayerWordDisplay);
        timerText = findViewById(R.id.multiplayerTimerText);
        inputField = findViewById(R.id.multiplayerInputField);
        myProgressBar = findViewById(R.id.myProgressBar);
        opponentProgressBar = findViewById(R.id.opponentProgressBar);

        gameRef = FirebaseDatabase.getInstance().getReference("game_sessions").child(gameSessionId);

        fetchOpponentIdAndParagraph();
    }

    /**
     * Revised method to fetch opponent ID and paragraph text.
     */
    private void fetchOpponentIdAndParagraph() {
        playersListener = gameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && !isGameStarted) {
                    DataSnapshot playersSnapshot = snapshot.child("players");
                    String paragraphText = snapshot.child("paragraph_text").getValue(String.class);

                    // 1. Try to find the opponent ID
                    String tempOpponentId = null;
                    if (playersSnapshot.exists() && playersSnapshot.getChildrenCount() == 2) {
                        for (DataSnapshot player : playersSnapshot.getChildren()) {
                            String playerId = player.getKey();
                            if (playerId != null && !playerId.equals(userId)) {
                                tempOpponentId = playerId;
                                break;
                            }
                        }
                    }

                    // 2. Check if all required data is present
                    if (tempOpponentId != null && paragraphText != null && !paragraphText.isEmpty()) {
                        opponentId = tempOpponentId; // Final assignment
                        currentParagraph = paragraphText;
                        wordDisplay.setText(currentParagraph);

                        // CRUCIAL: Remove listener before starting the game to prevent re-triggering
                        gameRef.removeEventListener(playersListener);
                        startMultiplayerGame();
                    } else {
                        Log.d(TAG, "Waiting for game session data to be fully populated. Current state: "
                                + "opponentId=" + tempOpponentId + ", para=" + (paragraphText != null));
                        // Keep listening until all data arrives
                    }
                } else if (!snapshot.exists()) {
                    Log.e(TAG, "Game session not found. Finishing.");
                    // When room node disappears, treat it as room closed by other side / host left.
                    Toast.makeText(MultiplayerGameActivity.this, "Room closed.", Toast.LENGTH_SHORT).show();
                    safeRemoveAllListeners();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase read failed: " + error.getMessage());
                Toast.makeText(MultiplayerGameActivity.this, "Failed to load game data.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void startMultiplayerGame() {
        if (isGameStarted) return;
        isGameStarted = true;

        isGameOver = false;
        inputField.setEnabled(true);
        inputField.setText("");
        inputField.requestFocus();
        gameStartTime = System.currentTimeMillis();

        // Set my presence and onDisconnect handler
        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        myPresenceRef.setValue(true);
        // We tell Firebase to set this to false when the connection is lost.
        myPresenceRef.onDisconnect().setValue(false);

        startTimer();
        setupTypingListener();
        setupOpponentListener();
        setupGameResultListener();   // NEW: listen for winnerId / reason

        // Setup the opponent disconnect listener (relies on opponentId being set)
        if (opponentId != null) {
            setupOpponentDisconnectListener();
        } else {
            Log.e(TAG, "Opponent ID missing when starting game, cannot track presence.");
        }
    }

    private void startTimer() {
        timer = new CountDownTimer(TIME_LIMIT, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                timerText.setText("Time left: " + secondsRemaining + "s");
            }

            public void onFinish() {
                // Timer expired -> normal end of match; compute & write our final score and wait for opponent
                gameOver();
            }
        }.start();
    }

    private void setupTypingListener() {
        inputField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String typedText = s.toString();
                updateProgressBar(typedText.length());
                highlightText(typedText);
                updateProgressOnFirebase(typedText.length());

                // When a player completes the paragraph (exact match), handle immediate win.
                if (typedText.length() == currentParagraph.length() && typedText.equals(currentParagraph)) {
                    handlePlayerCompletedEarly();
                }
            }
        });
    }

    private void highlightText(String typedText) {
        SpannableStringBuilder spannable = new SpannableStringBuilder();
        int minLength = Math.min(typedText.length(), currentParagraph.length());
        int dullGreen = 0xFF228B22;

        for (int i = 0; i < minLength; i++) {
            char typedChar = typedText.charAt(i);
            char correctChar = currentParagraph.charAt(i);
            SpannableString spanChar = new SpannableString(String.valueOf(correctChar));
            if (typedChar == correctChar) {
                spanChar.setSpan(new ForegroundColorSpan(dullGreen), 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                spanChar.setSpan(new ForegroundColorSpan(0xFFFF0000), 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            spannable.append(spanChar);
        }
        if (typedText.length() < currentParagraph.length()) {
            spannable.append(currentParagraph.substring(typedText.length()));
        }
        wordDisplay.setText(spannable);
    }

    private void updateProgressBar(int charsTyped) {
        if (currentParagraph != null && currentParagraph.length() > 0) {
            int progress = (int) ((charsTyped / (float) currentParagraph.length()) * 100);
            myProgressBar.setProgress(progress);
        }
    }

    private void updateProgressOnFirebase(int charsTyped) {
        DatabaseReference playerRef = gameRef.child("players_progress").child(userId);
        playerRef.child("chars_typed").setValue(charsTyped);
    }

    private void setupOpponentListener() {
        DatabaseReference opponentRef = gameRef.child("players_progress").child(opponentId);
        opponentListener = opponentRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Long charsTyped = snapshot.child("chars_typed").getValue(Long.class);
                    if (charsTyped != null && currentParagraph.length() > 0) {
                        int progress = (int) ((charsTyped / (float) currentParagraph.length()) * 100);
                        opponentProgressBar.setProgress(progress);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    /**
     * NEW: Listen for explicit game result so the losing side sees proper lose message
     * instead of the opponent-disconnected dialog.
     */
    private void setupGameResultListener() {
        if (gameResultListener != null) {
            gameRef.child("result").removeEventListener(gameResultListener);
        }

        gameResultListener = gameRef.child("result").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                String winnerId = snapshot.child("winnerId").getValue(String.class);
                String reason = snapshot.child("reason").getValue(String.class);

                if (winnerId == null || winnerId.isEmpty()) return;

                // If this client already ended the game (e.g. winner wrote result), ignore.
                if (isGameOver) return;

                isGameOver = true;

                // Stop timers and input; cancel any disconnect dialog
                if (timer != null) timer.cancel();
                inputField.setEnabled(false);
                cancelOpponentDisconnectTimer();

                boolean iWon = winnerId.equals(userId);
                String msg;
                if (iWon) {
                    if ("completed_paragraph".equals(reason)) {
                        msg = "You completed the paragraph. You win! 🏆";
                    } else {
                        msg = "You win! 🏆";
                    }
                } else {
                    if ("completed_paragraph".equals(reason)) {
                        msg = "Opponent completed the paragraph. You lost. 😔";
                    } else {
                        msg = "You lost. 😔";
                    }
                }

                Toast.makeText(MultiplayerGameActivity.this, msg, Toast.LENGTH_LONG).show();
                safeRemoveAllListeners();
                new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 2000);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "gameResultListener cancelled: " + error.getMessage());
            }
        });
    }

    private void setupOpponentDisconnectListener() {
        // 🛡️ Guard added in startMultiplayerGame, safe to assume opponentId is valid here.
        DatabaseReference opponentPresenceRef = gameRef.child("players_progress").child(opponentId).child("is_present");
        opponentPresenceListener = opponentPresenceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isPresent = snapshot.getValue(Boolean.class);

                // If isPresent is true, the opponent is online — cancel any pending disconnect timer.
                if (isPresent != null && isPresent) {
                    Log.d(TAG, "Opponent presence: ONLINE -> cancel disconnect timer if any.");
                    cancelOpponentDisconnectTimer();
                } else {
                    // Opponent is missing OR marked offline. Start the graceful reconnection flow.
                    Log.d(TAG, "Opponent presence: OFFLINE or NULL -> starting reconnection flow.");
                    onPossibleOpponentDisconnect();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Opponent presence listener cancelled: " + error.getMessage());
            }
        });
    }

    /**
     * Called when we detect a possible opponent disconnect. This starts a countdown (OPPONENT_RECONNECT_TIMEOUT_MS)
     * giving the opponent a chance to reconnect. If they reconnect, cancelOpponentDisconnectTimer() must be called.
     */
    private void onPossibleOpponentDisconnect() {
        if (isGameOver) return; // already finished

        // If a timer is already running, do nothing.
        if (disconnectRunnable != null) {
            Log.d(TAG, "Disconnect timer already running — ignoring additional trigger.");
            return;
        }

        // Build a non-cancellable dialog with a negative button to claim victory immediately.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Opponent disconnected");
        builder.setMessage("Waiting 30s for opponent to reconnect...");
        builder.setCancelable(false);
        builder.setNegativeButton("Claim victory now", (dialog, which) -> {
            cancelOpponentDisconnectTimer();
            onOpponentConfirmedLeft();
        });
        disconnectDialog = builder.create();
        disconnectDialog.show();

        final long startTime = System.currentTimeMillis();

        disconnectRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = OPPONENT_RECONNECT_TIMEOUT_MS - elapsed;
                if (remaining <= 0) {
                    // Timeout expired: opponent didn't reconnect -> finalize as left.
                    Log.d(TAG, "Opponent did not reconnect within timeout. Confirming left.");
                    cancelOpponentDisconnectTimer();
                    if (disconnectDialog != null && disconnectDialog.isShowing()) {
                        disconnectDialog.dismiss();
                    }
                    onOpponentConfirmedLeft();
                } else {
                    // update dialog message with remaining seconds
                    if (disconnectDialog != null && disconnectDialog.isShowing()) {
                        disconnectDialog.setMessage("Waiting " + (remaining / 1000) + "s for opponent to reconnect...");
                    }
                    // schedule next tick after 1s
                    disconnectHandler.postDelayed(this, 1000);
                }
            }
        };

        // Start countdown
        disconnectHandler.post(disconnectRunnable);
    }

    /**
     * Cancel the pending opponent disconnect countdown (called when opponent reconnects).
     */
    private void cancelOpponentDisconnectTimer() {
        if (disconnectRunnable != null) {
            disconnectHandler.removeCallbacks(disconnectRunnable);
            disconnectRunnable = null;
            Log.d(TAG, "Disconnect timer cancelled.");
        }
        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
            disconnectDialog = null;
        }
    }

    /**
     * Called when we have confirmed that the opponent truly left (timeout expired or user claimed victory).
     */
    private void onOpponentConfirmedLeft() {
        if (isGameOver) {
            // If already ended via other means, still ensure cleanup
            Log.d(TAG, "onOpponentConfirmedLeft called but game was already over.");
        } else {
            isGameOver = true; // now finalize the game as over
        }

        if (timer != null) {
            timer.cancel();
        }

        inputField.setEnabled(false);

        // Cancel my own onDisconnect cleanup so my exit doesn't trigger a cascade.
        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        try {
            myPresenceRef.onDisconnect().cancel();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel onDisconnect: " + e.getMessage());
        }

        // Show persistent feedback and end after a short delay.
        Toast.makeText(this, "Opponent did not reconnect. You win by default.", Toast.LENGTH_LONG).show();

        // Perform any existing cleanup logic (similar to your previous code)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            safeRemoveAllListeners();
            finish();
        }, 3000);
    }

    /**
     * When the player completes the paragraph before the timer, they win immediately.
     * This writes the player's final score and writes the result (winnerId/reason).
     * The opponent will see this via setupGameResultListener().
     */
    private void handlePlayerCompletedEarly() {
        if (isGameOver) return;
        isGameOver = true;

        // Stop timer and input
        if (timer != null) timer.cancel();
        inputField.setEnabled(false);

        // Compute final stats
        int myWpm = calculateWPM(inputField.getText().toString());
        int myAccuracy = calculateAccuracy(inputField.getText().toString(), currentParagraph);

        // Write final score for this player
        DatabaseReference finalScoreRef = gameRef.child("final_scores").child(userId);
        finalScoreRef.child("wpm").setValue(myWpm);
        finalScoreRef.child("accuracy").setValue(myAccuracy);

        // Write result so the opponent's client knows what happened
        gameRef.child("result").child("winnerId").setValue(userId);
        gameRef.child("result").child("reason").setValue("completed_paragraph");

        // Remove our onDisconnect because we're purposely ending game now
        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        try {
            myPresenceRef.onDisconnect().cancel();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel onDisconnect: " + e.getMessage());
        }

        // Show victory locally and close activity
        Toast.makeText(this, "You completed the paragraph. You win! 🏆", Toast.LENGTH_LONG).show();
        safeRemoveAllListeners();
        new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 1500);
    }

    private void safeRemoveAllListeners() {
        try {
            if (gameRef != null) {
                if (opponentId != null) {
                    if (opponentListener != null) {
                        gameRef.child("players_progress").child(opponentId).removeEventListener(opponentListener);
                    }
                    if (opponentPresenceListener != null) {
                        gameRef.child("players_progress").child(opponentId).child("is_present").removeEventListener(opponentPresenceListener);
                    }
                }
                if (finalScoreListener != null) {
                    gameRef.child("final_scores").removeEventListener(finalScoreListener);
                }
                if (playersListener != null) {
                    gameRef.removeEventListener(playersListener);
                }
                if (gameResultListener != null) {   // NEW
                    gameRef.child("result").removeEventListener(gameResultListener);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error removing listeners: " + e.getMessage());
        }
    }

    /**
     * Timer end / normal finish handler.
     * This writes our final score and then waits for opponent's score (existing flow).
     */
    private void gameOver() {
        if (isGameOver) return;
        isGameOver = true;

        if (timer != null) {
            timer.cancel();
        }
        inputField.setEnabled(false);

        // Cancel our onDisconnect because game concluded normally - server-side cleanup not needed.
        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        try {
            myPresenceRef.onDisconnect().cancel();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel onDisconnect in gameOver: " + e.getMessage());
        }

        DatabaseReference finalScoreRef = gameRef.child("final_scores").child(userId);
        finalScoreRef.child("wpm").setValue(calculateWPM(inputField.getText().toString()));
        finalScoreRef.child("accuracy").setValue(calculateAccuracy(inputField.getText().toString(), currentParagraph));

        // When timer finishes, we wait for both final_scores children (existing logic).
        finalScoreListener = gameRef.child("final_scores").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // If both players have submitted final_scores (2 children), declare winner based on values
                if (snapshot.getChildrenCount() == 2) {
                    declareWinner(snapshot);
                    gameRef.child("final_scores").removeEventListener(finalScoreListener);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private int calculateWPM(String typedText) {
        long durationMinutes = (System.currentTimeMillis() - gameStartTime) / 60000;
        int wordCount = typedText.trim().isEmpty() ? 0 : typedText.split("\\s+").length;
        if (durationMinutes <= 0) return wordCount;
        return (int) (wordCount / durationMinutes);
    }

    private int calculateAccuracy(String typedText, String originalText) {
        int correctChars = 0;
        int minLength = Math.min(typedText.length(), originalText.length());
        for (int i = 0; i < minLength; i++) {
            if (typedText.charAt(i) == originalText.charAt(i)) {
                correctChars++;
            }
        }
        if (originalText.length() == 0) return 0;
        return (int) ((correctChars / (float) originalText.length()) * 100);
    }

    private void declareWinner(DataSnapshot finalScoresSnapshot) {
        int myWpm = 0;
        int opponentWpm = 0;
        int myAccuracy = 0;
        int opponentAccuracy = 0;

        for (DataSnapshot scoreSnapshot : finalScoresSnapshot.getChildren()) {
            String playerId = scoreSnapshot.getKey();
            Integer wpmValue = scoreSnapshot.child("wpm").getValue(Integer.class);
            Integer accuracyValue = scoreSnapshot.child("accuracy").getValue(Integer.class);

            if (wpmValue == null) wpmValue = 0;
            if (accuracyValue == null) accuracyValue = 0;

            if (playerId.equals(userId)) {
                myWpm = wpmValue;
                myAccuracy = accuracyValue;
            } else {
                opponentWpm = wpmValue;
                opponentAccuracy = accuracyValue;
            }
        }

        String result;
        // Decide based on accuracy when timer ended as per your requirement.
        if (myAccuracy > opponentAccuracy) {
            result = "You won! 🏆";
        } else if (opponentAccuracy > myAccuracy) {
            result = "You lost! 😔";
        } else {
            // If accuracy tied, fallback to WPM
            if (myWpm > opponentWpm) {
                result = "You won! 🏆";
            } else if (opponentWpm > myWpm) {
                result = "You lost! 😔";
            } else {
                result = "It's a draw! 🤝";
            }
        }

        Toast.makeText(this, result + "\nYour WPM: " + myWpm + " Accuracy: " + myAccuracy +
                "\nOpponent's WPM: " + opponentWpm + " Accuracy: " + opponentAccuracy, Toast.LENGTH_LONG).show();

        // Optionally, remove the room after declaring winner
        try {
            gameRef.removeValue();
        } catch (Exception e) {
            Log.w(TAG, "Failed to remove room after declaring winner: " + e.getMessage());
        }

        // End the activity after showing results
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            safeRemoveAllListeners();
            finish();
        }, 4000);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Cancel any pending disconnect timer to avoid leaking UI handlers while in background
        cancelOpponentDisconnectTimer();

        if (gameRef != null) {
            // Remove all specific listeners
            if (opponentId != null) {
                if (opponentListener != null) {
                    gameRef.child("players_progress").child(opponentId).removeEventListener(opponentListener);
                }
                if (opponentPresenceListener != null) {
                    gameRef.child("players_progress").child(opponentId).child("is_present").removeEventListener(opponentPresenceListener);
                }
            }
            if (finalScoreListener != null) {
                gameRef.child("final_scores").removeEventListener(finalScoreListener);
            }
            if (playersListener != null) {
                // This listener should only exist if the game hasn't started yet.
                gameRef.removeEventListener(playersListener);
            }
            if (gameResultListener != null) {
                gameRef.child("result").removeEventListener(gameResultListener);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Ensure we cancel the disconnect timer and dialog
        cancelOpponentDisconnectTimer();

        // IMPORTANT: Do NOT cancel myPresenceRef.onDisconnect() here.
        // Keeping the onDisconnect handler registered ensures that if the process is killed (force-quit),
        // Firebase will run the server-side onDisconnect action and set my 'is_present' to false,
        // so the remaining player(s) will be notified and the disconnect flow will run.
        //
        // We only cancel onDisconnect when the game ends normally (in gameOver() or onOpponentConfirmedLeft()).
    }

    @Override
    public void onBackPressed() {
        // If the game is already over, allow default behavior
        if (isGameOver) {
            super.onBackPressed();
            return;
        }

        // Show confirmation dialog to leave the room (intentional leave)
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Leave Room");
        builder.setMessage("Are you sure you want to leave the room? This will close the room for the match and the opponent will be declared winner.");
        builder.setCancelable(true);
        builder.setPositiveButton("Leave Room", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                handlePlayerLeftIntentional();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * Called when the local user intentionally leaves via the back-confirm dialog.
     * We'll set presence=false, remove the room, and finish.
     */
    private void handlePlayerLeftIntentional() {
        if (isGameOver) {
            safeRemoveAllListeners();
            finish();
            return;
        }
        isGameOver = true;
        cancelOpponentDisconnectTimer();
        if (timer != null) timer.cancel();
        inputField.setEnabled(false);

        // Mark us absent (so opponent sees immediate presence=false)
        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        try {
            myPresenceRef.setValue(false);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set presence=false on intentional leave: " + e.getMessage());
        }

        // Delete the whole room so opponent is notified / closed
        gameRef.removeValue().addOnCompleteListener(task -> {
            safeRemoveAllListeners();
            Toast.makeText(MultiplayerGameActivity.this, "You left the room.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
