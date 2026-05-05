package com.samarthshukla.protyper;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
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

    // UI Elements
    private TextView statusTextView;
    private TextView roomCodeDisplayTextView;
    private MaterialButton cancelMatchButton;
    private MaterialButton findMatchButton;
    private MaterialButton rulesButton;
    private MaterialButton createRoomButton;
    private MaterialButton joinRoomButton;
    private MaterialButton playWithFriendButton;
    private EditText roomCodeInput;

    private BottomSheetBehavior<View> bottomSheetBehavior;

    // State trackers for cancellation & Matchmaking
    private boolean findingMatch = false;
    private String finalGameSessionIdForHost = null;
    private String finalOpponentIdForHost = null;
    private String currentOperation = "NONE";
    private String activeRoomCode = null;

    // Listeners so we can detach them if user cancels
    private ValueEventListener gameStartListener;
    private ValueEventListener hostRoomListener;
    private ValueEventListener joinRoomListener;

    // Animated "..." on status text
    private Handler statusHandler = new Handler();
    private Runnable statusDotsRunnable;
    private boolean animateStatusDots = false;

    // --- RESTORED: BOT MATCHMAKING TIMERS ---
    private Handler botHandler = new Handler();
    private Runnable botRunnable;
    private final int BOT_TIMEOUT_MS = 5000; // Triggers after 5 seconds of waiting

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_lobby);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Bind existing views
        statusTextView = findViewById(R.id.statusTextView);
        findMatchButton = findViewById(R.id.findMatchButton);
        rulesButton = findViewById(R.id.rulesButton);

        // Bind new Private Room views
        createRoomButton = findViewById(R.id.createRoomButton);
        joinRoomButton = findViewById(R.id.joinRoomButton);
        roomCodeInput = findViewById(R.id.roomCodeInput);

        // Bind Cancellation & Display Views
        roomCodeDisplayTextView = findViewById(R.id.roomCodeDisplayTextView);
        cancelMatchButton = findViewById(R.id.cancelMatchButton);

        // --- BOTTOM SHEET BINDINGS ---
        playWithFriendButton = findViewById(R.id.playWithFriendButton);
        View bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        playWithFriendButton.setOnClickListener(v -> {
            if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        // Setup Cancel Button
        cancelMatchButton.setOnClickListener(v -> cancelOperation());

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
                    statusTextView.setText("READY TO FIND A MATCH");

                    // Enable Matchmaking
                    findMatchButton.setVisibility(View.VISIBLE);
                    findMatchButton.setEnabled(true);
                    findMatchButton.setOnClickListener(v -> findMatch());

                    // Enable Private Rooms
                    createRoomButton.setOnClickListener(v -> createPrivateRoom());
                    joinRoomButton.setOnClickListener(v -> joinPrivateRoom(roomCodeInput.getText().toString().trim().toUpperCase()));

                } else {
                    Toast.makeText(MultiplayerLobbyActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                    statusTextView.setText("Authentication failed. Please restart.");
                }
            }
        });
    }

    // ==========================================
    // CANCELLATION LOGIC
    // ==========================================

    private void cancelOperation() {
        stopStatusDotsAnimation();
        stopBotTimer(); // Stop the bot countdown if we cancel!

        if ("RANDOM".equals(currentOperation)) {
            findingMatch = false;
            if (userId != null) {
                mDatabase.child("matchmaking_queue").child(userId).removeValue();
                if (gameStartListener != null) {
                    mDatabase.child("matchmaking_queue").child(userId).removeEventListener(gameStartListener);
                }
            }
        }
        else if ("HOST".equals(currentOperation)) {
            if (activeRoomCode != null) {
                if (hostRoomListener != null) {
                    mDatabase.child("private_rooms").child(activeRoomCode).removeEventListener(hostRoomListener);
                }
                mDatabase.child("private_rooms").child(activeRoomCode).removeValue();
            }
        }
        else if ("JOIN".equals(currentOperation)) {
            if (activeRoomCode != null && joinRoomListener != null) {
                mDatabase.child("private_rooms").child(activeRoomCode).removeEventListener(joinRoomListener);
            }
        }

        // Reset UI State
        currentOperation = "NONE";
        activeRoomCode = null;
        finalGameSessionIdForHost = null;
        finalOpponentIdForHost = null;
        roomCodeDisplayTextView.setVisibility(View.GONE);
        cancelMatchButton.setVisibility(View.GONE);
        statusTextView.setText("READY TO FIND A MATCH");
        enableAllButtons();
    }

    // ==========================================
    // RANDOM MATCHMAKING LOGIC (WITH GHOST FILTER)
    // ==========================================

    private void findMatch() {
        if (findingMatch) return;
        findingMatch = true;
        currentOperation = "RANDOM";

        disableAllButtons();
        cancelMatchButton.setVisibility(View.VISIBLE);
        statusTextView.setText("Finding a match");
        startStatusDotsAnimation();

        DatabaseReference matchmakingRef = mDatabase.child("matchmaking_queue");
        final String myUserId = userId;

        finalGameSessionIdForHost = null;
        finalOpponentIdForHost = null;

        matchmakingRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String opponentId = null;

                // --- NEW: GHOST QUEUE EXPLOIT FIX ---
                long currentTime = System.currentTimeMillis();
                long STALE_THRESHOLD_MS = 15000; // 15 Seconds
                List<String> ghostsToRemove = new ArrayList<>();

                for (MutableData player : currentData.getChildren()) {
                    if (!player.getKey().equals(userId)) {
                        if (player.child("gameSessionId").getValue() == null) {

                            Long timestamp = player.child("timestamp").getValue(Long.class);

                            // If they have no timestamp, or it's older than 15s, they are a Ghost.
                            if (timestamp == null || (currentTime - timestamp > STALE_THRESHOLD_MS)) {
                                ghostsToRemove.add(player.getKey());
                            } else {
                                opponentId = player.getKey();
                                break; // Found a valid, alive opponent!
                            }
                        }
                    }
                }

                // Delete all the ghosts we found from the database queue
                for (String ghostId : ghostsToRemove) {
                    currentData.child(ghostId).setValue(null);
                }
                // ------------------------------------

                if (opponentId != null) {
                    String gameSessionId = UUID.randomUUID().toString();
                    finalGameSessionIdForHost = gameSessionId;
                    finalOpponentIdForHost = opponentId;

                    currentData.child(opponentId).child("gameSessionId").setValue(gameSessionId);
                    currentData.child(myUserId).setValue(null);
                    return Transaction.success(currentData);
                } else {
                    Map<String, Object> playerEntry = new HashMap<>();
                    playerEntry.put("userId", userId);
                    playerEntry.put("timestamp", ServerValue.TIMESTAMP); // Stamp the current time
                    currentData.child(userId).setValue(playerEntry);
                    return Transaction.success(currentData);
                }
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (!"RANDOM".equals(currentOperation)) return;
                findingMatch = false;

                if (error != null || !committed || currentData == null) {
                    cancelOperation();
                    Toast.makeText(MultiplayerLobbyActivity.this, "Matchmaking failed.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (finalGameSessionIdForHost != null && finalOpponentIdForHost != null) {
                    // WE FOUND A REAL PLAYER IMMEDIATELY
                    stopBotTimer();

                    String gameSessionIdToCreate = finalGameSessionIdForHost;
                    String opponentIdToInclude = finalOpponentIdForHost;

                    finalGameSessionIdForHost = null;
                    finalOpponentIdForHost = null;

                    String randomParagraph = getRandomParagraphFromAssets();

                    Map<String, Object> gameData = new HashMap<>();
                    gameData.put("status", "in_progress");
                    gameData.put("paragraph_text", randomParagraph);
                    gameData.put("isBotMatch", false); // Real player

                    Map<String, String> playersMap = new HashMap<>();
                    playersMap.put(myUserId, "player1");
                    playersMap.put(opponentIdToInclude, "player2");
                    gameData.put("players", playersMap);

                    mDatabase.child("game_sessions").child(gameSessionIdToCreate)
                            .setValue(gameData)
                            .addOnCompleteListener(task -> {
                                stopStatusDotsAnimation();
                                cancelMatchButton.setVisibility(View.GONE);
                                statusTextView.setText("Arena found! Starting...");
                                startGameActivity(gameSessionIdToCreate, false); // Random Match = False
                            });
                    return;
                }

                // NOBODY FOUND YET -> JOIN QUEUE AND WAIT FOR A HUMAN
                DataSnapshot myDataSnapshot = currentData.child(userId);
                String finalGameSessionId = myDataSnapshot.child("gameSessionId").getValue(String.class);

                if (finalGameSessionId != null && !finalGameSessionId.isEmpty()) {
                    // Someone grabbed us while we were executing
                    stopBotTimer();
                    stopStatusDotsAnimation();
                    cancelMatchButton.setVisibility(View.GONE);
                    statusTextView.setText("Arena found! Preparing...");
                    new Handler().postDelayed(() -> startGameActivity(finalGameSessionId, false), 1500);
                } else if (currentData.child(userId).exists()) {
                    // Waiting in queue... start the bot countdown!
                    startBotTimer();

                    statusTextView.setText("Waiting for opponent");
                    startStatusDotsAnimation();
                    listenForGameStart();
                } else {
                    listenForGameStart();
                }
            }
        });
    }

    private void listenForGameStart() {
        if (gameStartListener != null && userId != null) {
            mDatabase.child("matchmaking_queue").child(userId).removeEventListener(gameStartListener);
        }

        DatabaseReference myQueueRef = mDatabase.child("matchmaking_queue").child(userId);
        gameStartListener = myQueueRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String gameSessionId = snapshot.child("gameSessionId").getValue(String.class);
                if (gameSessionId != null && !gameSessionId.isEmpty()) {
                    stopBotTimer(); // A real player found us! Cancel the bot!
                    myQueueRef.removeEventListener(this);

                    stopStatusDotsAnimation();
                    cancelMatchButton.setVisibility(View.GONE);
                    statusTextView.setText("Arena found! Preparing...");
                    new Handler().postDelayed(() -> startGameActivity(gameSessionId, false), 1500);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if ("RANDOM".equals(currentOperation)) cancelOperation();
            }
        });
    }

    // ==========================================
    // SEAMLESS BOT MATCHMAKING
    // ==========================================

    private void startBotTimer() {
        stopBotTimer(); // Clear any existing timers
        botRunnable = () -> {
            if (!"RANDOM".equals(currentOperation)) return;
            triggerBotMatch();
        };
        botHandler.postDelayed(botRunnable, BOT_TIMEOUT_MS);
    }

    private void stopBotTimer() {
        if (botRunnable != null) {
            botHandler.removeCallbacks(botRunnable);
            botRunnable = null;
        }
    }

    private void triggerBotMatch() {
        // 1. Remove from the real matchmaking queue
        if (userId != null) {
            mDatabase.child("matchmaking_queue").child(userId).removeValue();
            if (gameStartListener != null) {
                mDatabase.child("matchmaking_queue").child(userId).removeEventListener(gameStartListener);
            }
        }

        // 2. Create the Bot Session
        String gameSessionId = UUID.randomUUID().toString();
        String botId = "BOT_" + new Random().nextInt(99999);

        String randomParagraph = getRandomParagraphFromAssets();
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("status", "in_progress");
        gameData.put("paragraph_text", randomParagraph);

        // --- BOT SPECIFIC FLAGS ---
        gameData.put("isBotMatch", true);
        gameData.put("botName", "Guest_" + (1000 + new Random().nextInt(8999)));
        int botTargetWpm = 30 + new Random().nextInt(30); // Randomly 30 - 60 WPM
        gameData.put("botWpm", botTargetWpm);

        Map<String, String> playersMap = new HashMap<>();
        playersMap.put(userId, "player1");
        playersMap.put(botId, "player2");
        gameData.put("players", playersMap);

        // 3. Launch
        mDatabase.child("game_sessions").child(gameSessionId)
                .setValue(gameData)
                .addOnCompleteListener(task -> {
                    stopStatusDotsAnimation();
                    cancelMatchButton.setVisibility(View.GONE);
                    statusTextView.setText("Arena found! Preparing...");
                    // Random Match = False (XP IS GIVEN FOR BOTS)
                    new Handler().postDelayed(() -> startGameActivity(gameSessionId, false), 1500);
                });
    }

    // ==========================================
    // PRIVATE ROOM LOGIC
    // ==========================================

    private void createPrivateRoom() {
        currentOperation = "HOST";
        disableAllButtons();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        activeRoomCode = generateRoomCode();

        roomCodeDisplayTextView.setText(activeRoomCode);
        roomCodeDisplayTextView.setVisibility(View.VISIBLE);
        cancelMatchButton.setVisibility(View.VISIBLE);

        statusTextView.setText("Waiting for friend...");
        startStatusDotsAnimation();

        DatabaseReference roomRef = mDatabase.child("private_rooms").child(activeRoomCode);

        Map<String, Object> roomData = new HashMap<>();
        roomData.put("hostId", userId);
        roomData.put("status", "waiting");
        roomRef.setValue(roomData);

        hostRoomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild("guestId") && "joined".equals(snapshot.child("status").getValue(String.class))) {
                    roomRef.removeEventListener(this);

                    String guestId = snapshot.child("guestId").getValue(String.class);
                    String gameSessionId = UUID.randomUUID().toString();

                    String randomParagraph = getRandomParagraphFromAssets();
                    Map<String, Object> gameData = new HashMap<>();
                    gameData.put("status", "in_progress");
                    gameData.put("paragraph_text", randomParagraph);
                    gameData.put("isBotMatch", false);

                    Map<String, String> playersMap = new HashMap<>();
                    playersMap.put(userId, "player1");
                    playersMap.put(guestId, "player2");
                    gameData.put("players", playersMap);

                    mDatabase.child("game_sessions").child(gameSessionId)
                            .setValue(gameData)
                            .addOnCompleteListener(task -> {
                                roomRef.child("gameSessionId").setValue(gameSessionId);
                                stopStatusDotsAnimation();
                                cancelMatchButton.setVisibility(View.GONE);
                                statusTextView.setText("Match found! Starting...");
                                startGameActivity(gameSessionId, true); // Friend Match = True
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if ("HOST".equals(currentOperation)) cancelOperation();
            }
        };

        roomRef.addValueEventListener(hostRoomListener);
    }

    private void joinPrivateRoom(String roomCode) {
        if (roomCode.isEmpty()) {
            Toast.makeText(this, "Please enter a code", Toast.LENGTH_SHORT).show();
            return;
        }

        currentOperation = "JOIN";
        activeRoomCode = roomCode;

        disableAllButtons();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        cancelMatchButton.setVisibility(View.VISIBLE);

        statusTextView.setText("Joining room");
        startStatusDotsAnimation();

        DatabaseReference roomRef = mDatabase.child("private_rooms").child(activeRoomCode);

        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!"JOIN".equals(currentOperation)) return;

                if (snapshot.exists() && "waiting".equals(snapshot.child("status").getValue(String.class))) {
                    roomRef.child("guestId").setValue(userId);
                    roomRef.child("status").setValue("joined");

                    joinRoomListener = new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snap) {
                            String sessionId = snap.child("gameSessionId").getValue(String.class);
                            if (sessionId != null) {
                                roomRef.removeEventListener(this);
                                stopStatusDotsAnimation();
                                cancelMatchButton.setVisibility(View.GONE);
                                statusTextView.setText("Arena found! Preparing...");
                                new Handler().postDelayed(() -> startGameActivity(sessionId, true), 1500); // Friend Match = True
                            }
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    };
                    roomRef.addValueEventListener(joinRoomListener);

                } else {
                    Toast.makeText(MultiplayerLobbyActivity.this, "Room not found or full.", Toast.LENGTH_SHORT).show();
                    cancelOperation();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if ("JOIN".equals(currentOperation)) cancelOperation();
            }
        });
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return code.toString();
    }

    // ==========================================
    // SHARED GAME LAUNCH & UTILS
    // ==========================================

    private void startGameActivity(String gameSessionId, boolean isFriendMatch) {
        if (gameSessionId == null || gameSessionId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid ID. Try again.", Toast.LENGTH_SHORT).show();
            cancelOperation();
            return;
        }

        if (userId != null) {
            mDatabase.child("matchmaking_queue").child(userId).removeValue();
        }

        Intent intent = new Intent(this, MultiplayerGameActivity.class);
        intent.putExtra("gameSessionId", gameSessionId);
        intent.putExtra("userId", userId);
        intent.putExtra("isFriendMatch", isFriendMatch);

        startActivity(intent);
        finish();
    }

    private void disableAllButtons() {
        findMatchButton.setEnabled(false);
        createRoomButton.setEnabled(false);
        joinRoomButton.setEnabled(false);
        playWithFriendButton.setEnabled(false);
    }

    private void enableAllButtons() {
        findMatchButton.setEnabled(true);
        createRoomButton.setEnabled(true);
        joinRoomButton.setEnabled(true);
        playWithFriendButton.setEnabled(true);
    }

    private String getRandomParagraphFromAssets() {
        // 1. Instantly grab the paragraphs from our global vault!
        if (DictionaryManager.getInstance().isLoaded && !DictionaryManager.getInstance().paragraphs.isEmpty()) {
            List<String> paragraphs = DictionaryManager.getInstance().paragraphs;
            return paragraphs.get(new Random().nextInt(paragraphs.size()));
        }

        // 2. Failsafe: Just in case the background thread hasn't finished loading yet
        return "The quick brown fox jumps over the lazy dog.";
    }

    private void showRulesDialog() {
        String rulesText = "• Both players type the same paragraph for 120 seconds.\n\n" +
                "• First to finish correctly wins immediately.\n\n" +
                "• If time runs out, higher accuracy wins. If tied, higher WPM wins.\n\n" +
                "• Leaving the room gives the win to the other player.\n\n" +
                "• If someone disconnects, the other player can wait or claim victory.\n\n" +
                "• NOTE: XP and Win Rates are only tracked in Random Matchmaking, not Friend Rooms.";

        new AlertDialog.Builder(this)
                .setTitle("Match Rules")
                .setMessage(rulesText)
                .setPositiveButton("Got it", null)
                .show();
    }

    private void startStatusDotsAnimation() {
        animateStatusDots = true;
        if (statusDotsRunnable != null) statusHandler.removeCallbacks(statusDotsRunnable);

        statusDotsRunnable = new Runnable() {
            int dotCount = 0;
            String baseText = statusTextView.getText().toString().replace("...", "");
            @Override
            public void run() {
                if (!animateStatusDots) return;
                StringBuilder sb = new StringBuilder(baseText);
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
        cancelOperation();
    }
}