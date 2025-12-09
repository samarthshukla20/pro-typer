package com.samarthshukla.protyper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT_TYPE = "extra_result_type"; // "win"|"lose"|"draw"
    public static final String EXTRA_MY_WPM = "extra_my_wpm";
    public static final String EXTRA_MY_ACC = "extra_my_acc";
    public static final String EXTRA_OPP_WPM = "extra_opp_wpm";   // still accepted but ignored
    public static final String EXTRA_OPP_ACC = "extra_opp_acc";   // still accepted but ignored
    public static final String EXTRA_WINNER_ID = "extra_winner_id";
    public static final String EXTRA_YOUR_ID = "extra_your_id";

    public static final int RESULT_REMATCH = 101; // kept in case you ever use it

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        TextView titleTv = findViewById(R.id.gameOverTitle);
        TextView messageTv = findViewById(R.id.gameOverMessageInside);
        TextView myAccuracyTv = findViewById(R.id.scoreTextInsideCard);
        TextView myWpmTv = findViewById(R.id.wpmTextView);
        Button menuBtn = findViewById(R.id.menuButton);
        Button retryBtn = findViewById(R.id.retryButton);

        Intent i = getIntent();
        String resultType = i.getStringExtra(EXTRA_RESULT_TYPE);
        int myWpm = i.getIntExtra(EXTRA_MY_WPM, 0);
        int myAcc = i.getIntExtra(EXTRA_MY_ACC, 0);
        // opponent stats & reason ignored

        // Title + subtitle based on result
        if ("win".equals(resultType)) {
            titleTv.setText("You Win! 🏆");
            messageTv.setText("Great job — you finished first!");
        } else if ("lose".equals(resultType)) {
            titleTv.setText("You Lost 😔");
            messageTv.setText("Opponent did better this time.");
        } else if ("draw".equals(resultType)) {
            titleTv.setText("Draw 🤝");
            messageTv.setText("Both of you were evenly matched.");
        } else {
            titleTv.setText("Game Over");
            messageTv.setText("Match finished.");
        }

        // Your stats
        myAccuracyTv.setText("Accuracy: " + myAcc + "%");
        myWpmTv.setText("WPM: " + myWpm);

        // Menu: just close and go back to previous screen
        menuBtn.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });

        // Retry: open MultiplayerLobbyActivity
        retryBtn.setOnClickListener(v -> {
            Intent lobbyIntent = new Intent(ResultActivity.this, MultiplayerLobbyActivity.class);
            startActivity(lobbyIntent);
            // Optionally finish so user can’t come back to this result screen via back
            finish();
        });
    }
}