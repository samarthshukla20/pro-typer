package com.samarthshukla.protyper;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Setup top toolbar back button
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());

        // --- NEW: Website Row ---
        LinearLayout btnWebsite = findViewById(R.id.btnWebsite);
        btnWebsite.setOnClickListener(v ->
                openUrl("https://pro-typer.vercel.app/aboutus.html"));

        // --- Social Row: Instagram ---
        LinearLayout btnInstagram = findViewById(R.id.btnInstagram);
        btnInstagram.setOnClickListener(v ->
                openUrl("https://www.instagram.com/pro.typer.info?igsh=Z2NjNGcxaXJzemV0"));

        // --- Social Row: Facebook ---
        LinearLayout btnFaceBook = findViewById(R.id.btnFaceBook);
        btnFaceBook.setOnClickListener(v ->
                openUrl("https://www.facebook.com/share/1A2Ar6a1Gp/"));

        // --- Donation Row ---
        LinearLayout btnDonate = findViewById(R.id.btnDonate);
        btnDonate.setOnClickListener(v ->
                openUrl("https://crowdfund.org/c/support-us-by-donating-a-little-share-of-yours/"));
    }

    /**
     * Checks whether a given package is installed on the device.
     */
    private boolean isAppInstalled(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Opens the URL using deep linking to supported apps if available,
     * otherwise opens the link in an in-app browser using Custom Tabs.
     */
    private void openUrl(String url) {
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);

        // Deep link for Instagram
        if (url.contains("instagram.com") && isAppInstalled("com.instagram.android")) {
            intent.setPackage("com.instagram.android");
            try {
                startActivity(intent);
                return;
            } catch (Exception e) {
                // Fallback to in-app browser if something goes wrong.
            }
        }

        // Default: Open in-app browser using Custom Tabs.
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(this, uri);
    }
}