package com.winlator.cmod.core;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central in-memory log collector for the whole app process.
 *
 * Started from LudashiApp.onCreate() (if the user previously enabled it) so it is alive
 * before any guest process is launched. This matters because ProcessHelper.exec() decides,
 * at the moment each process starts, whether to keep its stdout/stderr pipes open or redirect
 * them to /dev/null based on whether ANY debug callback is registered at that instant
 * (see ProcessHelper#exec). Opening the old DebugDialog after a game/process is already
 * running was therefore too late to catch its output. Registering this collector as a
 * permanent debug callback at process startup fixes that.
 *
 * Two sources feed the same ring buffer:
 *  1. ProcessHelper debug callback -> wine/box64/guest process stdout+stderr lines.
 *  2. A background `logcat -v time` streaming reader -> the app's own Java-side log lines
 *     (any Log.d/e/... call anywhere in the codebase). Apps can only see their own process's
 *     lines by default on modern Android, so no extra permission is required.
 *
 * The enabled/disabled state is persisted so capture resumes automatically on next launch.
 */
public final class AppLogCollector implements Callback<String> {
    private static final String TAG = "AppLogCollector";
    private static final String PREF_KEY = "log_capture_enabled";
    private static final int MAX_LINES = 8000;

    private static final AppLogCollector INSTANCE = new AppLogCollector();

    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final Object lock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Process logcatProcess;

    private AppLogCollector() {}

    public static AppLogCollector getInstance() {
        return INSTANCE;
    }

    public static boolean isEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_KEY, false);
    }

    /** Call once from Application.onCreate(). Resumes capture if the user left it turned on. */
    public void startIfEnabled(Context context) {
        if (isEnabled(context)) start(context.getApplicationContext());
    }

    /** Call from the Settings toggle. Persists the choice and starts/stops capture live. */
    public void setEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_KEY, enabled).apply();
        if (enabled) {
            start(context.getApplicationContext());
        } else {
            stop();
        }
    }

    public synchronized void start(Context appContext) {
        if (!running.compareAndSet(false, true)) return;
        synchronized (lock) {
            lines.clear();
        }
        append("APP", "==== Log capture started ====");
        ProcessHelper.addDebugCallback(this);
        startLogcatStream();
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;
        ProcessHelper.removeDebugCallback(this);
        Process p = logcatProcess;
        logcatProcess = null;
        if (p != null) p.destroy();
    }

    public boolean isRunning() {
        return running.get();
    }

    /** ProcessHelper debug callback: fires for every wine/box64/guest process stdout+stderr line. */
    @Override
    public void call(String line) {
        append("WINE/BOX64", DateFormat.format("HH:mm:ss", new Date()) + " " + line);
    }

    private void startLogcatStream() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-v", "time", "-b", "main", "-b", "crash", "-b", "system"});
                logcatProcess = process;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while (running.get() && (line = reader.readLine()) != null) {
                        append("APP", line);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "logcat streaming failed", e);
            } finally {
                logcatProcess = null;
            }
        });
    }

    private void append(String source, CharSequence line) {
        String out = "[" + source + "] " + line;
        synchronized (lock) {
            lines.addLast(out);
            while (lines.size() > MAX_LINES) lines.removeFirst();
        }
    }

    public String getSnapshot() {
        synchronized (lock) {
            if (lines.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(l).append('\n');
            return sb.toString();
        }
    }

    public void clear() {
        synchronized (lock) {
            lines.clear();
        }
    }
}
