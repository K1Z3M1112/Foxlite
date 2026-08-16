package com.winlator.cmod

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import com.winlator.cmod.core.AppExecutors
import com.winlator.cmod.core.AppLogCollector
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader

/**
 * In-app log viewer for the whole app process, not just LSFG.
 *
 * If log capture is turned on (Settings > Advanced > Logs), this shows the merged
 * AppLogCollector buffer: the app's own logcat lines PLUS wine/box64/guest process
 * stdout+stderr, all timestamped and collected continuously since app startup
 * (see LudashiApp / AppLogCollector). If capture is off, falls back to a one-shot
 * `logcat -d` dump of just this process's own log lines (no wine/box64 output,
 * since nothing has been listening for it). Includes an optional filter to narrow
 * down to LSFG_DIAG lines only (see LsfgVkManager / GuestProgramLauncherComponent).
 */
class LogViewerActivity : AppCompatActivity() {

    private lateinit var logContentView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var filterCheckBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.log_viewer_activity)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "App Log"
        }

        logContentView = findViewById(R.id.TVLogContent)
        scrollView = logContentView.parent as ScrollView
        filterCheckBox = findViewById(R.id.CBLogFilterLsfgOnly)

        findViewById<android.view.View>(R.id.BTLogRefresh).setOnClickListener { refreshLog() }
        findViewById<android.view.View>(R.id.BTLogShare).setOnClickListener { shareLog() }
        findViewById<android.view.View>(R.id.BTLogClear).setOnClickListener { clearLog() }
        filterCheckBox.setOnCheckedChangeListener { _, _ -> refreshLog() }

        refreshLog()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshLog() {
        logContentView.text = "Loading log..."
        val lsfgOnly = filterCheckBox.isChecked

        AppExecutors.io.execute {
            val result = readLog(lsfgOnly)
            AppExecutors.mainThread.post {
                if (isFinishing || isDestroyed) return@post
                logContentView.text = result
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }

    private fun clearLog() {
        AppLogCollector.getInstance().clear()
        try {
            Runtime.getRuntime().exec("logcat -c")
            Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to clear logcat buffer", e)
        }
        refreshLog()
    }

    private fun shareLog() {
        val content = logContentView.text.toString()
        if (content.isBlank()) {
            Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dir = File(cacheDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "lsfg_log.txt")
            FileWriter(outFile, false).use { it.write(content) }

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.tileprovider", outFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share LSFG log"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share log", e)
            Toast.makeText(this, "Failed to share log: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun matchesLsfgFilter(line: String): Boolean =
        line.contains("LSFG_DIAG") || line.contains("LsfgVkManager") || line.contains("FrameGenManager")

    /** Runs off the UI thread via [AppExecutors.io]. Must not touch views. */
    private fun readLog(lsfgOnly: Boolean): String {
        val capturing = AppLogCollector.getInstance().isRunning
        val raw = if (capturing) AppLogCollector.getInstance().snapshot else readOneShotLogcat()

        val sb = StringBuilder(raw.length)
        for (line in raw.split("\n")) {
            if (line.isEmpty()) continue
            if (!lsfgOnly || matchesLsfgFilter(line)) sb.append(line).append('\n')
        }

        if (sb.isEmpty()) {
            when {
                lsfgOnly -> sb.append(
                    "No LSFG_DIAG log lines yet.\n\nLaunch a game/shortcut with LSFG enabled first, then come back and press Refresh."
                )
                !capturing -> sb.append(
                    "Log is empty.\n\nTurn on \"Capture full log (app + wine/box64) since launch\" in Settings > Advanced > Logs to also record wine/box64 process output continuously, starting from app launch."
                )
                else -> sb.append("Log is empty.")
            }
        }
        return sb.toString()
    }

    private fun readOneShotLogcat(): String {
        val sb = StringBuilder()
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "-b", "main", "-b", "crash", "-b", "system")
            )
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { line -> sb.append("[APP] ").append(line).append('\n') }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read logcat", e)
            sb.append("Failed to read log: ${e.message}")
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "LogViewerActivity"
    }
}
