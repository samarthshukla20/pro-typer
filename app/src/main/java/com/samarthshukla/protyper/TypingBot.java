package com.samarthshukla.protyper;

import android.os.Handler;
import android.os.Looper;
import java.util.Random;

public class TypingBot {

    // --- 1. THE PERSONAS ---
    public enum Persona {
        NOOB("NoobMaster69", 35, 0.08f),      // 35 WPM, 8% chance to make a typo
        NINJA("KeyboardNinja", 65, 0.04f),    // 65 WPM, 4% chance to make a typo
        PRO("ProTyper_AI", 100, 0.01f);       // 100 WPM, 1% chance to make a typo

        public final String name;
        public final int baseWpm;
        public final float mistakeChance;

        Persona(String name, int baseWpm, float mistakeChance) {
            this.name = name;
            this.baseWpm = baseWpm;
            this.mistakeChance = mistakeChance;
        }
    }

    // --- 2. BOT LISTENER INTERFACE ---
    // This allows the bot to talk back to your UI to update its progress bar
    public interface BotListener {
        void onBotProgressUpdate(int charsTyped, String currentText);
        void onBotFinished();
    }

    private Persona persona;
    private String targetText;
    private BotListener listener;

    private int botIndex = 0;
    private int playerIndex = 0; // The bot tracks you for rubber-banding!
    private boolean isRunning = false;

    private Handler botHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    public TypingBot(Persona persona, String targetText, BotListener listener) {
        this.persona = persona;
        this.targetText = targetText;
        this.listener = listener;
    }

    // Let the Activity tell the bot how far along the human player is
    public void updatePlayerProgress(int humanCharsTyped) {
        this.playerIndex = humanCharsTyped;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        botIndex = 0;
        typeNextCharacter();
    }

    public void stop() {
        isRunning = false;
        botHandler.removeCallbacksAndMessages(null);
    }

    public String getName() {
        return persona.name;
    }

    // --- 3. THE BRAIN ---
    private void typeNextCharacter() {
        if (!isRunning) return;

        if (botIndex < targetText.length()) {
            // Tell the UI we typed a character
            botIndex++;
            String typedSoFar = targetText.substring(0, botIndex);
            if (listener != null) {
                listener.onBotProgressUpdate(botIndex, typedSoFar);
            }

            // Calculate base typing speed (Standard formula: 1 Word = 5 Characters)
            // CPM = WPM * 5. Milliseconds per char = 60,000 / CPM
            int cpm = persona.baseWpm * 5;
            long baseDelayMs = 60000 / cpm;

            // --- THE RUBBER BAND ALGORITHM ---
            float speedMultiplier = 1.0f;
            int difference = playerIndex - botIndex;

            if (difference > 15) {
                // Player is winning by a lot -> Bot sweats and types 20% faster
                speedMultiplier = 0.8f;
            } else if (difference < -15) {
                // Bot is winning by a lot -> Bot slacks off and types 30% slower
                speedMultiplier = 1.3f;
            }

            long finalDelay = (long) (baseDelayMs * speedMultiplier);

            // --- THE HUMAN ERROR SIMULATOR ---
            // Roll the dice to see if the bot makes a typo
            if (random.nextFloat() <= persona.mistakeChance) {
                // Add a 400ms penalty to simulate hitting backspace and correcting the typo
                finalDelay += 400;
            }

            // Schedule the next keystroke
            botHandler.postDelayed(this::typeNextCharacter, finalDelay);

        } else {
            // Bot reached the end of the text!
            isRunning = false;
            if (listener != null) {
                listener.onBotFinished();
            }
        }
    }
}