package com.samarthshukla.protyper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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

import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;

public class MultiplayerGameActivity extends AppCompatActivity {

    private static final String TAG = "MultiplayerGameActivity";

    private TextView wordDisplay, timerText;
    private EditText inputField;
    private ProgressBar myProgressBar, opponentProgressBar;
    private ScrollView paragraphScrollView; // for auto-scroll
    private View paragraphCard;            // NEW: for keyboard shifting
    private View inputCard;                // NEW: for keyboard shifting
    private DatabaseReference gameRef;
    private String gameSessionId, userId, opponentId;
    private String currentParagraph = "";

    private CountDownTimer timer;
    private static final int TIME_LIMIT = 120000;
    private long gameStartTime;
    private boolean isGameOver = false;
    private boolean isGameStarted = false;

    private ValueEventListener opponentListener;
    private ValueEventListener finalScoreListener;
    private ValueEventListener playersListener;
    private ValueEventListener opponentPresenceListener;
    private ValueEventListener gameResultListener;   // listens for winnerId / reason

    // New fields for debounce / reconnect window
    private Handler disconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable disconnectRunnable;
    private AlertDialog disconnectDialog;
    private static final int OPPONENT_RECONNECT_TIMEOUT_MS = 30000; // 30s

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ Make layout resize when keyboard appears
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

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
        paragraphScrollView = findViewById(R.id.paragraphScrollView);
        paragraphCard = findViewById(R.id.paragraphCard); // root card for paragraph
        inputCard = findViewById(R.id.inputCard);         // input card

        // ✅ Keyboard handling like ParagraphActivity, but for multiplayer views
        setupKeyboardShiftBehavior();

        gameRef = FirebaseDatabase.getInstance().getReference("game_sessions").child(gameSessionId);

        fetchOpponentIdAndParagraph();
    }

    private void setupKeyboardShiftBehavior() {
        KeyboardVisibilityEvent.setEventListener(this, isOpen -> {
            View rootView = findViewById(android.R.id.content);
            if (rootView == null || inputCard == null || paragraphCard == null) return;

            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (isOpen && keypadHeight > screenHeight * 0.15f) {
                // Height available above the keyboard
                int availableHeight = screenHeight - keypadHeight;

                int[] inputLocation = new int[2];
                inputCard.getLocationOnScreen(inputLocation);
                int inputBottom = inputLocation[1] + inputCard.getHeight();

                int overlap = inputBottom - availableHeight;
                if (overlap < 0) overlap = 0;

                // Shift input card fully above keyboard
                inputCard.animate().translationY(-overlap).setDuration(120).start();

                // Slightly move paragraph card up so gap between paragraph and input stays nice
                int[] paragraphLocation = new int[2];
                paragraphCard.getLocationOnScreen(paragraphLocation);
                int paragraphBottom = paragraphLocation[1] + paragraphCard.getHeight();
                int currentGap = inputLocation[1] - paragraphBottom;
                int minGap = dpToPx(8);
                int extraGapReduction = currentGap - minGap;
                if (extraGapReduction < 0) extraGapReduction = 0;
                int paragraphTranslation = -Math.min(overlap, extraGapReduction);

                paragraphCard.animate().translationY(paragraphTranslation).setDuration(120).start();
            } else {
                // Keyboard hidden → reset positions
                inputCard.animate().translationY(0).setDuration(120).start();
                paragraphCard.animate().translationY(0).setDuration(120).start();
            }
        });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
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
        setupGameResultListener();

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

        // auto-scroll to keep current typing line visible
        if (paragraphScrollView != null) {
            final int cursorOffset = Math.min(typedText.length(), currentParagraph.length());
            paragraphScrollView.post(() -> {
                Layout layout = wordDisplay.getLayout();
                if (layout != null && cursorOffset >= 0 && cursorOffset <= layout.getText().length()) {
                    int line = layout.getLineForOffset(cursorOffset);
                    int y = layout.getLineTop(line);
                    paragraphScrollView.smoothScrollTo(0, y);
                }
            });
        }
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
     * Listen for explicit game result so the losing side sees proper lose message
     * instead of the opponent-disconnected dialog.
     *
     * We compute my WPM & accuracy locally from inputField/currentParagraph,
     * so the losing side no longer sees 0/0.
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

                // Decide my result type
                String resultType;
                if ("draw".equals(winnerId)) {
                    resultType = "draw";
                } else if (winnerId.equals(userId)) {
                    resultType = "win";
                } else {
                    resultType = "lose";
                }

                // Compute my stats locally
                String typed = inputField.getText().toString();
                int myWpm = calculateWPM(typed);
                int myAcc = calculateAccuracy(typed, currentParagraph);

                // Opponent stats: not used on UI anymore, pass 0,0
                launchResultScreen(resultType,
                        reason,
                        myWpm, myAcc,
                        0, 0,
                        winnerId);

                safeRemoveAllListeners();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "gameResultListener cancelled: " + error.getMessage());
            }
        });
    }

    private void setupOpponentDisconnectListener() {
        DatabaseReference opponentPresenceRef = gameRef.child("players_progress").child(opponentId).child("is_present");
        opponentPresenceListener = opponentPresenceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isPresent = snapshot.getValue(Boolean.class);

                if (isPresent != null && isPresent) {
                    Log.d(TAG, "Opponent presence: ONLINE -> cancel disconnect timer if any.");
                    cancelOpponentDisconnectTimer();
                } else {
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

    private void onPossibleOpponentDisconnect() {
        if (isGameOver) return; // already finished

        if (disconnectRunnable != null) {
            Log.d(TAG, "Disconnect timer already running — ignoring additional trigger.");
            return;
        }

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
                    Log.d(TAG, "Opponent did not reconnect within timeout. Confirming left.");
                    cancelOpponentDisconnectTimer();
                    if (disconnectDialog != null && disconnectDialog.isShowing()) {
                        disconnectDialog.dismiss();
                    }
                    onOpponentConfirmedLeft();
                } else {
                    if (disconnectDialog != null && disconnectDialog.isShowing()) {
                        disconnectDialog.setMessage("Waiting " + (remaining / 1000) + "s for opponent to reconnect...");
                    }
                    disconnectHandler.postDelayed(this, 1000);
                }
            }
        };

        disconnectHandler.post(disconnectRunnable);
    }

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

    private void onOpponentConfirmedLeft() {
        if (isGameOver) {
            Log.d(TAG, "onOpponentConfirmedLeft called but game was already over.");
        } else {
            isGameOver = true;
        }

        if (timer != null) {
            timer.cancel();
        }

        inputField.setEnabled(false);

        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        try {
            myPresenceRef.onDisconnect().cancel();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel onDisconnect: " + e.getMessage());
        }

        int myWpm = calculateWPM(inputField.getText().toString());
        int myAcc = calculateAccuracy(inputField.getText().toString(), currentParagraph);

        launchResultScreen("win", "opponent_disconnected", myWpm, myAcc, 0, 0, userId);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                gameRef.removeValue();
            } catch (Exception ignored) {}
            safeRemoveAllListeners();
        }, 1200);
    }

    private void handlePlayerCompletedEarly() {
        if (isGameOver) return;
        isGameOver = true;

        if (timer != null) timer.cancel();
        inputField.setEnabled(false);

        int myWpm = calculateWPM(inputField.getText().toString());
        int myAccuracy = calculateAccuracy(inputField.getText().toString(), currentParagraph);

        DatabaseReference finalScoreRef = gameRef.child("final_scores").child(userId);
        finalScoreRef.child("wpm").setValue(myWpm);
        finalScoreRef.child("accuracy").setValue(myAccuracy);

        gameRef.child("result").child("winnerId").setValue(userId);
        gameRef.child("result").child("reason").setValue("completed_paragraph");

        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        try {
            myPresenceRef.onDisconnect().cancel();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel onDisconnect: " + e.getMessage());
        }

        launchResultScreen("win", "completed_paragraph", myWpm, myAccuracy, 0, 0, userId);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { gameRef.removeValue(); } catch (Exception ignored) {}
            safeRemoveAllListeners();
        }, 1500);
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
                if (gameResultListener != null) {
                    gameRef.child("result").removeEventListener(gameResultListener);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error removing listeners: " + e.getMessage());
        }
    }

    private void gameOver() {
        if (isGameOver) return;
        isGameOver = true;

        if (timer != null) {
            timer.cancel();
        }
        inputField.setEnabled(false);

        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        try {
            myPresenceRef.onDisconnect().cancel();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel onDisconnect in gameOver: " + e.getMessage());
        }

        DatabaseReference finalScoreRef = gameRef.child("final_scores").child(userId);
        finalScoreRef.child("wpm").setValue(calculateWPM(inputField.getText().toString()));
        finalScoreRef.child("accuracy").setValue(calculateAccuracy(inputField.getText().toString(), currentParagraph));

        finalScoreListener = gameRef.child("final_scores").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
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

        String resultType;
        if (myAccuracy > opponentAccuracy) {
            resultType = "win";
        } else if (opponentAccuracy > myAccuracy) {
            resultType = "lose";
        } else {
            if (myWpm > opponentWpm) {
                resultType = "win";
            } else if (opponentWpm > myWpm) {
                resultType = "lose";
            } else {
                resultType = "draw";
            }
        }

        try {
            if ("win".equals(resultType)) {
                gameRef.child("result").child("winnerId").setValue(userId);
            } else if ("lose".equals(resultType)) {
                gameRef.child("result").child("winnerId").setValue(opponentId);
            } else {
                gameRef.child("result").child("winnerId").setValue("draw");
            }
            gameRef.child("result").child("reason").setValue("timer_accuracy");
        } catch (Exception e) {
            Log.w(TAG, "Failed to write result node: " + e.getMessage());
        }

        launchResultScreen(resultType, "timer_accuracy", myWpm, myAccuracy, opponentWpm, opponentAccuracy,
                "win".equals(resultType) ? userId : ("lose".equals(resultType) ? opponentId : null));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { gameRef.removeValue(); } catch (Exception ignored) {}
            safeRemoveAllListeners();
        }, 1500);
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelOpponentDisconnectTimer();

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
            if (gameResultListener != null) {
                gameRef.child("result").removeEventListener(gameResultListener);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelOpponentDisconnectTimer();
        // Do NOT cancel onDisconnect here
    }

    @Override
    public void onBackPressed() {
        if (isGameOver) {
            super.onBackPressed();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Leave Room");
        builder.setMessage("Are you sure you want to leave the room? This will close the room for the match and the opponent will be declared winner.");
        builder.setCancelable(true);
        builder.setPositiveButton("Leave Room", (dialog, which) -> handlePlayerLeftIntentional());
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

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

        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        try {
            myPresenceRef.setValue(false);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set presence=false on intentional leave: " + e.getMessage());
        }

        try {
            if (opponentId != null && !opponentId.isEmpty()) {
                gameRef.child("result").child("winnerId").setValue(opponentId);
                gameRef.child("result").child("reason").setValue("left_room");
            }
        } catch (Exception ignored) {}

        gameRef.removeValue().addOnCompleteListener(task -> {
            safeRemoveAllListeners();
            finish();
        });
    }

    private void launchResultScreen(String resultType, String reason,
                                    int myWpm, int myAcc, int oppWpm, int oppAcc,
                                    String winnerId) {
        try {
            Intent intent = new Intent(this, ResultActivity.class);
            intent.putExtra(ResultActivity.EXTRA_RESULT_TYPE, resultType);
            intent.putExtra(ResultActivity.EXTRA_MY_WPM, myWpm);
            intent.putExtra(ResultActivity.EXTRA_MY_ACC, myAcc);
            intent.putExtra(ResultActivity.EXTRA_OPP_WPM, oppWpm);
            intent.putExtra(ResultActivity.EXTRA_OPP_ACC, oppAcc);
            intent.putExtra(ResultActivity.EXTRA_WINNER_ID, winnerId);
            intent.putExtra(ResultActivity.EXTRA_YOUR_ID, userId);

            startActivityForResult(intent, 1000);
        } catch (Exception e) {
            Toast.makeText(this, (resultType.equals("win") ? "You won!" : "You lost!"), Toast.LENGTH_LONG).show();
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1200);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1000) {
            safeRemoveAllListeners();
            finish();
        }
    }
}
