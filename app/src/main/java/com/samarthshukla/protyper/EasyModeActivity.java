package com.samarthshukla.protyper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import androidx.appcompat.app.AlertDialog;

import android.os.Handler;


public class EasyModeActivity extends AppCompatActivity {
    private TextView wordDisplay, timerText, scoreText, tvDateTime;
    private EditText inputField;
    private List<String> words = new ArrayList<>();
    private Random random = new Random();
    private int score = 0;
    private CountDownTimer timer;
    private static final int TIME_LIMIT = 10000; // 10 seconds per word
    private List<String> usedWords;
    private InterstitialAd interstitialAd;
    private long gameStartTime;
    private boolean hasShownRewardDialog = false;
    private boolean isGameOver = false;
    private RewardedAd rewardedAd;
    private long pauseStartTime = 0;
    private long totalPausedDuration = 0;
    private SoundPool soundPool;
    private int soundIdCorrect;
    private int soundIdGameOver;

    // --- XP CACHE VARIABLES ---
    private int cachedTotalXp = 0;
    private int cachedLevel = 1;

    // --- NEW: THE BONUS VAULT ---
    private int currentSessionAdXp = 0;

    private TextView countdownText;
    private View countdownDimBackground;

    private String getCurrentDateTime() {
        String currentDateTime = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault()).format(new Date());
        if (tvDateTime != null) {
            tvDateTime.setText(currentDateTime);
        }
        return currentDateTime;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_easy_mode);

        MobileAds.initialize(this, initializationStatus -> {});
        loadInterstitialAd();
        loadRewardedAd();

        // --- PRE-FETCH XP DATA (Zero UI Delay) ---
        String userId = XpManager.getGlobalUserId(this);
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(userId)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            if (snapshot.child("total_xp").getValue(Integer.class) != null)
                                cachedTotalXp = snapshot.child("total_xp").getValue(Integer.class);
                            if (snapshot.child("level").getValue(Integer.class) != null)
                                cachedLevel = snapshot.child("level").getValue(Integer.class);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                });
        // -----------------------------------------

        wordDisplay = findViewById(R.id.wordDisplay);
        timerText   = findViewById(R.id.timerText);
        scoreText   = findViewById(R.id.scoreText);
        inputField  = findViewById(R.id.inputField);
        tvDateTime  = findViewById(R.id.tvDateTime);
        getCurrentDateTime();

        countdownText = findViewById(R.id.countdownText);
        countdownDimBackground = findViewById(R.id.countdownDimBackground);

        wordDisplay.setTypeface(ResourcesCompat.getFont(this, R.font.difficulty));

        // --- NEW: INSTANT LOAD FROM VAULT ---
        wordDisplay.setText("Loading...");
        inputField.setEnabled(false);
        loadWordsInstantly();

        inputField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                checkWord();
            }
        });

        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setDecorFitsSystemWindows(true);
            final View rootView = findViewById(android.R.id.content);
            final View wordCard = findViewById(R.id.wordCard);
            final View textInputLayout = findViewById(R.id.textInputLayout);

            rootView.setOnApplyWindowInsetsListener((v, insets) -> {
                int imeHeight = insets.getInsets(WindowInsets.Type.ime()).bottom;
                if (imeHeight > 0) {
                    int availableHeight = rootView.getHeight() - imeHeight;
                    int[] inputLocation = new int[2];
                    textInputLayout.getLocationOnScreen(inputLocation);
                    int inputBottom = inputLocation[1] + textInputLayout.getHeight();
                    int overlap = inputBottom - availableHeight;
                    if (overlap < 0) overlap = 0;

                    int[] wordLocation = new int[2];
                    wordCard.getLocationOnScreen(wordLocation);
                    int wordBottom = wordLocation[1] + wordCard.getHeight();
                    int currentGap = inputLocation[1] - wordBottom;

                    int minGap = dpToPx(8);
                    int extraGapReduction = currentGap - minGap;
                    if (extraGapReduction < 0) extraGapReduction = 0;

                    int translationForInput = -overlap;
                    int translationForWord = -Math.min(overlap, extraGapReduction);

                    textInputLayout.animate().translationY(translationForInput).setDuration(100).start();
                    wordCard.animate().translationY(translationForWord).setDuration(100).start();
                } else {
                    textInputLayout.animate().translationY(0).setDuration(100).start();
                    wordCard.animate().translationY(0).setDuration(100).start();
                }
                return insets;
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(audioAttributes)
                    .build();
        } else {
            soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        }

        soundIdCorrect = soundPool.load(this, R.raw.correct_sound, 1);
        soundIdGameOver = soundPool.load(this, R.raw.game_over_sound, 1);
    }

    // ==========================================
    // THE GHOST TYPING COUNTDOWN ENGINE
    // ==========================================
    private void startGhostCountdown() {
        if (countdownDimBackground != null) countdownDimBackground.setVisibility(View.VISIBLE);
        if (countdownText != null) {
            countdownText.setVisibility(View.VISIBLE);
            countdownText.setShadowLayer(20f, 0f, 0f, android.graphics.Color.TRANSPARENT);
        }

        final String[] wordsList = {"THREE", "TWO", "ONE", "GO!!!"};
        final long[] typeSpeeds = {70, 90, 110, 40};
        final long[] pauseAfter = {500, 500, 500, 800};

        final int colorGhost = android.graphics.Color.parseColor("#4A5568");
        final int colorLit = android.graphics.Color.WHITE;
        final int colorGo = android.graphics.Color.parseColor("#39FF14");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordsList.length; i++) {
            sb.append(wordsList[i]);
            if (i < wordsList.length - 1) sb.append("  ");
        }
        final String fullText = sb.toString();

        countdownText.setText(fullText);
        countdownText.setTextColor(colorGhost);

        final android.text.SpannableString spannable = new android.text.SpannableString(fullText);
        final Handler handler = new Handler(android.os.Looper.getMainLooper());

        class GhostTyper implements Runnable {
            int wordIdx = 0;
            int charIdx = 0;
            int globalIdx = 0;

            @Override
            public void run() {
                if (wordIdx >= wordsList.length) {
                    finishCountdown();
                    return;
                }

                String currentWord = wordsList[wordIdx];
                boolean isGoWord = currentWord.equals("GO!!!");

                if (charIdx < currentWord.length()) {
                    int color = isGoWord ? colorGo : colorLit;
                    spannable.setSpan(new android.text.style.ForegroundColorSpan(color), globalIdx, globalIdx + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    countdownText.setText(spannable);

                    if (isGoWord) {
                        countdownText.setShadowLayer(30f, 0f, 0f, colorGo);
                    }

                    // Optional: Play click sound here if you want!

                    charIdx++;
                    globalIdx++;
                    handler.postDelayed(this, typeSpeeds[wordIdx]);
                } else {
                    globalIdx += 2;
                    charIdx = 0;
                    long delay = pauseAfter[wordIdx];
                    wordIdx++;
                    handler.postDelayed(this, delay);
                }
            }
        }
        handler.postDelayed(new GhostTyper(), 500);
    }

    private void finishCountdown() {
        if (countdownText != null) countdownText.setVisibility(View.GONE);
        if (countdownDimBackground != null) countdownDimBackground.setVisibility(View.GONE);

        // 1. Unlock the keyboard
        inputField.setEnabled(true);
        inputField.requestFocus();
        new Handler().postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
        }, 100);

        // 2. NOW officially start the game timers!
        gameStartTime = System.currentTimeMillis();
        totalPausedDuration = 0;
        pauseStartTime = 0;

        startTimer();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void loadWordsInstantly() {
        words.clear();

        // Grab the pre-loaded 26-file data from our vault! Takes < 1 millisecond.
        if (DictionaryManager.getInstance().isLoaded && !DictionaryManager.getInstance().singleWords.isEmpty()) {
            words.addAll(DictionaryManager.getInstance().singleWords);
        } else {
            // Failsafe just in case they clicked play in under 0.1 seconds of opening the app
            words.add("PROTYPER");
            words.add("CHAMPION");
            words.add("KEYBOARD");
        }

        startNewGame();
    }

    private void startNewGame() {
        score = 0;
        currentSessionAdXp = 0; // RESET THE BONUS VAULT FOR A NEW GAME
        scoreText.setText("Score: " + score);
        inputField.setText("");
        inputField.setEnabled(false);
        usedWords = new ArrayList<>();
        generateNewWord();
        gameStartTime = System.currentTimeMillis();
        startGhostCountdown();
    }

    private void generateNewWord() {
        String newWord;
        do {
            newWord = words.get(random.nextInt(words.size()));
        } while (usedWords.contains(newWord));
        usedWords.add(newWord);
        wordDisplay.setText(newWord);
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(TIME_LIMIT, 1000) {
            public void onTick(long millisUntilFinished) {
                timerText.setText("Time left: " + millisUntilFinished / 1000 + "s");
            }
            public void onFinish() {
                gameOver();
            }
        }.start();
    }

    private void checkWord() {
        String typedWord = inputField.getText().toString().trim();
        String displayedWord = wordDisplay.getText().toString().trim();
        if (typedWord.equalsIgnoreCase(displayedWord)) {
            score += 1;
            scoreText.setText("Score: " + score);
            inputField.setText("");
            soundPool.play(soundIdCorrect, 1, 1, 0, 0, 1);
            generateNewWord();
            startTimer();
        }
    }

    private void gameOver() {
        if (isGameOver) return;
        isGameOver = true;

        inputField.setEnabled(false);
        if (soundPool != null) {
            soundPool.play(soundIdGameOver, 1, 1, 0, 0, 1);
        }

        if (!hasShownRewardDialog) {
            hasShownRewardDialog = true;
            showRewardedAdDialog();
        } else {
            saveGameHistory();
            showAdThenGameOver();
        }
    }

    private void showAdThenGameOver() {
        showGameOverCard(score);
    }

    private void showGameOverCard(int score) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View gameOverView = inflater.inflate(R.layout.game_over_card, null);
        TextView gameOverMessage = gameOverView.findViewById(R.id.gameOverMessageInside);
        TextView scoreInsideCard = gameOverView.findViewById(R.id.scoreTextInsideCard);

        // Bind the new Detailed XP Views
        TextView baseXpText = gameOverView.findViewById(R.id.baseXpText);
        TextView adXpText = gameOverView.findViewById(R.id.adXpText);
        TextView xpEarnedText = gameOverView.findViewById(R.id.xpEarnedText);
        android.widget.ProgressBar xpProgressBar = gameOverView.findViewById(R.id.xpProgressBar);
        TextView levelInfoText = gameOverView.findViewById(R.id.levelInfoText);

        MaterialButton retryButton = gameOverView.findViewById(R.id.retryButton);
        MaterialButton menuButton = gameOverView.findViewById(R.id.menuButton);

        // --- NEW: CALCULATE BREAKDOWN XP ---
        int gameplayXp = XpManager.calculateSinglePlayerXp(1, score); // 1 = Easy, 2 = Medium, 3 = Hard
        int adXp = currentSessionAdXp;
        final int totalEarnedXp = gameplayXp + adXp;

        gameOverMessage.setText("Time's Up!");
        scoreInsideCard.setText("Score: " + score);

        // --- NEW: POPULATE THE UI RECEIPT ---
        if (baseXpText != null) {
            baseXpText.setText("Base XP: +" + gameplayXp);
        }
        if (adXp > 0 && adXpText != null) {
            adXpText.setText("Ad Vault: +" + adXp);
            adXpText.setVisibility(View.VISIBLE); // Only unhide if they watched an ad!
        }
        if (xpEarnedText != null) {
            xpEarnedText.setText("Total: +" + totalEarnedXp + " XP");
        }

        // Fetch the Guaranteed Global Player ID
        String userId = XpManager.getGlobalUserId(this);

        // --- NEW: CALCULATE WPM AND SAVE TO STATS MANAGER ---
        long now = System.currentTimeMillis();
        int durationPlayedInSeconds = (int) ((now - gameStartTime - totalPausedDuration) / 1000);
        if (durationPlayedInSeconds <= 0) durationPlayedInSeconds = 1; // Prevent divide by zero crash

        // Calculate WPM: (Total Words / Seconds Played) * 60 seconds
        int finalWpm = (int) (((float) score / durationPlayedInSeconds) * 60);

        // Fire it off to Firebase (Solo Mode protects Win Rate!)
        StatsManager.saveSoloStats(userId, finalWpm);
        // ----------------------------------------------------

        // INSTANT ANIMATION (No Network Wait!)
        int previousLevelXp = (cachedLevel > 1) ? XpManager.getXpRequiredForNextLevel(cachedLevel - 1) : 0;
        int nextLevelXp = XpManager.getXpRequiredForNextLevel(cachedLevel);

        int currentXpInThisLevel = cachedTotalXp - previousLevelXp;

        final int maxXpForThisLevel = nextLevelXp - previousLevelXp;
        final int newXpInThisLevel = currentXpInThisLevel + totalEarnedXp;

        xpProgressBar.setMax(maxXpForThisLevel);
        levelInfoText.setText("Level " + cachedLevel + " (" + currentXpInThisLevel + " / " + maxXpForThisLevel + ")");

        android.animation.ObjectAnimator animation = android.animation.ObjectAnimator.ofInt(xpProgressBar, "progress", currentXpInThisLevel, newXpInThisLevel);
        animation.setDuration(1200);

        animation.addUpdateListener(anim -> {
            int animatedValue = (int) anim.getAnimatedValue();
            if (animatedValue >= maxXpForThisLevel) {
                levelInfoText.setText("Level UP!");
                levelInfoText.setTextColor(android.graphics.Color.parseColor("#FFD700"));
            } else {
                levelInfoText.setText("Level " + cachedLevel + " (" + animatedValue + " / " + maxXpForThisLevel + ")");
            }
        });

        // Save to Firebase AND update local cache after animation finishes
        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                super.onAnimationEnd(animation);

                // SAVE THE COMBINED XP TO FIREBASE
                XpManager.saveXpToFirebase(userId, totalEarnedXp);

                // Update local cache so if they click "Retry", the next match's math is correct
                cachedTotalXp += totalEarnedXp;
                while (cachedTotalXp >= XpManager.getXpRequiredForNextLevel(cachedLevel)) {
                    cachedLevel++;
                }
            }
        });

        animation.start();

        retryButton.setOnClickListener(v -> {
            removeGameOverView(gameOverView);
            isGameOver = false;
            hasShownRewardDialog = false;
            startNewGame();
        });

        menuButton.setOnClickListener(v -> openMenu());
        ViewGroup rootView = findViewById(android.R.id.content);
        rootView.addView(gameOverView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void saveGameHistory() {
        long now = System.currentTimeMillis();
        int durationPlayed = (int) ((now - gameStartTime - totalPausedDuration) / 1000);
        String dateTime = getCurrentDateTime();
        HistoryManager.addHistory(this, new GameHistory("Easy", durationPlayed, score, dateTime, -1));
    }

    private void removeGameOverView(View gameOverView) {
        ViewGroup parent = (ViewGroup) gameOverView.getParent();
        if (parent != null) {
            parent.removeView(gameOverView);
        }
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, getString(R.string.Interstitial), adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                    }
                    @Override
                    public void onAdFailedToLoad(LoadAdError adError) {
                        interstitialAd = null;
                    }
                });
    }

    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, getString(R.string.Rewarded), adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewardedAd = ad;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                rewardedAd = null;
                Log.e("AdMob", "Failed to load Ad: " + adError.getMessage());
            }
        });
    }

    private void showRewardedAdDialog() {
        final AlertDialog dialog;
        final AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rewarded_single, null);

        MaterialButton watchAdButton = dialogView.findViewById(R.id.btnWatchAd);
        MaterialButton cancelButton = dialogView.findViewById(R.id.btnCancel);

        builder.setView(dialogView);
        dialog = builder.create();
        dialog.setCancelable(false);

        pauseStartTime = System.currentTimeMillis();

        final Handler handler = new Handler();
        final int[] secondsLeft = {500000000};

        builder.setTitle("Continue Game?");

        final Runnable[] countdownRunnable = new Runnable[1];
        countdownRunnable[0] = new Runnable() {
            @Override
            public void run() {
                secondsLeft[0]--;
                if (secondsLeft[0] > 0) {
                    handler.postDelayed(this, 1000);
                } else {
                    dialog.dismiss();
                    totalPausedDuration += System.currentTimeMillis() - pauseStartTime;
                    saveGameHistory();
                    showAdThenGameOver();
                }
            }
        };
        handler.postDelayed(countdownRunnable[0], 1000);

        watchAdButton.setOnClickListener(v -> {
            handler.removeCallbacks(countdownRunnable[0]);
            dialog.dismiss();
            showRewardedAd();
        });

        cancelButton.setOnClickListener(v -> {
            handler.removeCallbacks(countdownRunnable[0]);
            dialog.dismiss();
            totalPausedDuration += System.currentTimeMillis() - pauseStartTime;
            saveGameHistory();
            showAdThenGameOver();
        });

        dialog.getWindow().setDimAmount(0.9f);
        dialog.show();
    }


    private void showRewardedAd() {
        if (rewardedAd != null) {
            pauseStartTime = System.currentTimeMillis();

            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    rewardedAd = null;
                    loadRewardedAd();
                    resumeGame();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                    rewardedAd = null;
                    showAdThenGameOver();
                }
            });

            rewardedAd.show(this, rewardItem -> {
                // --- NEW: STORE BONUS XP IN VAULT INSTEAD OF SAVING IMMEDIATELY ---
                int adXp = XpManager.getAdBonusXp();
                currentSessionAdXp += adXp;
                Toast.makeText(EasyModeActivity.this, "+" + adXp + " Bonus XP Locked In!", Toast.LENGTH_SHORT).show();
            });

        } else {
            Toast.makeText(this, "Ad is still loading or unavailable. Check connection.", Toast.LENGTH_SHORT).show();
            showAdThenGameOver();
        }
    }

    private void resumeGame() {
        totalPausedDuration += System.currentTimeMillis() - pauseStartTime;

        isGameOver = false;
        inputField.setEnabled(true);
        inputField.setText("");
        inputField.requestFocus();

        startTimer();
    }

    @Override
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exit Game?")
                .setMessage("Do you want to go back to the menu or continue playing?")
                .setPositiveButton("Go to Menu", (dialog, which) -> finish())
                .setNegativeButton("Continue Playing", (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static void saveGameHistory(Context context, String mode, int duration, int score) {
        SharedPreferences preferences = context.getSharedPreferences("GameHistory", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        String currentDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        String existingHistory = preferences.getString("history", "");
        String newEntry = mode + ", " + duration + "s, " + score + " points, " + currentDateTime + "\n";
        editor.putString("history", existingHistory + newEntry);
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    private void openMenu() {
        finish();
    }
}