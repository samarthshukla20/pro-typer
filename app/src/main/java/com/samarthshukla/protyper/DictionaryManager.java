package com.samarthshukla.protyper;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class DictionaryManager {
    private static DictionaryManager instance;

    // The two vaults
    public List<String> paragraphs = new ArrayList<>();
    public List<String> singleWords = new ArrayList<>();

    public boolean isLoaded = false;

    public static DictionaryManager getInstance() {
        if (instance == null) {
            instance = new DictionaryManager();
        }
        return instance;
    }

    public void loadDataAsync(Context context) {
        if (isLoaded) return;

        new Thread(() -> {
            try {
                // 1. LOAD PARAGRAPHS
                InputStream isPara = context.getAssets().open("pg.txt");
                BufferedReader readerPara = new BufferedReader(new InputStreamReader(isPara));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = readerPara.readLine()) != null) {
                    builder.append(line).append("\n");
                }
                readerPara.close();

                String[] parts = builder.toString().split("(?m)^\\s*\\d+\\.\\s*");
                for (String part : parts) {
                    if (!part.trim().isEmpty()) {
                        paragraphs.add(part.trim());
                    }
                }

                // 2. LOAD SINGLE WORDS (A to Z)
                for (char letter = 'A'; letter <= 'Z'; letter++) {
                    String fileName = letter + " Words.txt";
                    try {
                        InputStream isWord = context.getAssets().open(fileName);
                        BufferedReader readerWord = new BufferedReader(new InputStreamReader(isWord));
                        String word;
                        while ((word = readerWord.readLine()) != null) {
                            if (!word.trim().isEmpty()) {
                                singleWords.add(word.trim());
                            }
                        }
                        readerWord.close();
                    } catch (Exception e) {
                        // In case a specific letter file is missing, it skips it without crashing
                        e.printStackTrace();
                    }
                }

                // Everything is securely in memory!
                isLoaded = true;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}