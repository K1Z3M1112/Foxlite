package com.winlator.cmod;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * In-app log viewer for the whole app process (not just LSFG). Shows the
 * app's own logcat buffer (main + system + crash) via `logcat -d`, which
 * requires no extra permission since apps can only read their own process's
 * log lines by default on modern Android. Includes an optional filter to
 * narrow down to LSFG_DIAG lines only (see LsfgVkManager /
 * GuestProgramLauncherComponent) for frame-gen debugging specifically.
 */
public class LogViewerActivity extends AppCompatActivity {
    private static final String TAG = "LogViewerActivity";

    private TextView logContentView;
    private ScrollView scrollView;
    private CheckBox filterCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_viewer_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("App Log");
        }

        logContentView = findViewById(R.id.TVLogContent);
        scrollView = (ScrollView) logContentView.getParent();
        filterCheckBox = findViewById(R.id.CBLogFilterLsfgOnly);

        findViewById(R.id.BTLogRefresh).setOnClickListener(v -> refreshLog());
        findViewById(R.id.BTLogShare).setOnClickListener(v -> shareLog());
        findViewById(R.id.BTLogClear).setOnClickListener(v -> clearLog());
        filterCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> refreshLog());

        refreshLog();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshLog() {
        logContentView.setText("Loading log...");
        new ReadLogTask().execute(filterCheckBox.isChecked());
    }

    private void clearLog() {
        try {
            Runtime.getRuntime().exec("logcat -c");
            Toast.makeText(this, "Logcat buffer cleared", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Failed to clear logcat buffer", e);
        }
        refreshLog();
    }

    private void shareLog() {
        String content = logContentView.getText().toString();
        if (content.trim().isEmpty()) {
            Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(getCacheDir(), "logs");
            if (!dir.exists()) dir.mkdirs();
            File outFile = new File(dir, "lsfg_log.txt");
            try (FileWriter writer = new FileWriter(outFile, false)) {
                writer.write(content);
            }

            android.net.Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".tileprovider", outFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share LSFG log"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to share log", e);
            Toast.makeText(this, "Failed to share log: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class ReadLogTask extends AsyncTask<Boolean, Void, String> {
        @Override
        protected String doInBackground(Boolean... params) {
            boolean lsfgOnly = params.length > 0 && params[0];
            StringBuilder sb = new StringBuilder();
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-d", "-v", "time", "-b", "main", "-b", "crash", "-b", "system"});
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!lsfgOnly
                                || line.contains("LSFG_DIAG")
                                || line.contains("LsfgVkManager")
                                || line.contains("FrameGenManager")) {
                            sb.append(line).append('\n');
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to read logcat", e);
                sb.append("Failed to read log: ").append(e.getMessage());
            }
            if (sb.length() == 0) {
                sb.append(lsfgOnly
                        ? "No LSFG_DIAG log lines yet.\n\nLaunch a game/shortcut with LSFG enabled first, then come back and press Refresh."
                        : "Log is empty.");
            }
            return sb.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            logContentView.setText(result);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }
}
