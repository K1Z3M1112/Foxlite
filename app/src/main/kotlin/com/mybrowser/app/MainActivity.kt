package com.mybrowser.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

// Constants
const val INTERNAL_HOME_URL = "about:blank"
const val MAX_TABS = 15

object ContextMenuType {
    const val UNKNOWN = 0
    const val LINK = 1
    const val IMAGE = 2
    const val IMAGE_LINK = 3
}

// ═══════════════════════════════════════════════════════════
// 1. Native SQLite Database Helper
// ═══════════════════════════════════════════════════════════
data class HistoryItem(val id: Long, val url: String, val title: String, val timestamp: Long, val favicon: String?)
data class BookmarkItem(val id: Long, val url: String, val title: String, val timestamp: Long, val favicon: String?)
data class SavedTab(val id: String, val url: String, val title: String, val isDesktop: Boolean, val position: Int)

class NativeBrowserDb(context: Context) : SQLiteOpenHelper(context, "browser_native.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, title TEXT, timestamp INTEGER, favicon TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, title TEXT, timestamp INTEGER, favicon TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tabs (id TEXT PRIMARY KEY, url TEXT, title TEXT, is_desktop INTEGER, position INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS custom_filters (domain TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_url ON history(url)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_timestamp ON history(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_url ON bookmarks(url)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { onCreate(db) }

    fun insertHistory(url: String, title: String, favicon: String? = null) {
        if (url == INTERNAL_HOME_URL || url.isBlank()) return
        try {
            val values = ContentValues().apply { put("url", url); put("title", title); put("timestamp", System.currentTimeMillis()); put("favicon", favicon) }
            writableDatabase.insert("history", null, values)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun updateHistoryFavicon(url: String, favicon: String) {
        try {
            val values = ContentValues().apply { put("favicon", favicon) }
            writableDatabase.update("history", values, "url=?", arrayOf(url))
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getAllHistory(): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        try {
            readableDatabase.rawQuery("SELECT id, url, title, timestamp, favicon FROM history ORDER BY timestamp DESC LIMIT 200", null).use { cursor ->
                while (cursor.moveToNext()) list.add(HistoryItem(cursor.getLong(0), cursor.getString(1) ?: "", cursor.getString(2) ?: "", cursor.getLong(3), cursor.getString(4)))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun escapeLike(input: String): String = input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun searchHistoryAndBookmarks(query: String, limit: Int = 6): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        if (query.isBlank()) return results
        try {
            val like = "%${escapeLike(query.trim())}%"
            readableDatabase.rawQuery(
                "SELECT title, url FROM (SELECT title, url, timestamp FROM bookmarks WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' UNION ALL SELECT title, url, timestamp FROM history WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\') ORDER BY timestamp DESC LIMIT ?",
                arrayOf(like, like, like, like, limit.toString())
            ).use { cursor -> while (cursor.moveToNext()) results.add((cursor.getString(0) ?: "") to (cursor.getString(1) ?: "")) }
        } catch (e: Exception) { e.printStackTrace() }
        return results.distinctBy { it.second }
    }

    fun deleteHistoryById(id: Long) { try { writableDatabase.delete("history", "id=?", arrayOf(id.toString())) } catch (e: Exception) {} }
    fun clearAllHistory() { try { writableDatabase.delete("history", null, null) } catch (e: Exception) {} }

    fun insertBookmark(url: String, title: String, favicon: String? = null) {
        if (url == INTERNAL_HOME_URL || url.isBlank()) return
        try {
            if (isBookmarked(url)) return
            val values = ContentValues().apply { put("url", url); put("title", title); put("timestamp", System.currentTimeMillis()); put("favicon", favicon) }
            writableDatabase.insert("bookmarks", null, values)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun isBookmarked(url: String): Boolean {
        return try { readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE url=? LIMIT 1", arrayOf(url)).use { it.moveToFirst() } } catch (e: Exception) { false }
    }

    fun getAllBookmarks(): List<BookmarkItem> {
        val list = mutableListOf<BookmarkItem>()
        try {
            readableDatabase.rawQuery("SELECT id, url, title, timestamp, favicon FROM bookmarks ORDER BY timestamp DESC", null).use { cursor ->
                while (cursor.moveToNext()) list.add(BookmarkItem(cursor.getLong(0), cursor.getString(1) ?: "", cursor.getString(2) ?: "", cursor.getLong(3), cursor.getString(4)))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun deleteBookmarkByUrl(url: String) { try { writableDatabase.delete("bookmarks", "url=?", arrayOf(url)) } catch (e: Exception) {} }

    fun saveTabs(tabs: List<SavedTab>) {
        try {
            writableDatabase.beginTransaction()
            writableDatabase.delete("tabs", null, null)
            tabs.forEach { t ->
                val values = ContentValues().apply { put("id", t.id); put("url", t.url); put("title", t.title); put("is_desktop", if (t.isDesktop) 1 else 0); put("position", t.position) }
                writableDatabase.insert("tabs", null, values)
            }
            writableDatabase.setTransactionSuccessful()
        } catch (e: Exception) { e.printStackTrace() } finally { try { writableDatabase.endTransaction() } catch (e: Exception) {} }
    }

    fun loadTabs(): List<SavedTab> {
        val list = mutableListOf<SavedTab>()
        try {
            readableDatabase.rawQuery("SELECT id, url, title, is_desktop, position FROM tabs ORDER BY position ASC", null).use { cursor ->
                while (cursor.moveToNext()) list.add(SavedTab(cursor.getString(0) ?: "", cursor.getString(1) ?: INTERNAL_HOME_URL, cursor.getString(2) ?: "หน้าแรก", cursor.getInt(3) == 1, cursor.getInt(4)))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun addCustomFilter(domain: String) { try { val values = ContentValues().apply { put("domain", domain.lowercase().trim()) }; writableDatabase.insertWithOnConflict("custom_filters", null, values, SQLiteDatabase.CONFLICT_IGNORE) } catch (e: Exception) { e.printStackTrace() } }
    fun removeCustomFilter(domain: String) { try { writableDatabase.delete("custom_filters", "domain=?", arrayOf(domain.lowercase().trim())) } catch (e: Exception) {} }
    fun getCustomFilters(): List<String> {
        val list = mutableListOf<String>()
        try { readableDatabase.rawQuery("SELECT domain FROM custom_filters ORDER BY domain ASC", null).use { cursor -> while (cursor.moveToNext()) list.add(cursor.getString(0) ?: "") } } catch (e: Exception) { e.printStackTrace() }
        return list
    }
    fun getSetting(key: String, default: String): String { try { readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { cursor -> if (cursor.moveToFirst()) return cursor.getString(0) ?: default } } catch (e: Exception) { e.printStackTrace() }; return default }
    fun setSetting(key: String, value: String) { try { val values = ContentValues().apply { put("key", key); put("value", value) }; writableDatabase.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE) } catch (e: Exception) { e.printStackTrace() } }
}

// ═══════════════════════════════════════════════════════════
// 2. Data Models & Utilities
// ═══════════════════════════════════════════════════════════
class TabState(
    val id: String = java.util.UUID.randomUUID().toString(),
    url: String,
    title: String = "หน้าแรก",
    isDesktop: Boolean = false,
    val isIncognito: Boolean = false
) {
    var url by mutableStateOf(url)
    var title by mutableStateOf(title)
    var isDesktop by mutableStateOf(isDesktop)
    var favicon by mutableStateOf<Bitmap?>(null)
    var reloadKey by mutableIntStateOf(0)
    var savedState: GeckoSession.SessionState? = null
    var snapshot by mutableStateOf<Bitmap?>(null)
    var isSuspended by mutableStateOf(false)
}

data class ContextMenuData(val type: Int, val url: String)

enum class SearchEngine(val label: String, val urlPrefix: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave", "https://search.brave.com/search?q=")
}

object BrowserSecurity {
    private val BLOCKED_SCHEMES = arrayOf("file:", "content:", "javascript:", "vbscript:", "data:")
    private val DOMAIN_REGEX = Regex("^(https?://)?([\\w-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?(/.*)?$")

    fun isDangerous(url: String?): Boolean {
        if (url == null) return true
        val u = url.lowercase().trim()
        return BLOCKED_SCHEMES.any { u.startsWith(it) }
    }

    fun sanitizeUrl(input: String?, engine: SearchEngine = SearchEngine.GOOGLE): String {
        if (input.isNullOrBlank()) return INTERNAL_HOME_URL
        var s = input.trim()
        if (isDangerous(s)) return INTERNAL_HOME_URL
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            val looksLikeDomain = !s.contains(" ") && DOMAIN_REGEX.matches(s)
            s = if (looksLikeDomain) "https://$s" else engine.urlPrefix + Uri.encode(s)
        }
        return s
    }
}

object PinSecurity {
    fun generateSalt(): String { val bytes = ByteArray(16); SecureRandom().nextBytes(bytes); return Base64.encodeToString(bytes, Base64.NO_WRAP) }
    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256"); var data = (salt + pin).toByteArray(Charsets.UTF_8)
        repeat(1000) { data = digest.digest(data); digest.reset() }
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }
    fun verify(pin: String, salt: String, storedHash: String): Boolean = hash(pin, salt) == storedHash
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
    Toast.makeText(context, "📋 คัดลอกลงคลิปบอร์ดแล้ว", Toast.LENGTH_SHORT).show()
}

fun downloadFile(context: Context, url: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, null, null))
        }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(context, "📥 เริ่มดาวน์โหลด...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "❌ ดาวน์โหลดล้มเหลว", Toast.LENGTH_SHORT).show()
    }
}

fun bitmapToBase64(bitmap: Bitmap, maxSize: Int = 48): String {
    return try {
        val scaled = Bitmap.createScaledBitmap(bitmap, maxSize, maxSize, true)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 80, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { "" }
}

fun base64ToBitmap(base64: String?): Bitmap? {
    if (base64.isNullOrBlank()) return null
    return try { val bytes = Base64.decode(base64, Base64.NO_WRAP); android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (e: Exception) { null }
}

// ═══════════════════════════════════════════════════════════
// 3. Thread-Safe AdBlock Engine & Gecko Engine Singleton
// ═══════════════════════════════════════════════════════════
object GeckoEngine {
    private var runtime: GeckoRuntime? = null
    fun getRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .build()
            runtime = GeckoRuntime.create(context.applicationContext, settings)
        }
        return runtime!!
    }
}

class AdBlockEngine(private val context: Context, private val dbHelper: NativeBrowserDb) {
    private val hostFile = File(context.filesDir, "adblock_hosts.txt")
    private val hostFileTmp = File(context.filesDir, "adblock_hosts.txt.tmp")

    @Volatile private var blockedDomains: Set<String> = emptySet()
    private val mainHandler = Handler(Looper.getMainLooper())

    var isLoaded by mutableStateOf(false)
        private set
    var isUpdating by mutableStateOf(false)
        private set
    var ruleCount by mutableIntStateOf(0)
        private set
    var statusText by mutableStateOf("กำลังเตรียมระบบ...")
        private set

    private val hostApiUrls = listOf("https://adaway.org/hosts.txt", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")

    init { if (hostFile.exists() && hostFile.length() > 0) loadFromDisk() else updateFromNetwork() }

    private fun loadCustomFilters(): Set<String> = try { dbHelper.getCustomFilters().map { it.lowercase() }.toSet() } catch (e: Exception) { emptySet() }

    fun loadFromDisk() {
        thread {
            try {
                mainHandler.post { statusText = "กำลังอ่านข้อมูล AdBlock..." }
                val newBlocked = HashSet<String>()
                if (hostFile.exists()) {
                    hostFile.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val parts = trimmed.split("\\s+".toRegex())
                            val domain = if (parts.size >= 2) parts[1] else parts[0]
                            if (domain.isNotBlank() && domain != "0.0.0.0" && domain != "127.0.0.1") newBlocked.add(domain.lowercase())
                        }
                    }
                }
                newBlocked.addAll(loadCustomFilters())
                mainHandler.post { blockedDomains = newBlocked; ruleCount = blockedDomains.size; isLoaded = true; statusText = "พร้อมใช้งาน ($ruleCount รายการ)" }
            } catch (e: Exception) { mainHandler.post { statusText = "โหลดข้อมูลล้มเหลว: ${e.message}" } }
        }
    }

    fun updateFromNetwork() {
        if (isUpdating) return
        isUpdating = true
        mainHandler.post { statusText = "กำลังดาวน์โหลดกฎ AdBlock..." }
        thread {
            try {
                val combined = StringBuilder()
                var anySuccess = false
                for (apiUrl in hostApiUrls) {
                    try {
                        val connection = URL(apiUrl).openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
                        val text = connection.getInputStream().bufferedReader().use { it.readText() }
                        if (text.isNotBlank()) { combined.append(text).append("\n"); anySuccess = true }
                    } catch (inner: Exception) {}
                }
                if (anySuccess) { hostFileTmp.writeText(combined.toString()); if (hostFile.exists()) hostFile.delete(); hostFileTmp.renameTo(hostFile); loadFromDisk() } 
                else mainHandler.post { statusText = "อัปเดตล้มเหลว (เช็กอินเทอร์เน็ต)" }
            } catch (e: Exception) { mainHandler.post { statusText = "อัปเดตล้มเหลว (เช็กอินเทอร์เน็ต)" } } 
            finally { mainHandler.post { isUpdating = false } }
        }
    }

    fun refreshCustomFiltersOnly() { thread { loadFromDisk() } }

    fun isAd(url: String): Boolean {
        if (!isLoaded || url.isBlank()) return false
        val host = try { Uri.parse(url).host?.lowercase() } catch (e: Exception) { null } ?: return false
        var currentHost = host
        while (currentHost.contains(".")) {
            if (blockedDomains.contains(currentHost)) return true
            currentHost = currentHost.substringAfter(".", "")
        }
        return false
    }
}

// ═══════════════════════════════════════════════════════════
// 4. Activity Entry Point
// ═══════════════════════════════════════════════════════════
class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("Browser", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BrowserApp(prefs = prefs, onExitApp = { finishAffinity() })
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 5. Root App & PIN Gate
// ═══════════════════════════════════════════════════════════
@Composable
fun BrowserApp(prefs: SharedPreferences, onExitApp: () -> Unit) {
    var unlocked by rememberSaveable { mutableStateOf(false) }
    if (!unlocked) PasswordGateDialog(prefs = prefs, onUnlocked = { unlocked = true }, onExit = onExitApp)
    else BrowserScreen(prefs = prefs)
}

@Composable
fun PasswordGateDialog(prefs: SharedPreferences, onUnlocked: () -> Unit, onExit: () -> Unit) {
    val savedHash = remember { prefs.getString("app_pin_hash", "") ?: "" }
    val savedSalt = remember { prefs.getString("app_pin_salt", "") ?: "" }
    val isSetupMode = savedHash.isEmpty()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    val isLockedOut = failedAttempts >= 5

    AlertDialog(
        onDismissRequest = { },
        title = { Text(if (isSetupMode) "🆕 ตั้งรหัสผ่านของคุณ" else "🔐 ยืนยันรหัสผ่าน") },
        text = {
            Column {
                Text(if (isSetupMode) "กรุณากำหนด PIN เพื่อเข้าใช้งาน" else "กรุณากรอกรหัสผ่านเพื่อเข้าสู่ระบบ")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it; error = null },
                    singleLine = true, enabled = !isLockedOut,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (isLockedOut) { Spacer(Modifier.height(4.dp)); Text("⛔ ลองผิดหลายครั้งเกินไป กรุณาปิดแล้วเปิดแอปใหม่", color = MaterialTheme.colorScheme.error) }
                error?.let { Spacer(Modifier.height(4.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLockedOut,
                onClick = {
                    val entered = pin.trim()
                    if (entered.isEmpty()) { error = "❌ ห้ามปล่อยช่องว่าง"; return@TextButton }
                    if (entered.length < 4) { error = "❌ PIN ต้องมีอย่างน้อย 4 หลัก"; return@TextButton }
                    if (isSetupMode) {
                        val newSalt = PinSecurity.generateSalt()
                        val newHash = PinSecurity.hash(entered, newSalt)
                        prefs.edit().putString("app_pin_hash", newHash).putString("app_pin_salt", newSalt).apply()
                        onUnlocked()
                    } else if (PinSecurity.verify(entered, savedSalt, savedHash)) { onUnlocked() } 
                    else { failedAttempts++; error = "❌ รหัสผ่านไม่ถูกต้อง"; pin = "" }
                }
            ) { Text("ตกลง") }
        },
        dismissButton = { TextButton(onClick = onExit) { Text("ออก") } }
    )
}

// ═══════════════════════════════════════════════════════════
// 6. Main Browser Screen
// ═══════════════════════════════════════════════════════════
@Composable
fun BrowserScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val dbHelper = remember { NativeBrowserDb(context) }
    val adBlockEngine = remember { AdBlockEngine(context, dbHelper) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showAdBlockDialog by remember { mutableStateOf(false) }
    var showDataDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var searchEngine by remember { mutableStateOf(SearchEngine.valueOf(dbHelper.getSetting("search_engine", SearchEngine.GOOGLE.name))) }

    val restoredTabs = remember { dbHelper.loadTabs() }
    val tabs = remember {
        mutableStateListOf<TabState>().apply {
            if (restoredTabs.isNotEmpty()) restoredTabs.forEach { add(TabState(id = it.id, url = it.url, title = it.title, isDesktop = it.isDesktop)) }
            else add(TabState(url = INTERNAL_HOME_URL))
        }
    }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    if (currentTab !in tabs.indices) currentTab = 0

    fun persistTabs() {
        val toSave = tabs.mapIndexedNotNull { idx, t -> if (t.isIncognito) null else SavedTab(t.id, t.url, t.title, t.isDesktop, idx) }
        thread { dbHelper.saveTabs(toSave) }
    }

    val sessionMap = remember { mutableStateMapOf<String, GeckoSession>() }

    var urlBarText by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showUrlSuggestions by remember { mutableStateOf(false) }
    var urlSuggestions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    var contextMenuData by remember { mutableStateOf<ContextMenuData?>(null) }

    var pendingRedirectUrl by remember { mutableStateOf<String?>(null) }
    var pendingRedirectTabId by remember { mutableStateOf<String?>(null) }

    var bookmarksList by remember { mutableStateOf(dbHelper.getAllBookmarks()) }
    fun refreshBookmarks() { bookmarksList = dbHelper.getAllBookmarks() }
    fun currentSession(): GeckoSession? = sessionMap[tabs.getOrNull(currentTab)?.id]

    fun navigate(target: String) {
        val tab = tabs.getOrNull(currentTab) ?: return
        tab.url = target
        currentSession()?.loadUri(target)
        showUrlSuggestions = false
    }

    fun switchToTab(newIndex: Int) {
        if (newIndex !in tabs.indices) return
        currentTab = newIndex
        showTabs = false

        val target = tabs[newIndex]
        urlBarText = if (target.url != INTERNAL_HOME_URL) target.url else ""

        val session = sessionMap[target.id]
        // In GeckoView, state is restored when attached or navigated
        progress = if (session != null && target.url != INTERNAL_HOME_URL) 100 else 0
        refreshBookmarks()
    }

    fun addTab(url: String = INTERNAL_HOME_URL, incognito: Boolean = false) {
        if (tabs.size >= MAX_TABS) { Toast.makeText(context, "⚠️ เปิดได้สูงสุด $MAX_TABS แท็บ", Toast.LENGTH_SHORT).show(); return }
        tabs.add(TabState(url = url, isIncognito = incognito))
        switchToTab(tabs.size - 1)
        persistTabs()
    }

    fun closeTab(idx: Int) {
        if (tabs.size <= 1 || idx !in tabs.indices) return
        val closedId = tabs[idx].id
        val wasCurrentId = tabs.getOrNull(currentTab)?.id

        sessionMap.remove(closedId)?.close()
        tabs.removeAt(idx)

        val newCurrentIndex = if (closedId == wasCurrentId) idx.coerceAtMost(tabs.size - 1)
        else tabs.indexOfFirst { it.id == wasCurrentId }.takeIf { it >= 0 } ?: (tabs.size - 1)
        switchToTab(newCurrentIndex)
        persistTabs()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> persistTabs()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sessionMap.values.forEach { it.close() }
        }
    }

    BackHandler(enabled = true) {
        when {
            pendingRedirectUrl != null -> { pendingRedirectUrl = null; pendingRedirectTabId = null }
            showUrlSuggestions -> showUrlSuggestions = false
            contextMenuData != null -> contextMenuData = null
            showMenu -> showMenu = false
            showTabs -> showTabs = false
            showDataDialog -> showDataDialog = false
            showSettingsDialog -> showSettingsDialog = false
            canGoBack -> currentSession()?.goBack()
            else -> (context as? ComponentActivity)?.finish()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {

        if (tabs.getOrNull(currentTab)?.isIncognito == true) {
            Surface(color = Color(0xFF3A2A55), modifier = Modifier.fillMaxWidth()) {
                Text("🕶️ โหมดไม่ระบุตัวตน — จะไม่บันทึกประวัติการเข้าชม", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            val activeTab = tabs.getOrNull(currentTab)
            if (activeTab != null) {
                val isTabHome = activeTab.url == INTERNAL_HOME_URL

                if (isTabHome) {
                    CustomHomePage(
                        bookmarks = bookmarksList,
                        onSearch = { query -> val target = BrowserSecurity.sanitizeUrl(query, searchEngine); activeTab.url = target; urlBarText = target; showUrlSuggestions = false },
                        onSelectBookmark = { url -> activeTab.url = url; urlBarText = url; showUrlSuggestions = false }
                    )
                } else {
                    key(activeTab.id, activeTab.reloadKey) {
                        CleanGeckoView(
                            initialUrl = activeTab.url,
                            savedState = activeTab.savedState,
                            isDesktop = activeTab.isDesktop,
                            isIncognito = activeTab.isIncognito,
                            adBlockEngine = adBlockEngine,
                            dbHelper = dbHelper,
                            onSessionCreated = { session -> sessionMap[activeTab.id] = session },
                            onUrlChanged = { url ->
                                if (url != INTERNAL_HOME_URL) { activeTab.url = url; urlBarText = url; persistTabs() }
                            },
                            onTitleChanged = { title ->
                                activeTab.title = if (activeTab.url == INTERNAL_HOME_URL) "หน้าแรก" else title?.take(20) ?: "Web Page"
                                persistTabs()
                            },
                            onProgressChanged = { p -> progress = p },
                            onNavStateChanged = { back, fwd -> canGoBack = back; canGoForward = fwd },
                            onLongPress = { contextMenuData = it },
                            onConfirmRedirect = { redirectUrl -> pendingRedirectUrl = redirectUrl; pendingRedirectTabId = activeTab.id },
                            onCrashed = {
                                sessionMap.remove(activeTab.id)?.close()
                                activeTab.savedState = null
                                activeTab.reloadKey++
                            },
                            onSuspend = { state, snapshot ->
                                activeTab.savedState = state
                                activeTab.snapshot = snapshot
                                activeTab.isSuspended = true
                                sessionMap.remove(activeTab.id)
                            }
                        )
                    }
                }
            }

            if (pendingRedirectUrl != null) {
                AlertDialog(
                    onDismissRequest = { pendingRedirectUrl = null; pendingRedirectTabId = null },
                    title = { Text("⚠️ แจ้งเตือนการเปลี่ยนหน้า") },
                    text = { Text("เว็บนี้กำลังพาคุณไปยังลิงก์อื่น คุณต้องการไปยังลิงก์นี้หรือไม่?\n\n$pendingRedirectUrl") },
                    confirmButton = {
                        TextButton(onClick = {
                            val url = pendingRedirectUrl
                            val tabId = pendingRedirectTabId
                            pendingRedirectUrl = null; pendingRedirectTabId = null
                            if (url != null) {
                                val scheme = try { Uri.parse(url).scheme?.lowercase() ?: "" } catch (e: Exception) { "" }
                                if (scheme == "http" || scheme == "https") tabId?.let { sessionMap[it]?.loadUri(url) }
                                else {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    catch (e: Exception) { Toast.makeText(context, "⚠️ ไม่พบแอปที่รองรับลิงก์นี้", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }) { Text("ตกลงไป") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRedirectUrl = null; pendingRedirectTabId = null }) { Text("บล็อกลิงก์ (ยกเลิก)") }
                    }
                )
            }

            if (showMenu) {
                val menuTab = tabs.getOrNull(currentTab)
                MenuDrawer(
                    isDesktop = menuTab?.isDesktop ?: false,
                    onHome = { menuTab?.url = INTERNAL_HOME_URL; urlBarText = ""; refreshBookmarks(); showMenu = false },
                    onAddTab = { addTab(); showMenu = false },
                    onAddIncognitoTab = { addTab(incognito = true); showMenu = false },
                    onAddBookmark = {
                        val currentUrl = menuTab?.url ?: ""
                        if (currentUrl.isNotBlank() && currentUrl != INTERNAL_HOME_URL) {
                            if (dbHelper.isBookmarked(currentUrl)) Toast.makeText(context, "ℹ️ บุ๊กมาร์กนี้มีอยู่แล้ว", Toast.LENGTH_SHORT).show()
                            else {
                                dbHelper.insertBookmark(currentUrl, menuTab?.title ?: currentUrl, null) // Favicon support needs extraction from GeckoView
                                refreshBookmarks()
                                Toast.makeText(context, "📌 เพิ่มบุ๊กมาร์กเรียบร้อย", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showMenu = false
                    },
                    onOpenHistoryBookmarks = { showDataDialog = true; showMenu = false },
                    onToggleDesktop = {
                        tabs.getOrNull(currentTab)?.let { t ->
                            t.isDesktop = !t.isDesktop
                            sessionMap[t.id]?.settings?.userAgentMode = if (t.isDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                            sessionMap[t.id]?.reload()
                        }
                        persistTabs(); showMenu = false
                    },
                    onAdBlockMenu = { showAdBlockDialog = true; showMenu = false },
                    onSettings = { showSettingsDialog = true; showMenu = false },
                    onClearData = { Toast.makeText(context, "🧹 (Clear Cache ถูกจัดการโดย Runtime)", Toast.LENGTH_SHORT).show(); showMenu = false },
                    onExit = {
                        persistTabs()
                        sessionMap.values.forEach { it.close() }
                        (context as? ComponentActivity)?.finishAffinity()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp)
                )
            }

            if (showTabs) {
                FullScreenTabSwitcher(
                    tabs = tabs, currentTab = currentTab,
                    onSelect = { switchToTab(it) }, onClose = { closeTab(it) }, onAddTab = { addTab() },
                    onAddIncognitoTab = { addTab(incognito = true) }, onDismiss = { showTabs = false }, modifier = Modifier.fillMaxSize().padding(10.dp)
                )
            }

            contextMenuData?.let { data ->
                ContextMenuSheet(
                    data = data,
                    onOpenInNewTab = { url -> addTab(url) },
                    onCopyLink = { url -> copyToClipboard(context, url) },
                    onDownload = { url -> downloadFile(context, url) },
                    onDismiss = { contextMenuData = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (showAdBlockDialog) AdBlockDialog(adBlockEngine, dbHelper) { showAdBlockDialog = false }
            if (showDataDialog) DataManagementDialog(dbHelper, onSelectUrl = { navigate(it) }, onDismiss = { showDataDialog = false; refreshBookmarks() })
            if (showSettingsDialog) SettingsDialog(searchEngine, onSelectEngine = { searchEngine = it; dbHelper.setSetting("search_engine", it.name) }, onDismiss = { showSettingsDialog = false })
        }

        Box {
            BrowserBottomToolbar(
                urlBarText = urlBarText,
                onUrlBarChange = { text ->
                    urlBarText = text
                    if (text.isNotBlank()) { urlSuggestions = dbHelper.searchHistoryAndBookmarks(text); showUrlSuggestions = urlSuggestions.isNotEmpty() } 
                    else showUrlSuggestions = false
                },
                onGo = { navigate(BrowserSecurity.sanitizeUrl(urlBarText, searchEngine)) },
                onBack = { currentSession()?.goBack() },
                onForward = { currentSession()?.goForward() },
                onRefresh = { currentSession()?.reload() },
                onMenu = { showMenu = !showMenu; showTabs = false },
                onTabs = { showTabs = !showTabs; showMenu = false },
                canGoBack = canGoBack, canGoForward = canGoForward, progress = if (tabs.getOrNull(currentTab)?.url == INTERNAL_HOME_URL) 0 else progress
            )

            if (showUrlSuggestions && urlSuggestions.isNotEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).offset(y = (-64).dp), color = Color(0xFF252525), tonalElevation = 8.dp) {
                    Column {
                        urlSuggestions.forEach { (title, url) ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { navigate(BrowserSecurity.sanitizeUrl(url, searchEngine)) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title.ifBlank { url }, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(url, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 7. UI Helpers
// ═══════════════════════════════════════════════════════════
@Composable
fun CustomHomePage(bookmarks: List<BookmarkItem>, onSearch: (String) -> Unit, onSelectBookmark: (String) -> Unit) {
    var searchText by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(40.dp))
        Text("BROWSER", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(30.dp))
        OutlinedTextField(
            value = searchText, onValueChange = { searchText = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            placeholder = { Text("ค้นหาหรือพิมพ์ URL...", color = Color.Gray) }, singleLine = true, shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E), focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF333333)),
            trailingIcon = { IconButton(onClick = { if (searchText.isNotBlank()) onSearch(searchText) }) { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { if (searchText.isNotBlank()) onSearch(searchText) })
        )
        Spacer(modifier = Modifier.height(40.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("บุ๊กมาร์กของคุณ", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (bookmarks.isEmpty()) Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("ยังไม่มีบุ๊กมาร์ก\n(กดเมนูขวาล่างเพื่อเพิ่มหน้าเว็บลงบุ๊กมาร์ก)", color = Color.DarkGray, textAlign = TextAlign.Center, fontSize = 13.sp) }
        else LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(bookmarks) { item -> BookmarkGridItem(item = item, onClick = { onSelectBookmark(item.url) }) }
        }
    }
}
@Composable
fun BookmarkGridItem(item: BookmarkItem, onClick: () -> Unit) {
    val initial = item.title.trim().take(1).uppercase().ifEmpty { "★" }
    val faviconBitmap = remember(item.favicon) { base64ToBitmap(item.favicon) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF252525)) {
            Box(contentAlignment = Alignment.Center) {
                if (faviconBitmap != null) Image(bitmap = faviconBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)))
                else Text(initial, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(item.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
@Composable
fun BrowserBottomToolbar(urlBarText: String, onUrlBarChange: (String) -> Unit, onGo: () -> Unit, onBack: () -> Unit, onForward: () -> Unit, onRefresh: () -> Unit, onMenu: () -> Unit, onTabs: () -> Unit, canGoBack: Boolean, canGoForward: Boolean, progress: Int) {
    Column(modifier = Modifier.background(Color(0xFF1E1E1E))) {
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(38.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (canGoBack) Color.White else Color.DarkGray) }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(38.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = if (canGoForward) Color.White else Color.DarkGray) }
            OutlinedTextField(
                value = urlBarText, onValueChange = onUrlBarChange, modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                placeholder = { Text("ค้นหา...", fontSize = 12.sp, color = Color.Gray) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { onGo() }),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Gray, modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = onGo, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    }
                }
            )
            IconButton(onClick = onTabs, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Layers, contentDescription = "Tabs", tint = Color.White) }
            IconButton(onClick = onMenu, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White) }
        }
    }
}

@Composable
fun MenuDrawer(isDesktop: Boolean, onHome: () -> Unit, onAddTab: () -> Unit, onAddIncognitoTab: () -> Unit, onAddBookmark: () -> Unit, onOpenHistoryBookmarks: () -> Unit, onToggleDesktop: () -> Unit, onAdBlockMenu: () -> Unit, onSettings: () -> Unit, onClearData: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.width(220.dp), color = Color(0xFF252525), shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("🏠 หน้าแรก", Modifier.clickable(onClick = onHome).fillMaxWidth().padding(10.dp), color = Color.White)
            Text("➕ แท็บใหม่", Modifier.clickable(onClick = onAddTab).fillMaxWidth().padding(10.dp), color = Color.White)
            Text("🕶️ แท็บไม่ระบุตัวตน", Modifier.clickable(onClick = onAddIncognitoTab).fillMaxWidth().padding(10.dp), color = Color.White)
            Text("⭐ เพิ่มลงบุ๊กมาร์ก", Modifier.clickable(onClick = onAddBookmark).fillMaxWidth().padding(10.dp), color = Color.White)
            Text("📜 ประวัติ & บุ๊กมาร์ก", Modifier.clickable(onClick = onOpenHistoryBookmarks).fillMaxWidth().padding(10.dp), color = Color.White)
            Text(if (isDesktop) "📱 มุมมองมือถือ" else "💻 มุมมองคอมพิวเตอร์", Modifier.clickable(onClick = onToggleDesktop).fillMaxWidth().padding(10.dp), color = Color.White)
            Text("🛡️ จัดการ AdBlock", Modifier.clickable(onClick = onAdBlockMenu).fillMaxWidth().padding(10.dp), color = Color.White)
            Text("⚙️ ตั้งค่า", Modifier.clickable(onClick = onSettings).fillMaxWidth().padding(10.dp), color = Color.White)
            Text("🧹 ล้างแคช", Modifier.clickable(onClick = onClearData).fillMaxWidth().padding(10.dp), color = Color.White)
            Text("🚪 ออกจากแอป", Modifier.clickable(onClick = onExit).fillMaxWidth().padding(10.dp), color = Color.White)
        }
    }
}
@Composable
fun SettingsDialog(currentEngine: SearchEngine, onSelectEngine: (SearchEngine) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("⚙️ ตั้งค่า") }, text = {
        Column {
            Text("เครื่องมือค้นหาเริ่มต้น", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SearchEngine.values().forEach { engine ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onSelectEngine(engine) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = engine == currentEngine, onClick = { onSelectEngine(engine) })
                    Spacer(Modifier.width(8.dp))
                    Text(engine.label)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } })
}
@Composable
fun DataManagementDialog(dbHelper: NativeBrowserDb, onSelectUrl: (String) -> Unit, onDismiss: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var historyList by remember { mutableStateOf(dbHelper.getAllHistory()) }
    var bookmarkList by remember { mutableStateOf(dbHelper.getAllBookmarks()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("📜 ประวัติ", modifier = Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("⭐ บุ๊กมาร์ก", modifier = Modifier.padding(12.dp)) }
            }
        },
        text = {
            Box(modifier = Modifier.height(350.dp).fillMaxWidth()) {
                if (selectedTab == 0) {
                    if (historyList.isEmpty()) Text("ไม่มีประวัติการท่องเว็บ", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                    else LazyColumn { items(historyList.size) { i -> val item = historyList[i]; val faviconBitmap = remember(item.favicon) { base64ToBitmap(item.favicon) }
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelectUrl(item.url); onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (faviconBitmap != null) { Image(bitmap = faviconBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))); Spacer(Modifier.width(8.dp)) }
                            Column(modifier = Modifier.weight(1f)) { Text(item.title, maxLines = 1, color = Color.White, fontSize = 14.sp); Text(item.url, maxLines = 1, color = Color.Gray, fontSize = 11.sp) }
                            IconButton(onClick = { dbHelper.deleteHistoryById(item.id); historyList = dbHelper.getAllHistory() }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(18.dp)) }
                        }
                        HorizontalDivider(color = Color(0xFF333333)) } }
                } else {
                    if (bookmarkList.isEmpty()) Text("ยังไม่มีบุ๊กมาร์ก", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                    else LazyColumn { items(bookmarkList.size) { i -> val item = bookmarkList[i]; val faviconBitmap = remember(item.favicon) { base64ToBitmap(item.favicon) }
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelectUrl(item.url); onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (faviconBitmap != null) { Image(bitmap = faviconBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))); Spacer(Modifier.width(8.dp)) }
                            Column(modifier = Modifier.weight(1f)) { Text(item.title, maxLines = 1, color = Color.White, fontSize = 14.sp); Text(item.url, maxLines = 1, color = Color.Gray, fontSize = 11.sp) }
                            IconButton(onClick = { dbHelper.deleteBookmarkByUrl(item.url); bookmarkList = dbHelper.getAllBookmarks() }) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(18.dp)) }
                        }
                        HorizontalDivider(color = Color(0xFF333333)) } }
                }

                if (showClearConfirm) {
                    AlertDialog(onDismissRequest = { showClearConfirm = false }, title = { Text("ล้างประวัติทั้งหมด?") }, text = { Text("การกระทำนี้ไม่สามารถย้อนกลับได้") },
                        confirmButton = { TextButton(onClick = { dbHelper.clearAllHistory(); historyList = emptyList(); showClearConfirm = false }) { Text("ยืนยันลบ", color = Color.Red) } },
                        dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("ยกเลิก") } }
                    )
                }
            }
        },
        confirmButton = { if (selectedTab == 0 && historyList.isNotEmpty()) { TextButton(onClick = { showClearConfirm = true }) { Text("🧹 ล้างประวัติทั้งหมด", color = Color.Red) } } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}
@Composable
fun ContextMenuSheet(data: ContextMenuData, onOpenInNewTab: (String) -> Unit, onCopyLink: (String) -> Unit, onDownload: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val isLink = data.type == ContextMenuType.LINK || data.type == ContextMenuType.IMAGE_LINK
    val isImage = data.type == ContextMenuType.IMAGE || data.type == ContextMenuType.IMAGE_LINK
    Surface(modifier = modifier.fillMaxWidth().padding(8.dp), color = Color(0xFF252525), shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = data.url, color = Color.Gray, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(bottom = 12.dp))
            if (isLink) { Text("🔗 เปิดในแท็บใหม่", Modifier.clickable { onOpenInNewTab(data.url); onDismiss() }.fillMaxWidth().padding(12.dp), color = Color.White); Text("📋 คัดลอกลิงก์", Modifier.clickable { onCopyLink(data.url); onDismiss() }.fillMaxWidth().padding(12.dp), color = Color.White) }
            if (isImage) { Text("🖼️ เปิดรูปในแท็บใหม่", Modifier.clickable { onOpenInNewTab(data.url); onDismiss() }.fillMaxWidth().padding(12.dp), color = Color.White); Text("📥 ดาวน์โหลดรูปภาพ", Modifier.clickable { onDownload(data.url); onDismiss() }.fillMaxWidth().padding(12.dp), color = Color.White); if (!isLink) Text("📋 คัดลอกลิงก์รูปภาพ", Modifier.clickable { onCopyLink(data.url); onDismiss() }.fillMaxWidth().padding(12.dp), color = Color.White) }
            Spacer(Modifier.height(8.dp)); Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("ยกเลิก") }
        }
    }
}

@Composable
fun FullScreenTabSwitcher(tabs: List<TabState>, currentTab: Int, onSelect: (Int) -> Unit, onClose: (Int) -> Unit, onAddTab: () -> Unit, onAddIncognitoTab: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (tabs.isEmpty()) return
    val safeInitial = currentTab.coerceIn(0, tabs.size - 1)
    val switcherPagerState = rememberPagerState(initialPage = safeInitial, pageCount = { tabs.size })

    Surface(modifier = modifier, color = Color(0xFF0A0A0A), shape = RoundedCornerShape(20.dp), tonalElevation = 12.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("แท็บทั้งหมด (${tabs.size})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "ปิด", tint = Color.White) }
            }
            Text("ปัดซ้าย/ขวาเพื่อดูแท็บ · แตะเพื่อเปิด", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            HorizontalPager(state = switcherPagerState, modifier = Modifier.weight(1f).fillMaxWidth(), pageSpacing = 12.dp, contentPadding = PaddingValues(horizontal = 28.dp)) { page ->
                val tab = tabs.getOrNull(page)
                if (tab != null) TabPreviewCard(tab = tab, isActive = page == currentTab, modifier = Modifier.fillMaxSize(), onClick = { onSelect(page) }, onClose = { onClose(page) })
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                tabs.indices.forEach { i ->
                    val isCurrentPage = i == switcherPagerState.currentPage
                    Box(modifier = Modifier.padding(horizontal = 3.dp).size(if (isCurrentPage) 8.dp else 6.dp).background(color = if (isCurrentPage) Color.White else Color(0xFF444444), shape = androidx.compose.foundation.shape.CircleShape))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAddTab, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("แท็บใหม่", fontSize = 13.sp) }
                OutlinedButton(onClick = onAddIncognitoTab, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("ไม่ระบุตัวตน", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun TabPreviewCard(tab: TabState, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, onClose: () -> Unit) {
    Surface(modifier = modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick), color = Color(0xFF1B1B1B), border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null, shape = RoundedCornerShape(24.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF262626)), contentAlignment = Alignment.Center) {
                val favicon = tab.favicon
                if (favicon != null) Image(bitmap = favicon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp))
                else Icon(if (tab.url == INTERNAL_HOME_URL) Icons.Default.Home else Icons.Default.Language, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
            }
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (tab.isIncognito) { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(text = tab.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, contentDescription = "ปิดแท็บ", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))).padding(12.dp)) {
                Text(text = if (tab.url == INTERNAL_HOME_URL) "หน้าแรก" else tab.url, color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AdBlockDialog(adBlockEngine: AdBlockEngine, dbHelper: NativeBrowserDb, onDismiss: () -> Unit) {
    var newFilter by remember { mutableStateOf("") }
    var customFilters by remember { mutableStateOf(dbHelper.getCustomFilters()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("🛡️ ระบบ AdBlocker") }, text = {
        Column {
            Text("สถานะ: ${adBlockEngine.statusText}"); Spacer(Modifier.height(8.dp)); Text("กฎทั้งหมด: ${adBlockEngine.ruleCount} รายการ")
            if (adBlockEngine.isUpdating) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF333333)); Spacer(Modifier.height(8.dp)); Text("เพิ่มโดเมนที่ต้องการบล็อกเอง", fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newFilter, onValueChange = { newFilter = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("เช่น ads.example.com", fontSize = 12.sp) }, textStyle = TextStyle(fontSize = 13.sp))
                IconButton(onClick = { val domain = newFilter.trim(); if (domain.isNotBlank()) { dbHelper.addCustomFilter(domain); customFilters = dbHelper.getCustomFilters(); adBlockEngine.refreshCustomFiltersOnly(); newFilter = "" } }) { Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary) }
            }
            if (customFilters.isNotEmpty()) {
                Spacer(Modifier.height(8.dp)); Box(modifier = Modifier.heightIn(max = 120.dp)) {
                    LazyColumn { items(customFilters.size) { i -> val domain = customFilters[i]
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(domain, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { dbHelper.removeCustomFilter(domain); customFilters = dbHelper.getCustomFilters(); adBlockEngine.refreshCustomFiltersOnly() }) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(16.dp)) } }
                    } }
                }
            }
        }
    }, confirmButton = { Button(onClick = { adBlockEngine.updateFromNetwork() }, enabled = !adBlockEngine.isUpdating) { Text(if (adBlockEngine.isUpdating) "กำลังอัปเดต..." else "🔄 อัปเดตกฎบล็อก") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } })
}

// ═══════════════════════════════════════════════════════════
// 9. GeckoView Core Engine Replacement
// ═══════════════════════════════════════════════════════════
@Composable
fun CleanGeckoView(
    initialUrl: String,
    savedState: GeckoSession.SessionState?,
    isDesktop: Boolean,
    isIncognito: Boolean,
    adBlockEngine: AdBlockEngine,
    dbHelper: NativeBrowserDb,
    onSessionCreated: (GeckoSession) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavStateChanged: (Boolean, Boolean) -> Unit,
    onLongPress: (ContextMenuData) -> Unit,
    onConfirmRedirect: (String) -> Unit,
    onCrashed: () -> Unit = {},
    onSuspend: (GeckoSession.SessionState?, Bitmap?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var customVideoView by remember { mutableStateOf<View?>(null) }
    
    // File Chooser state
    var pendingFilePrompt by remember { mutableStateOf<GeckoSession.PromptDelegate.FilePrompt?>(null) }
    var pendingFileResult by remember { mutableStateOf<GeckoResult<GeckoSession.PromptDelegate.PromptResponse>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null && pendingFilePrompt != null) {
            pendingFileResult?.complete(pendingFilePrompt?.confirm(context, uri))
        } else {
            pendingFileResult?.complete(pendingFilePrompt?.dismiss())
        }
        pendingFilePrompt = null
        pendingFileResult = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.White),
            factory = { ctx ->
                val geckoView = GeckoView(ctx)
                
                // Gecko Session Setup
                val settings = GeckoSessionSettings.Builder()
                    .usePrivateMode(isIncognito)
                    .userAgentMode(if (isDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                    .suspendMediaWhenInactive(true)
                    .allowJavascript(true)
                    .build()

                val session = GeckoSession(settings)
                
                // --- 1. Progress Delegate ---
                session.progressDelegate = object : GeckoSession.ProgressDelegate {
                    override fun onPageStart(session: GeckoSession, url: String) {
                        onProgressChanged(10)
                    }

                    override fun onPageStop(session: GeckoSession, success: Boolean) {
                        onProgressChanged(100)
                    }

                    override fun onProgressChange(session: GeckoSession, progress: Int) {
                        onProgressChanged(progress)
                    }

                    override fun onSessionCrash(session: GeckoSession) {
                        Toast.makeText(ctx, "⚠️ หน้าเว็บมีปัญหา", Toast.LENGTH_SHORT).show()
                        onCrashed()
                    }
                }

                // --- 2. Content Delegate (Title, Context Menu, FullScreen) ---
                session.contentDelegate = object : GeckoSession.ContentDelegate {
                    override fun onTitleChange(session: GeckoSession, title: String?) {
                        onTitleChanged(title)
                    }

                    override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
                        val type = when {
                            element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE && element.linkUri != null -> ContextMenuType.IMAGE_LINK
                            element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE -> ContextMenuType.IMAGE
                            element.linkUri != null -> ContextMenuType.LINK
                            else -> ContextMenuType.UNKNOWN
                        }
                        val url = element.linkUri ?: element.srcUri ?: ""
                        if (url.isNotEmpty() && type != ContextMenuType.UNKNOWN) {
                            onLongPress(ContextMenuData(type, url))
                        }
                    }

                    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                        if (fullScreen) {
                            // Needs a dedicated view for video. Using GeckoView's internal system if possible, 
                            // or leave blank to fallback to built-in behavior.
                        } else {
                            customVideoView = null
                        }
                    }
                }

                // --- 3. Navigation Delegate (URL intercept, Adblock) ---
                session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                    override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                        val urlStr = request.uri

                        // Adblock (Top-Level & IFrame only, resource requests require WebExtensions)
                        if (adBlockEngine.isAd(urlStr)) {
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }

                        if (BrowserSecurity.isDangerous(urlStr)) {
                            Toast.makeText(ctx, "🚫 บล็อกลิงก์ที่ไม่ปลอดภัย", Toast.LENGTH_SHORT).show()
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }

                        val scheme = try { Uri.parse(urlStr).scheme?.lowercase() ?: "" } catch (e: Exception) { "" }
                        val isHttp = scheme == "http" || scheme == "https"

                        if (!isHttp) {
                            onConfirmRedirect(urlStr)
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }

                        // App-level domain redirect checks can be placed here if needed
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }

                    override fun onLocationChange(session: GeckoSession, url: String?, perms: List<GeckoSession.PermissionDelegate.ContentPermission>?) {
                        super.onLocationChange(session, url, perms)
                        if (url != null) {
                            onUrlChanged(url)
                            if (!isIncognito && url != INTERNAL_HOME_URL) {
                                thread { dbHelper.insertHistory(url, "Web Page") }
                            }
                        }
                    }

                    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                        onNavStateChanged(canGoBack, session.canGoForward)
                    }

                    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                        onNavStateChanged(session.canGoBack, canGoForward)
                    }
                }

                // --- 4. Prompt Delegate (File Picker) ---
                session.promptDelegate = object : GeckoSession.PromptDelegate {
                    override fun onFilePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.FilePrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                        pendingFilePrompt = prompt
                        pendingFileResult = result

                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                        try {
                            filePickerLauncher.launch(intent)
                        } catch (e: Exception) {
                            result.complete(prompt.dismiss())
                        }
                        return result
                    }
                }

                session.open(GeckoEngine.getRuntime(ctx))
                geckoView.setSession(session)

                if (savedState != null) {
                    session.restoreState(savedState)
                } else if (initialUrl != INTERNAL_HOME_URL) {
                    session.loadUri(initialUrl)
                }

                onSessionCreated(session)
                geckoView
            },
            update = { view ->
                // Switch Desktop Mode Dynamically
                view.session?.settings?.userAgentMode = if (isDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            },
            onRelease = { view ->
                val state = view.session?.saveState()
                onSuspend(state, null) // GeckoView snapshot is asynchronous, omitting here for simplicity
                view.session?.close()
                view.releaseSession()
            }
        )

        customVideoView?.let { videoView ->
            AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize().background(Color.Black))
        }
    }
}
