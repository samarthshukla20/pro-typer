package com.samarthshukla.protyper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.airbnb.lottie.LottieAnimationView;
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

import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class ParagraphActivity extends AppCompatActivity {

    private TextView wordDisplay, timerText, scoreText, tvDateTime;
    private EditText inputField;
    private ScrollView paragraphScrollView;

    private List<String> words = new ArrayList<>();
    private Random random = new Random();
    private int accuracy = 0;
    private CountDownTimer timer;
    private static final int TIME_LIMIT = 120000; // 120s
    private List<String> usedWords;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private long gameStartTime;
    private long startTime;
    private String currentParagraph = "";
    private boolean isGameOver = false;
    private boolean isParagraphFullyTyped = false;
    private boolean hasShownRewardDialog = false;
    private boolean historySaved = false;
    private long totalPausedDuration = 0;
    private long pauseStartTime = 0;
    private View gameOverCardView;
    private SoundPool soundPool;
    private int soundIdParaComplete;
    private int soundIdGameOver;

    // --- XP CACHE VARIABLES ---
    private int cachedTotalXp = 0;
    private int cachedLevel = 1;
    private int currentSessionAdXp = 0;
    private long paragraphEndTime = 0;

    private TextView countdownText;
    private View countdownDimBackground;

    private String getCurrentDateTime() {
        String currentDateTime = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
                .format(new Date());
        if (tvDateTime != null) {
            tvDateTime.setText(currentDateTime);
        }
        return currentDateTime;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_paragraph_mode);

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
        timerText = findViewById(R.id.timerText);
        scoreText = findViewById(R.id.scoreText);
        inputField = findViewById(R.id.inputField);
        tvDateTime = findViewById(R.id.tvDateTime);
        paragraphScrollView = findViewById(R.id.paragraphScrollView);
        countdownText = findViewById(R.id.countdownText);
        countdownDimBackground = findViewById(R.id.countdownDimBackground);

        startTime = System.currentTimeMillis();
        getCurrentDateTime();

        wordDisplay.setTypeface(ResourcesCompat.getFont(this, R.font.difficulty));

        // INSTANT LOAD - No fake loading screens, no disabled inputs!
        loadWordsInstantly();

        inputField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { checkWord(); }
        });

        KeyboardVisibilityEvent.setEventListener(this, isOpen -> {
            View wordCard = findViewById(R.id.wordCard);
            View textInputLayout = findViewById(R.id.textInputLayout);
            View rootView = findViewById(android.R.id.content);
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            if (isOpen && keypadHeight > screenHeight * 0.15) {
                int availableHeight = screenHeight - keypadHeight;
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
        });

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

        soundIdParaComplete = soundPool.load(this, R.raw.para_complete_sound, 1);
        soundIdGameOver = soundPool.load(this, R.raw.game_over_sound, 1);
    }

    // ==========================================
    // THE GHOST TYPING COUNTDOWN ENGINE
    // ==========================================
    private void startGhostCountdown() {
        if (countdownDimBackground != null) countdownDimBackground.setVisibility(View.VISIBLE);
        if (countdownText != null) {
            countdownText.setVisibility(View.VISIBLE);
            countdownText.setShadowLayer(20f, 0f, 0f, Color.TRANSPARENT);
        }

        final String[] wordsList = {"THREE", "TWO", "ONE", "GO!!!"};
        final long[] typeSpeeds = {70, 90, 110, 40};
        final long[] pauseAfter = {500, 500, 500, 800};

        final int colorGhost = Color.parseColor("#4A5568");
        final int colorLit = Color.WHITE;
        final int colorGo = Color.parseColor("#39FF14");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordsList.length; i++) {
            sb.append(wordsList[i]);
            if (i < wordsList.length - 1) sb.append("  ");
        }
        final String fullText = sb.toString();

        countdownText.setText(fullText);
        countdownText.setTextColor(colorGhost);

        final SpannableString spannable = new SpannableString(fullText);
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
                    spannable.setSpan(new ForegroundColorSpan(color), globalIdx, globalIdx + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    countdownText.setText(spannable);

                    if (isGoWord) {
                        countdownText.setShadowLayer(30f, 0f, 0f, colorGo);
                    }

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

        // Unlock the keyboard
        inputField.setEnabled(true);
        inputField.requestFocus();
        new Handler().postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
        }, 100);

        // NOW officially start the game timers!
        gameStartTime = System.currentTimeMillis();
        startTime = gameStartTime;
        if (this instanceof ParagraphActivity) {
            paragraphEndTime = 0;
            totalPausedDuration = 0;
            pauseStartTime = 0;
        }
        startTimer();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void loadWordsInstantly() {
        words.clear();

        // Grab the pre-loaded data from our vault!
        if (DictionaryManager.getInstance().isLoaded && !DictionaryManager.getInstance().paragraphs.isEmpty()) {
            words.addAll(DictionaryManager.getInstance().paragraphs);
        } else {
            // Failsafe: If they clicked so fast the background thread hasn't finished yet
            words.add("The quick brown fox jumps over the lazy dog.");
        }

        startNewGame();
    }

    private void startNewGame() {
        isGameOver = false;
        accuracy = 0;
        currentSessionAdXp = 0; // RESET VAULT
        scoreText.setText("Accuracy: " + calculateAccuracy() + "%");
        inputField.setText("");
        inputField.setEnabled(false);
        usedWords = new ArrayList<>();
        generateNewWord();

        //gameStartTime = System.currentTimeMillis();
        startTime = gameStartTime;
        paragraphEndTime = 0;
        totalPausedDuration = 0;
        pauseStartTime = 0;

        //startTimer();
        hasShownRewardDialog = false;
        historySaved = false;
        isParagraphFullyTyped = false;

        if (paragraphScrollView != null) {
            paragraphScrollView.scrollTo(0, 0);
        }

        startGhostCountdown();

    }

    private void generateNewWord() {
        String newWord;
        do {
            newWord = words.get(random.nextInt(words.size()));
        } while (usedWords.contains(newWord));
        usedWords.add(newWord);
        wordDisplay.setText(newWord);
        currentParagraph = newWord;

        if (paragraphScrollView != null) {
            paragraphScrollView.post(() -> paragraphScrollView.scrollTo(0, 0));
        }
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(TIME_LIMIT, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                timerText.setText("Time left: " + secondsRemaining + "s");
            }
            public void onFinish() { gameOver(); }
        }.start();
    }

    private void checkWord() {
        String typedText = inputField.getText().toString();
        String paragraphText = wordDisplay.getText().toString();

        SpannableStringBuilder spannable = new SpannableStringBuilder();
        int minLength = Math.min(typedText.length(), paragraphText.length());
        int dullGreen = Color.parseColor("#228B22");
        for (int i = 0; i < minLength; i++) {
            char typedChar = typedText.charAt(i);
            char correctChar = paragraphText.charAt(i);
            SpannableString spanChar = new SpannableString(String.valueOf(correctChar));
            if (typedChar == correctChar) {
                spanChar.setSpan(new ForegroundColorSpan(dullGreen), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                spanChar.setSpan(new ForegroundColorSpan(Color.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            spannable.append(spanChar);
        }
        if (typedText.length() < paragraphText.length()) {
            spannable.append(paragraphText.substring(typedText.length()));
            isParagraphFullyTyped = false;
        } else if (typedText.equals(paragraphText)) {
            isParagraphFullyTyped = true;
        } else {
            isParagraphFullyTyped = false;
        }
        wordDisplay.setText(spannable);

        autoScrollParagraph(typedText.length());

        if (typedText.length() == paragraphText.length() && isParagraphFullyTyped) {
            paragraphEndTime = System.currentTimeMillis(); // <-- INSTANTLY LOCKS THE CLOCK!
            inputField.setEnabled(false);
            if (timer != null) timer.cancel();
            accuracy = 100;
            showConfettiThenGameOver(accuracy);
        } else {
            scoreText.setText("Accuracy: " + calculateAccuracy() + "%");
        }
    }

    private void autoScrollParagraph(int typedLength) {
        if (paragraphScrollView == null || wordDisplay == null) return;
        wordDisplay.post(() -> {
            Layout layout = wordDisplay.getLayout();
            if (layout == null) return;
            int textLength = wordDisplay.getText().length();
            if (textLength == 0) return;
            int offset = Math.max(0, Math.min(typedLength, textLength - 1));
            int line = layout.getLineForOffset(offset);
            int lineTop = layout.getLineTop(line);
            int targetScrollY = lineTop - dpToPx(24);
            if (targetScrollY < 0) targetScrollY = 0;
            paragraphScrollView.smoothScrollTo(0, targetScrollY);
        });
    }

    private void showConfettiThenGameOver(final int accuracy) {
        final ViewGroup rootView = findViewById(android.R.id.content);
        final View dimBg = new View(this);
        dimBg.setBackgroundColor(0x00000000);
        dimBg.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        dimBg.setClickable(false);
        dimBg.setFocusable(false);
        rootView.addView(dimBg);

        final LottieAnimationView confetti = new LottieAnimationView(this);
        confetti.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        confetti.setScaleType(LottieAnimationView.ScaleType.CENTER_INSIDE);
        confetti.setAnimation("confetti.json");
        confetti.setRepeatCount(0);
        confetti.setSpeed(1f);
        rootView.addView(confetti);

        if (soundPool != null) {
            soundPool.play(soundIdParaComplete, 1, 1, 0, 0, 1);
        }

        confetti.playAnimation();
        confetti.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                rootView.removeView(confetti);
                rootView.removeView(dimBg);

                if (isParagraphFullyTyped && !isGameOver) {
                    showCongratsCard(accuracy);
                } else {
                    showGameOverCard(accuracy);
                }
            }
        });
    }

    private int getCalculatedWpm() {
        String typedText = inputField.getText().toString();
        String paragraphText = currentParagraph;

        if (paragraphText == null) paragraphText = "";

        int correctChars = 0;
        int minLen = Math.min(typedText.length(), paragraphText.length());
        for (int i = 0; i < minLen; i++) {
            if (typedText.charAt(i) == paragraphText.charAt(i)) {
                correctChars++;
            }
        }

        long finalEndTime = (paragraphEndTime > 0) ? paragraphEndTime : System.currentTimeMillis();
        long activeMillis = (finalEndTime - gameStartTime) - totalPausedDuration;

        if (activeMillis <= 0) activeMillis = 1000;

        double minutes = activeMillis / 60000.0;
        double standardWords = correctChars / 5.0;

        return (int) Math.round(standardWords / minutes);
    }

    private int calculateAccuracy() {
        String typedText = inputField.getText().toString();
        String paragraphText = currentParagraph;

        if (typedText.isEmpty()) return 0;

        int correctChars = 0;
        int minLen = Math.min(typedText.length(), paragraphText.length());

        for (int i = 0; i < minLen; i++) {
            if (typedText.charAt(i) == paragraphText.charAt(i)) {
                correctChars++;
            }
        }
        return (int) (((float) correctChars / typedText.length()) * 100);
    }

    private void animateXpAndSave(View cardView, int wpm, int finalAccuracy) {
        TextView baseXpText = cardView.findViewById(R.id.baseXpText);
        TextView completionXpText = cardView.findViewById(R.id.completionXpText);
        TextView speedXpText = cardView.findViewById(R.id.speedXpText);
        TextView adXpText = cardView.findViewById(R.id.adXpText);
        TextView xpEarnedText = cardView.findViewById(R.id.xpEarnedText);
        android.widget.ProgressBar xpProgressBar = cardView.findViewById(R.id.xpProgressBar);
        TextView levelInfoText = cardView.findViewById(R.id.levelInfoText);

        if (xpEarnedText == null || xpProgressBar == null) return;

        int baseXp = (int) ((wpm * (float) finalAccuracy) / 100f);
        int completionXp = isParagraphFullyTyped ? 20 : 0;
        int speedXp = 0;

        if (finalAccuracy > 80) {
            if (wpm >= 91) speedXp = 50;
            else if (wpm >= 61) speedXp = 45;
            else if (wpm >= 31) speedXp = 30;
            else speedXp = 15;
        }

        int adXp = currentSessionAdXp;
        final int totalEarnedXp = baseXp + completionXp + speedXp + adXp;

        if (baseXpText != null) {
            baseXpText.setText("Base XP: +" + baseXp);
        }
        if (completionXp > 0 && completionXpText != null) {
            completionXpText.setText("Completion: +" + completionXp);
            completionXpText.setVisibility(View.VISIBLE);
        }
        if (speedXp > 0 && speedXpText != null) {
            speedXpText.setText("Speed Bonus: +" + speedXp);
            speedXpText.setVisibility(View.VISIBLE);
        }
        if (adXp > 0 && adXpText != null) {
            adXpText.setText("Ad Vault: +" + adXp);
            adXpText.setVisibility(View.VISIBLE);
        }

        xpEarnedText.setText("Total: +" + totalEarnedXp + " XP");

        String userId = XpManager.getGlobalUserId(this);

        StatsManager.saveSoloStats(userId, wpm);

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
                levelInfoText.setTextColor(Color.parseColor("#FFD700"));
            } else {
                levelInfoText.setText("Level " + cachedLevel + " (" + animatedValue + " / " + maxXpForThisLevel + ")");
            }
        });

        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                XpManager.saveXpToFirebase(userId, totalEarnedXp);

                cachedTotalXp += totalEarnedXp;
                while (cachedTotalXp >= XpManager.getXpRequiredForNextLevel(cachedLevel)) {
                    cachedLevel++;
                }
            }
        });

        animation.start();
    }

    private void showCongratsCard(int accuracy) {
        saveGameHistoryOnce();

        LayoutInflater inflater = LayoutInflater.from(this);
        gameOverCardView = inflater.inflate(R.layout.congrats_card_para, null);

        TextView title = gameOverCardView.findViewById(R.id.congratsTitle);
        TextView message = gameOverCardView.findViewById(R.id.congratsMessageInside);
        TextView accuracyView = gameOverCardView.findViewById(R.id.congratsAccuracyText);
        TextView wpmTextView = gameOverCardView.findViewById(R.id.congratsWpmText);

        title.setText("🎉 Congratulations!");
        message.setText("You completed the paragraph!");
        accuracyView.setText("Accuracy: " + accuracy + "%");

        int wpm = getCalculatedWpm();
        wpmTextView.setText("WPM: " + wpm);

        animateXpAndSave(gameOverCardView, wpm, accuracy);

        MaterialButton nextButton = gameOverCardView.findViewById(R.id.nextButton);
        MaterialButton menuButton = gameOverCardView.findViewById(R.id.menuButton);

        nextButton.setOnClickListener(v -> {
            if (gameOverCardView != null && gameOverCardView.getParent() != null) {
                ((ViewGroup) gameOverCardView.getParent()).removeView(gameOverCardView);
            }

            inputField.setEnabled(true);
            inputField.setText("");
            inputField.requestFocus();
            new Handler().postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 300);

            isGameOver = false;
            hasShownRewardDialog = false;
            historySaved = false;
            startNewGame();
        });

        menuButton.setOnClickListener(v -> openMenu());

        ViewGroup rootView = findViewById(android.R.id.content);
        rootView.addView(gameOverCardView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showGameOverCard(int accuracy) {
        saveGameHistoryOnce();

        LayoutInflater inflater = LayoutInflater.from(this);
        gameOverCardView = inflater.inflate(R.layout.game_over_card_para, null);

        TextView gameOverMessage = gameOverCardView.findViewById(R.id.gameOverMessageInside);
        gameOverMessage.setText("Game Over!");

        TextView accuracyInsideCard = gameOverCardView.findViewById(R.id.scoreTextInsideCard);
        accuracyInsideCard.setText("Accuracy: " + accuracy + "%");

        TextView wpmTextView = gameOverCardView.findViewById(R.id.wpmTextView);
        int wpm = getCalculatedWpm();

        if (wpmTextView != null) {
            wpmTextView.setText("WPM: " + wpm);
        }

        animateXpAndSave(gameOverCardView, wpm, accuracy);

        MaterialButton retryButton = gameOverCardView.findViewById(R.id.retryButton);
        MaterialButton menuButton = gameOverCardView.findViewById(R.id.menuButton);

        retryButton.setOnClickListener(v -> {
            if (gameOverCardView != null && gameOverCardView.getParent() != null)
                ((ViewGroup) gameOverCardView.getParent()).removeView(gameOverCardView);

            inputField.setEnabled(true);
            inputField.setText("");
            inputField.requestFocus();
            new Handler().postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 300);

            isGameOver = false;
            hasShownRewardDialog = false;
            historySaved = false;
            startNewGame();
        });

        menuButton.setOnClickListener(v -> openMenu());

        ViewGroup rootView = findViewById(android.R.id.content);
        rootView.addView(gameOverCardView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void saveGameHistoryOnce() {
        if (!historySaved) {
            accuracy = calculateAccuracy();
            long rawDuration = System.currentTimeMillis() - gameStartTime;
            long actualDuration = rawDuration - totalPausedDuration;
            int durationPlayed = (int) (actualDuration / 1000);
            String dateTime = getCurrentDateTime();
            HistoryManager.addHistory(this, new GameHistory("Paragraph Mode", durationPlayed, 0, dateTime, accuracy));
            historySaved = true;
        }
    }

    private void openMenu() {
        finish();
    }

    private void gameOver() {
        if (isGameOver) return;
        isGameOver = true;
        inputField.setEnabled(false);
        if (soundPool != null) {
            soundPool.play(soundIdGameOver, 1, 1, 0, 0, 1);
        }
        accuracy = calculateAccuracy();
        if (!isParagraphFullyTyped && !hasShownRewardDialog) {
            hasShownRewardDialog = true;
            showRewardedAdDialog();
        } else {
            showAdThenGameOver();
        }
    }

    private void showAdThenGameOver() {
        showGameOverCard(accuracy);
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, getString(R.string.Interstitial), adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                    }
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                        interstitialAd = null;
                    }
                });
    }

    private void showRewardedAdDialog() {
        final AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rewarded_ad, null);
        TextView message = dialogView.findViewById(R.id.rewardMessage);
        MaterialButton watchAdButton = dialogView.findViewById(R.id.btnWatchAd);
        MaterialButton cancelButton = dialogView.findViewById(R.id.btnCancel);

        builder.setView(dialogView);
        builder.setCancelable(false);
        final AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        pauseStartTime = System.currentTimeMillis();

        dialog.getWindow().setDimAmount(0.9f);
        dialog.show();

        final int[] secondsLeft = {500000000};
        dialog.setTitle("Add +15 sec? (" + secondsLeft[0] + "s)");

        final Handler handler = new Handler();
        final Runnable countdownRunnable = new Runnable() {
            @Override
            public void run() {
                secondsLeft[0]--;
                dialog.setTitle("Add +15 sec? (" + secondsLeft[0] + "s)");
                if (secondsLeft[0] > 0) {
                    handler.postDelayed(this, 1000);
                } else {
                    dialog.dismiss();
                    totalPausedDuration += System.currentTimeMillis() - pauseStartTime;
                    showGameOverCard(accuracy);
                }
            }
        };
        handler.postDelayed(countdownRunnable, 1000);

        watchAdButton.setOnClickListener(v -> {
            handler.removeCallbacks(countdownRunnable);
            dialog.dismiss();
            showRewardedAd();
        });

        cancelButton.setOnClickListener(v -> {
            handler.removeCallbacks(countdownRunnable);
            dialog.dismiss();
            totalPausedDuration += System.currentTimeMillis() - pauseStartTime;
            showAdThenGameOver();
        });
    }

    private void resumeGameWith15Seconds() {
        inputField.setEnabled(true);
        isGameOver = false;
        totalPausedDuration += System.currentTimeMillis() - pauseStartTime;
        pauseStartTime = 0;
        inputField.requestFocus();
        new Handler().postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);

        if (timer != null) timer.cancel();
        timer = new CountDownTimer(15000, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                timerText.setText(String.format(Locale.getDefault(), "Extra Time: %d sec", secondsRemaining));
            }
            public void onFinish() { gameOver(); }
        }.start();
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
            }
        });
    }

    private void showRewardedAd() {
        if (rewardedAd != null) {
            pauseStartTime = System.currentTimeMillis();
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    rewardedAd = null;
                    loadRewardedAd();
                    resumeGameWith15Seconds();
                }
                @Override
                public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                    rewardedAd = null;
                    showAdThenGameOver();
                }
            });
            rewardedAd.show(this, rewardItem -> {
                int adXp = XpManager.getAdBonusXp();
                currentSessionAdXp += adXp;
                Toast.makeText(ParagraphActivity.this, "+15s & +" + adXp + " Bonus XP Locked In!", Toast.LENGTH_SHORT).show();
            });
        } else {
            Toast.makeText(this, "Ad is still loading or unavailable. Check connection.", Toast.LENGTH_SHORT).show();
        }
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
}