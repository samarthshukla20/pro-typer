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
    private ScrollView paragraphScrollView;
    private View paragraphCard;
    private View inputCard;
    private DatabaseReference gameRef;
    private String gameSessionId, userId, opponentId;
    private String currentParagraph = "";

    private CountDownTimer timer;
    private static final int TIME_LIMIT = 120000;
    private long gameStartTime;
    private long matchEndTime = 0;
    private boolean isGameOver = false;
    private boolean isGameStarted = false;

    // --- XP CACHE VARIABLES ---
    private int cachedTotalXp = 0;
    private int cachedLevel = 1;

    // --- ADVANCED AI BOT VARIABLES ---
    private boolean isBotMatch = false;
    private int botWpm = 0;
    private TypingBot myBot; // Our new AI Engine!
    private int botCharsTypedSoFar = 0; // Tracks bot progress for final scoring

    private ValueEventListener opponentListener;
    private ValueEventListener finalScoreListener;
    private ValueEventListener playersListener;
    private ValueEventListener opponentPresenceListener;
    private ValueEventListener gameResultListener;

    private Handler disconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable disconnectRunnable;
    private AlertDialog disconnectDialog;
    private static final int OPPONENT_RECONNECT_TIMEOUT_MS = 30000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_multiplayer_game);

        gameSessionId = getIntent().getStringExtra("gameSessionId");
        userId = getIntent().getStringExtra("userId");

        if (gameSessionId == null || userId == null) {
            Toast.makeText(this, "Failed to start game: Missing data.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // --- PRE-FETCH XP DATA (Zero UI Delay) ---
        String fetchId = userId != null ? userId : XpManager.getGlobalUserId(this);
        FirebaseDatabase.getInstance().getReference("users").child(fetchId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            if (snapshot.child("total_xp").getValue(Integer.class) != null)
                                cachedTotalXp = snapshot.child("total_xp").getValue(Integer.class);
                            if (snapshot.child("level").getValue(Integer.class) != null)
                                cachedLevel = snapshot.child("level").getValue(Integer.class);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
        // -----------------------------------------

        wordDisplay = findViewById(R.id.multiplayerWordDisplay);
        timerText = findViewById(R.id.multiplayerTimerText);
        inputField = findViewById(R.id.multiplayerInputField);
        myProgressBar = findViewById(R.id.myProgressBar);
        opponentProgressBar = findViewById(R.id.opponentProgressBar);
        paragraphScrollView = findViewById(R.id.paragraphScrollView);
        paragraphCard = findViewById(R.id.paragraphCard);
        inputCard = findViewById(R.id.inputCard);

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
                int availableHeight = screenHeight - keypadHeight;
                int[] inputLocation = new int[2];
                inputCard.getLocationOnScreen(inputLocation);
                int inputBottom = inputLocation[1] + inputCard.getHeight();
                int overlap = inputBottom - availableHeight;
                if (overlap < 0) overlap = 0;

                inputCard.animate().translationY(-overlap).setDuration(120).start();

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
                inputCard.animate().translationY(0).setDuration(120).start();
                paragraphCard.animate().translationY(0).setDuration(120).start();
            }
        });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void fetchOpponentIdAndParagraph() {
        playersListener = gameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && !isGameStarted) {
                    DataSnapshot playersSnapshot = snapshot.child("players");
                    String paragraphText = snapshot.child("paragraph_text").getValue(String.class);

                    Boolean botFlag = snapshot.child("isBotMatch").getValue(Boolean.class);
                    if (botFlag != null && botFlag) {
                        isBotMatch = true;
                        Integer wpm = snapshot.child("botWpm").getValue(Integer.class);
                        botWpm = (wpm != null) ? wpm : 40;
                    }

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

                    if (tempOpponentId != null && paragraphText != null && !paragraphText.isEmpty()) {
                        opponentId = tempOpponentId;
                        currentParagraph = paragraphText;
                        wordDisplay.setText(currentParagraph);

                        gameRef.removeEventListener(playersListener);
                        startMultiplayerGame();
                    }
                } else if (!snapshot.exists()) {
                    safeRemoveAllListeners();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MultiplayerGameActivity.this, "Failed to load game data.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void startMultiplayerGame() {
        if (isGameStarted) return;
        isGameStarted = true;

        isGameOver = false;
        matchEndTime = 0;
        inputField.setEnabled(true);
        inputField.setText("");
        inputField.requestFocus();
        gameStartTime = System.currentTimeMillis();

        DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
        myPresenceRef.setValue(true);
        myPresenceRef.onDisconnect().setValue(false);

        startTimer();
        setupTypingListener();
        setupGameResultListener();

        if (isBotMatch) {
            startBotSimulation();
        } else if (opponentId != null) {
            setupOpponentListener();
            setupOpponentDisconnectListener();
        }
    }

    private void cancelMyPresenceDisconnect() {
        try {
            DatabaseReference myPresenceRef = gameRef.child("players_progress").child(userId).child("is_present");
            myPresenceRef.onDisconnect().cancel();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel onDisconnect: " + e.getMessage());
        }
    }

    // ==========================================
    // NEW: ADVANCED BOT INTEGRATION
    // ==========================================
    private void startBotSimulation() {
        botCharsTypedSoFar = 0;

        // Assign Persona based on target WPM
        TypingBot.Persona persona;
        if (botWpm >= 80) persona = TypingBot.Persona.PRO;
        else if (botWpm >= 50) persona = TypingBot.Persona.NINJA;
        else persona = TypingBot.Persona.NOOB;

        myBot = new TypingBot(persona, currentParagraph, new TypingBot.BotListener() {
            @Override
            public void onBotProgressUpdate(int charsTyped, String currentText) {
                if (isGameOver) return;
                botCharsTypedSoFar = charsTyped;
                int progress = (int) (((float) charsTyped / currentParagraph.length()) * 100);
                opponentProgressBar.setProgress(progress);
            }

            @Override
            public void onBotFinished() {
                if (isGameOver) return;
                botCharsTypedSoFar = currentParagraph.length();
                opponentProgressBar.setProgress(100);
                handleBotCompletedEarly();
            }
        });

        // Unleash the bot!
        myBot.start();
    }

    private void stopBotSimulation() {
        if (myBot != null) {
            myBot.stop();
        }
    }

    private void handleBotCompletedEarly() {
        if (isGameOver) return;
        isGameOver = true;
        if (matchEndTime == 0) matchEndTime = System.currentTimeMillis();

        cancelMyPresenceDisconnect();
        if (timer != null) timer.cancel();
        inputField.setEnabled(false);

        int myWpm = calculateWPM(inputField.getText().toString());
        int myAccuracy = calculateAccuracy(inputField.getText().toString(), currentParagraph);
        int botAccuracy = 96; // Bots are pretty accurate

        DatabaseReference finalScoreRef = gameRef.child("final_scores").child(userId);
        finalScoreRef.child("wpm").setValue(myWpm);
        finalScoreRef.child("accuracy").setValue(myAccuracy);

        DatabaseReference botScoreRef = gameRef.child("final_scores").child(opponentId);
        botScoreRef.child("wpm").setValue(botWpm);
        botScoreRef.child("accuracy").setValue(botAccuracy);

        gameRef.child("result").child("winnerId").setValue(opponentId);
        gameRef.child("result").child("reason").setValue("completed_paragraph");

        launchResultScreen("lose", "completed_paragraph", myWpm, myAccuracy, botWpm, botAccuracy, opponentId);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { gameRef.removeValue(); } catch (Exception ignored) {}
            safeRemoveAllListeners();
        }, 1500);
    }
    // ==========================================

    private void startTimer() {
        timer = new CountDownTimer(TIME_LIMIT, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                timerText.setText("Time left: " + secondsRemaining + "s");
            }
            public void onFinish() {
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

                // --- NEW: TELL THE BOT OUR PROGRESS FOR RUBBER-BANDING ---
                if (isBotMatch && myBot != null) {
                    myBot.updatePlayerProgress(typedText.length());
                }
                // ---------------------------------------------------------

                if (typedText.length() == currentParagraph.length() && typedText.equals(currentParagraph)) {
                    if (matchEndTime == 0) matchEndTime = System.currentTimeMillis();
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
        if (isGameOver) return;
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
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupGameResultListener() {
        if (gameResultListener != null) {
            gameRef.child("result").removeEventListener(gameResultListener);
        }

        gameResultListener = gameRef.child("result").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String winnerId = snapshot.child("winnerId").getValue(String.class);
                String reason = snapshot.child("reason").getValue(String.class);

                if (winnerId == null || winnerId.isEmpty()) return;
                if (isGameOver) return;

                isGameOver = true;
                if (matchEndTime == 0) matchEndTime = System.currentTimeMillis();

                cancelMyPresenceDisconnect();
                stopBotSimulation();

                if (timer != null) timer.cancel();
                inputField.setEnabled(false);
                cancelOpponentDisconnectTimer();

                String resultType;
                if ("draw".equals(winnerId)) {
                    resultType = "draw";
                } else if (winnerId.equals(userId)) {
                    resultType = "win";
                } else {
                    resultType = "lose";
                }

                int myWpm = calculateWPM(inputField.getText().toString());
                int myAcc = calculateAccuracy(inputField.getText().toString(), currentParagraph);

                launchResultScreen(resultType, reason, myWpm, myAcc, 0, 0, winnerId);
                safeRemoveAllListeners();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { gameRef.removeValue(); } catch (Exception ignored) {}
                }, 2000);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupOpponentDisconnectListener() {
        DatabaseReference opponentPresenceRef = gameRef.child("players_progress").child(opponentId).child("is_present");
        opponentPresenceListener = opponentPresenceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isPresent = snapshot.getValue(Boolean.class);
                if (isPresent != null && isPresent) {
                    cancelOpponentDisconnectTimer();
                } else {
                    onPossibleOpponentDisconnect();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void onPossibleOpponentDisconnect() {
        if (isGameOver) return;
        if (disconnectRunnable != null) return;

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

        final long startTimeDelay = System.currentTimeMillis();
        disconnectRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTimeDelay;
                long remaining = OPPONENT_RECONNECT_TIMEOUT_MS - elapsed;
                if (remaining <= 0) {
                    cancelOpponentDisconnectTimer();
                    if (disconnectDialog != null && disconnectDialog.isShowing()) disconnectDialog.dismiss();
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
        }
        if (disconnectDialog != null && disconnectDialog.isShowing()) {
            disconnectDialog.dismiss();
            disconnectDialog = null;
        }
    }

    private void onOpponentConfirmedLeft() {
        if (!isGameOver) isGameOver = true;
        if (matchEndTime == 0) matchEndTime = System.currentTimeMillis();

        cancelMyPresenceDisconnect();
        stopBotSimulation();
        if (timer != null) timer.cancel();
        inputField.setEnabled(false);

        int myWpm = calculateWPM(inputField.getText().toString());
        int myAcc = calculateAccuracy(inputField.getText().toString(), currentParagraph);

        launchResultScreen("win", "opponent_disconnected", myWpm, myAcc, 0, 0, userId);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { gameRef.removeValue(); } catch (Exception ignored) {}
            safeRemoveAllListeners();
        }, 1200);
    }

    private void handlePlayerCompletedEarly() {
        if (isGameOver) return;
        isGameOver = true;
        if (matchEndTime == 0) matchEndTime = System.currentTimeMillis();

        cancelMyPresenceDisconnect();
        stopBotSimulation();
        if (timer != null) timer.cancel();
        inputField.setEnabled(false);

        int myWpm = calculateWPM(inputField.getText().toString());
        int myAccuracy = calculateAccuracy(inputField.getText().toString(), currentParagraph);

        DatabaseReference finalScoreRef = gameRef.child("final_scores").child(userId);
        finalScoreRef.child("wpm").setValue(myWpm);
        finalScoreRef.child("accuracy").setValue(myAccuracy);

        if (isBotMatch) {
            // Calculate effective bot WPM based on where it was when the player finished
            int effectiveBotWpm = (int) ((botCharsTypedSoFar / 5f) / ((System.currentTimeMillis() - gameStartTime) / 60000f));
            if (effectiveBotWpm > botWpm) effectiveBotWpm = botWpm;

            DatabaseReference botScoreRef = gameRef.child("final_scores").child(opponentId);
            botScoreRef.child("wpm").setValue(effectiveBotWpm);
            botScoreRef.child("accuracy").setValue(96);
        }

        gameRef.child("result").child("winnerId").setValue(userId);
        gameRef.child("result").child("reason").setValue("completed_paragraph");

        launchResultScreen("win", "completed_paragraph", myWpm, myAccuracy, 0, 0, userId);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { gameRef.removeValue(); } catch (Exception ignored) {}
            safeRemoveAllListeners();
        }, 1500);
    }

    private void gameOver() {
        if (isGameOver) return;
        isGameOver = true;
        if (matchEndTime == 0) matchEndTime = System.currentTimeMillis();

        cancelMyPresenceDisconnect();
        stopBotSimulation();
        if (timer != null) timer.cancel();
        inputField.setEnabled(false);

        DatabaseReference finalScoreRef = gameRef.child("final_scores").child(userId);
        finalScoreRef.child("wpm").setValue(calculateWPM(inputField.getText().toString()));
        finalScoreRef.child("accuracy").setValue(calculateAccuracy(inputField.getText().toString(), currentParagraph));

        if (isBotMatch) {
            int effectiveBotWpm = (int) ((botCharsTypedSoFar / 5f) / 2f);
            DatabaseReference botScoreRef = gameRef.child("final_scores").child(opponentId);
            botScoreRef.child("wpm").setValue(effectiveBotWpm);
            botScoreRef.child("accuracy").setValue(96);
        }

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
        String originalText = currentParagraph != null ? currentParagraph : "";
        int correctChars = 0;
        int minLen = Math.min(typedText.length(), originalText.length());
        for (int i = 0; i < minLen; i++) {
            if (typedText.charAt(i) == originalText.charAt(i)) correctChars++;
        }

        long finalEndTime = (matchEndTime > 0) ? matchEndTime : System.currentTimeMillis();
        long activeMillis = finalEndTime - gameStartTime;
        if (activeMillis <= 0) activeMillis = 1000;

        double minutes = activeMillis / 60000.0;
        double standardWords = correctChars / 5.0;

        return (int) Math.round(standardWords / minutes);
    }

    private int calculateAccuracy(String typedText, String originalText) {
        if (typedText == null || originalText == null) return 0;
        if (typedText.isEmpty()) return 0;

        int correctChars = 0;
        int minLength = Math.min(typedText.length(), originalText.length());
        for (int i = 0; i < minLength; i++) {
            if (typedText.charAt(i) == originalText.charAt(i)) correctChars++;
        }
        return (int) (((float) correctChars / typedText.length()) * 100);
    }

    private void declareWinner(DataSnapshot finalScoresSnapshot) {
        int myWpm = 0, opponentWpm = 0;
        int myAccuracy = 0, opponentAccuracy = 0;

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
            if (myWpm > opponentWpm) resultType = "win";
            else if (opponentWpm > myWpm) resultType = "lose";
            else resultType = "draw";
        }

        try {
            if ("win".equals(resultType)) gameRef.child("result").child("winnerId").setValue(userId);
            else if ("lose".equals(resultType)) gameRef.child("result").child("winnerId").setValue(opponentId);
            else gameRef.child("result").child("winnerId").setValue("draw");

            gameRef.child("result").child("reason").setValue("timer_accuracy");
        } catch (Exception ignored) {}

        launchResultScreen(resultType, "timer_accuracy", myWpm, myAccuracy, opponentWpm, opponentAccuracy,
                "win".equals(resultType) ? userId : ("lose".equals(resultType) ? opponentId : null));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { gameRef.removeValue(); } catch (Exception ignored) {}
            safeRemoveAllListeners();
        }, 1500);
    }

    private void safeRemoveAllListeners() {
        stopBotSimulation();
        try {
            if (gameRef != null) {
                if (opponentId != null) {
                    if (opponentListener != null) gameRef.child("players_progress").child(opponentId).removeEventListener(opponentListener);
                    if (opponentPresenceListener != null) gameRef.child("players_progress").child(opponentId).child("is_present").removeEventListener(opponentPresenceListener);
                }
                if (finalScoreListener != null) gameRef.child("final_scores").removeEventListener(finalScoreListener);
                if (playersListener != null) gameRef.removeEventListener(playersListener);
                if (gameResultListener != null) gameRef.child("result").removeEventListener(gameResultListener);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelOpponentDisconnectTimer();
        stopBotSimulation();
        safeRemoveAllListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelOpponentDisconnectTimer();
        stopBotSimulation();
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

        cancelMyPresenceDisconnect();
        cancelOpponentDisconnectTimer();
        stopBotSimulation();

        if (timer != null) timer.cancel();
        inputField.setEnabled(false);

        try { gameRef.child("players_progress").child(userId).child("is_present").setValue(false); } catch (Exception ignored) {}

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

    private void launchResultScreen(String resultType, String reason, int myWpm, int myAcc, int oppWpm, int oppAcc, String winnerId) {
        try {
            Intent intent = new Intent(this, ResultActivity.class);
            intent.putExtra(ResultActivity.EXTRA_RESULT_TYPE, resultType);
            intent.putExtra(ResultActivity.EXTRA_MY_WPM, myWpm);
            intent.putExtra(ResultActivity.EXTRA_MY_ACC, myAcc);
            intent.putExtra(ResultActivity.EXTRA_OPP_WPM, oppWpm);
            intent.putExtra(ResultActivity.EXTRA_OPP_ACC, oppAcc);
            intent.putExtra(ResultActivity.EXTRA_WINNER_ID, winnerId);
            intent.putExtra(ResultActivity.EXTRA_YOUR_ID, userId);

            intent.putExtra("cachedTotalXp", cachedTotalXp);
            intent.putExtra("cachedLevel", cachedLevel);

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