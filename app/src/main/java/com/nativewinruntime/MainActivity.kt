package com.nativewinruntime

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    external fun nativeRuntimeInfo(): String

    private lateinit var runtime: RuntimeManager
    private lateinit var bridge: ProcessBridge
    private lateinit var prefs: Prefs

    private lateinit var grid: GridView
    private lateinit var statusPill: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var selectedLabel: TextView

    private var selectedUri: Uri? = null
    private var selectedName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        runtime = RuntimeManager(this)
        bridge = ProcessBridge(this, runtime)
        prefs = Prefs(this)

        grid = findViewById(R.id.library_grid)
        statusPill = findViewById(R.id.status_pill)
        logView = findViewById(R.id.log_view)
        logScroll = findViewById(R.id.log_scroll)
        selectedLabel = findViewById(R.id.selected_label)

        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_launch).setOnClickListener { launchSelected() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            bridge.stop()
            appendLog("Stopped.")
        }

        logScroll.visibility = if (prefs.showConsole) View.VISIBLE else View.GONE

        refreshGrid()
        appendLog(nativeRuntimeInfo())
        prepareRuntimeInBackground()
    }

    override fun onResume() {
        super.onResume()
        logScroll.visibility = if (prefs.showConsole) View.VISIBLE else View.GONE
        refreshGrid()
        updateStatusPill()
    }

    private fun prepareRuntimeInBackground() {
        statusPill.text = "PREPARING RUNTIME…"
        Thread {
            val result = runtime.prepare()
            runOnUiThread {
                result.fold(
                    onSuccess = { appendLog("Runtime ready (bundled assets extracted).") },
                    onFailure = { appendLog("Runtime install failed: ${it.message}") }
                )
                updateStatusPill()
            }
        }.start()
    }

    private fun updateStatusPill() {
        statusPill.text = if (runtime.isReady()) "RUNTIME READY" else "RUNTIME NOT READY"
    }

    private fun refreshGrid() {
        val entries = prefs.library()
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = entries.size + 1
            override fun getItem(position: Int): Any? = entries.getOrNull(position - 1)
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_tile, parent, false)
                val icon = view.findViewById<TextView>(R.id.tile_icon)
                val label = view.findViewById<TextView>(R.id.tile_label)
                if (position == 0) {
                    icon.text = "➕"
                    label.text = "Add EXE"
                    view.setOnClickListener { pickExe() }
                    view.setOnLongClickListener { true }
                } else {
                    val entry = entries[position - 1]
                    icon.text = "🗔"
                    label.text = entry.name
                    view.setOnClickListener {
                        selectedUri = Uri.parse(entry.uri)
                        selectedName = entry.name
                        selectedLabel.text = entry.name
                    }
                    view.setOnLongClickListener {
                        confirmRemove(entry)
                        true
                    }
                }
                return view
            }
        }
    }

    private fun confirmRemove(entry: Prefs.LibraryEntry) {
        AlertDialog.Builder(this)
            .setTitle("Remove from library")
            .setMessage(entry.name)
            .setPositiveButton("Remove") { _, _ ->
                prefs.removeFromLibrary(entry.uri)
                if (selectedUri.toString() == entry.uri) {
                    selectedUri = null
                    selectedLabel.text = "No program selected"
                }
                refreshGrid()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickExe() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, REQUEST_PICK_EXE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_EXE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers don't support persistable permissions; the exe
                // still works for this session via the returned Uri.
            }
            val name = queryDisplayName(uri) ?: "Program.exe"
            prefs.addToLibrary(name, uri.toString())
            selectedUri = uri
            selectedName = name
            selectedLabel.text = name
            refreshGrid()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return null
    }

    private fun launchSelected() {
        val uri = selectedUri ?: run {
            appendLog("Select a program from the library first.")
            return
        }
        if (!runtime.isReady()) {
            appendLog("Runtime is still preparing. Please wait.")
            return
        }
        appendLog("Starting $selectedName…")
        bridge.launch(uri) { line -> runOnUiThread { appendLog(line) } }
    }

    private fun appendLog(line: String) {
        logView.append("\n$line")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        bridge.stop()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PICK_EXE = 42
        init { System.loadLibrary("native_runtime") }
    }
}
