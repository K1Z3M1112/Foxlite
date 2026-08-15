package com.nativewinruntime

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    external fun nativeRuntimeInfo(): String

    private lateinit var runtime: RuntimeManager
    private lateinit var bridge: ProcessBridge
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var console: TextView
    private var selectedUri: Uri? = null
    private var selectedName = "No EXE selected"
    private var currentPage = "home"
    private val green = Color.rgb(112, 255, 157)
    private val bg = Color.rgb(7, 10, 9)
    private val panel = Color.rgb(14, 19, 17)
    private val border = Color.rgb(35, 52, 44)
    private val muted = Color.rgb(137, 158, 148)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.statusBarColor = bg
        window.navigationBarColor = bg
        runtime = RuntimeManager(this)
        bridge = ProcessBridge(this, runtime)
        buildShell()
        showPage("home")
        if (!runtime.isReady()) prepareRuntime()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        showPage(currentPage)
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(10))
            setBackgroundColor(Color.rgb(10, 14, 12))
        }
        val brand = TextView(this).apply {
            text = "NATIVE WIN // CONSOLE"
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(green)
        }
        statusText = TextView(this).apply {
            text = " ● READY"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(green)
            gravity = Gravity.END
        }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(statusText)
        root.addView(header)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(Color.rgb(10, 14, 12))
        }
        listOf("HOME" to "home", "EMULATOR" to "emulator", "LOGS" to "logs", "SETTINGS" to "settings").forEach {
            val b = navButton(it.first)
            b.setOnClickListener { showPage(it.second) }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun showPage(page: String) {
        currentPage = page
        content.removeAllViews()
        when (page) {
            "home" -> homePage()
            "emulator" -> emulatorPage()
            "logs" -> logsPage()
            "settings" -> settingsPage()
        }
    }

    private fun homePage() {
        val box = column()
        box.addView(title("NATIVE WIN RUNTIME", "Android Windows runtime / PC emulator"))
        val state = panelView()
        val ready = runtime.isReady()
        state.addView(label("RUNTIME", if (ready) "INSTALLED / READY" else "NOT INSTALLED", green))
        state.addView(label("PROGRAM", selectedName, Color.WHITE))
        state.addView(label("DISPLAY", displayInfo(), Color.WHITE))
        box.addView(state)

        val prepare = action("INSTALL / REPAIR RUNTIME")
        prepare.setOnClickListener { prepareRuntime() }
        box.addView(prepare)
        val select = action("SELECT WINDOWS EXE")
        select.setOnClickListener { pickExe() }
        box.addView(select)
        val launch = action("▶  LAUNCH PROGRAM")
        launch.setOnClickListener { launchSelected() }
        box.addView(launch)
        val stop = action("■  STOP")
        stop.setOnClickListener { bridge.stop(); status("STOPPED"); appendLog("Process stopped.") }
        box.addView(stop)
        box.addView(label("SYSTEM", "Vulkan / ARM64 host / Box64 + Wine", muted))
        content.addView(scroll(box))
    }

    private fun emulatorPage() {
        val box = column()
        box.addView(title("PC EMULATOR", "Windows application execution"))
        val info = panelView()
        info.addView(label("ARCH", "ARM64 host → x86_64 Windows", Color.WHITE))
        info.addView(label("GRAPHICS", "Android Vulkan → Wine graphics path", Color.WHITE))
        info.addView(label("PREFIX", runtime.prefix().absolutePath, muted))
        info.addView(label("HOME", runtime.home().absolutePath, muted))
        box.addView(info)
        val select = action("SELECT EXE")
        select.setOnClickListener { pickExe() }
        box.addView(select)
        val launch = action("LAUNCH")
        launch.setOnClickListener { launchSelected() }
        box.addView(launch)
        box.addView(label("NOTE", "Compatibility depends on the selected Windows program, Wine build and device GPU driver.", muted))
        content.addView(scroll(box))
    }

    private fun logsPage() {
        val box = column()
        box.addView(title("SYSTEM LOG", "Live runtime output"))
        console = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.rgb(183, 219, 199))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            text = "Native Win Runtime console\n> ${nativeRuntimeInfo()}"
        }
        box.addView(card(console), LinearLayout.LayoutParams(-1, dp(320)))
        val clear = action("CLEAR LOG")
        clear.setOnClickListener { console.text = "" }
        box.addView(clear)
        content.addView(scroll(box))
    }

    private fun settingsPage() {
        val box = column()
        box.addView(title("SETTINGS", "Runtime and interface"))
        val orientation = action("FOLLOW DEVICE ORIENTATION")
        orientation.setOnClickListener {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            toast("Orientation: automatic")
        }
        box.addView(orientation)
        val portrait = action("FORCE PORTRAIT")
        portrait.setOnClickListener { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        box.addView(portrait)
        val landscape = action("FORCE LANDSCAPE")
        landscape.setOnClickListener { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        box.addView(landscape)
        val repair = action("REINSTALL RUNTIME")
        repair.setOnClickListener { prepareRuntime(force = true) }
        box.addView(repair)
        box.addView(label("RUNTIME PATH", runtime.dir().absolutePath, muted))
        box.addView(label("VERSION", "0.6 / Android native runtime UI", muted))
        content.addView(scroll(box))
    }

    private fun prepareRuntime(force: Boolean = false) {
        status("INSTALLING")
        appendLog("Preparing runtime assets…")
        Thread {
            val result = runtime.prepare(force)
            runOnUiThread {
                result.onSuccess {
                    status("READY")
                    appendLog("Runtime ready: ${it.absolutePath}")
                    showPage(currentPage)
                }.onFailure {
                    status("ERROR")
                    appendLog("Runtime install failed: ${it.message}")
                    toast("Runtime install failed")
                }
            }
        }.start()
    }

    private fun pickExe() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/octet-stream"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, 42)
    }

    private fun launchSelected() {
        val uri = selectedUri ?: run { toast("Select a Windows EXE first"); return }
        status("RUNNING")
        appendLog("Launching $selectedName")
        bridge.launch(uri) { line ->
            runOnUiThread {
                appendLog(line)
                if (line.startsWith("Process exited")) status("READY")
            }
        }
    }

    private fun appendLog(s: String) {
        if (!::console.isInitialized) return
        console.append("\n> $s")
    }

    private fun status(s: String) {
        statusText.text = " ● $s"
    }

    private fun displayInfo(): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val orientation = if (w >= h) "LANDSCAPE" else "PORTRAIT"
        return "$orientation  ${w}×${h}  density=${String.format(Locale.US, "%.1f", dm.density)}"
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(18))
    }

    private fun title(a: String, b: String): View {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(14))
        }
        l.addView(label("", a, green).apply { textSize = 22f; typeface = Typeface.MONOSPACE })
        l.addView(label("", b, muted))
        return l
    }

    private fun panelView() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setBackgroundColor(panel)
    }

    private fun card(v: View) = FrameLayout(this).apply {
        setBackgroundColor(panel)
        setPadding(dp(2), dp(2), dp(2), dp(2))
        addView(v)
    }

    private fun label(k: String, v: String, color: Int): TextView = TextView(this).apply {
        text = if (k.isBlank()) v else "$k  $v"
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextColor(color)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun action(text: String) = Button(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextColor(green)
        setBackgroundColor(Color.rgb(18, 28, 23))
        isAllCaps = false
        stateListAnimator = null
        val lp = LinearLayout.LayoutParams(-1, dp(52))
        lp.setMargins(0, dp(6), 0, dp(2))
        layoutParams = lp
    }

    private fun navButton(text: String) = TextView(this).apply {
        this.text = text
        textSize = 10f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(2), 0, dp(2), 0)
    }

    private fun scroll(v: View) = ScrollView(this).apply { addView(v) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        bridge.stop()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK) {
            selectedUri = data?.data
            selectedName = selectedUri?.lastPathSegment?.substringAfterLast('/') ?: "Selected EXE"
            appendLog("Selected: $selectedName")
            showPage(currentPage)
        }
    }

    companion object { init { System.loadLibrary("native_runtime") } }
}
