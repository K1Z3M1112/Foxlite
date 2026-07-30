package com.mybrowser.app

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
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.concurrent.thread

// Constants
const val INTERNAL_HOME_URL = "about:blank"
const val MAX_TABS = 15

// ═══════════════════════════════════════════════════════════
// 0. Design Tokens — ชุดสีและระยะกลางของแอป (Foxlite look)
// ═══════════════════════════════════════════════════════════
object FoxColors {
    val Background = Color(0xFF0F0F12)
    val Surface = Color(0xFF1A1A1E)
    val SurfaceElevated = Color(0xFF232327)
    val SurfaceHigh = Color(0xFF2C2C31)
    val Outline = Color(0xFF35353B)
    val OnSurface = Color(0xFFF2F1EF)
    val OnSurfaceMuted = Color(0xFFA0A0A8)
    val OnSurfaceFaint = Color(0xFF6E6E76)

    // สีแบรนด์: ส้มจิ้งจอก (Fox) เป็นสีหลัก, ใช้ทั้งแอปแทนสีสุ่ม
    val Accent = Color(0xFFFF7A3D)
    val AccentMuted = Color(0xFFFFA773)
    val AccentContainer = Color(0xFF3A2416)

    val Incognito = Color(0xFF8B5CF6)
    val IncognitoContainer = Color(0xFF2A1F3D)
    val Danger = Color(0xFFEF5350)
    val Success = Color(0xFF4CAF7D)
}

val FoxShapeSmall = RoundedCornerShape(10.dp)
val FoxShapeMedium = RoundedCornerShape(16.dp)
val FoxShapeLarge = RoundedCornerShape(24.dp)
val FoxShapePill = RoundedCornerShape(50)

// ═══════════════════════════════════════════════════════════
// 1. GeckoRuntime Singleton Manager
// ═══════════════════════════════════════════════════════════
object GeckoRuntimeManager {
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        if (runtime == null) {
            val created = GeckoRuntime.create(context.applicationContext)
            // สำคัญ: ถ้าไม่ตั้งค่า PromptDelegate, GeckoView จะไม่มีใครตอบรับ
            // คำขอสิทธิ์ตอนติดตั้งส่วนขยาย และจะปฏิเสธการติดตั้งไปเงียบๆ (ค่า default = DENY)
            created.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
                override fun onInstallPrompt(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>
                ): GeckoResult<AllowOrDeny>? {
                    // ผู้ใช้กดติดตั้งเองจากร้านค้าในแอปอยู่แล้ว จึงอนุมัติสิทธิ์ให้อัตโนมัติ
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
            }
            runtime = created
        }
        return runtime!!
    }
}

// ═══════════════════════════════════════════════════════════
// 2. Data Models & Database
// ═══════════════════════════════════════════════════════════
data class HistoryItem(val id: Long, val url: String, val title: String, val timestamp: Long, val favicon: String?)
data class BookmarkItem(val id: Long, val url: String, val title: String, val timestamp: Long, val favicon: String?)
data class SavedTab(val id: String, val url: String, val title: String, val isDesktop: Boolean, val position: Int)

data class StoreExtensionItem(
    val name: String,
    val description: String,
    val downloadUrl: String,
    val iconUrl: String = ""
)

// ข้อมูลส่วนขยายแนะนำสำหรับ Extension Store
val RECOMMENDED_EXTENSIONS = listOf(
    StoreExtensionItem(
        name = "uBlock Origin",
        description = "ตัวบล็อกโฆษณา สคริปต์ และสปายแวร์ที่มีประสิทธิภาพสูง",
        downloadUrl = "https://addons.mozilla.org/firefox/downloads/file/4264223/ublock_origin-1.57.0.xpi"
    ),
    StoreExtensionItem(
        name = "Dark Reader",
        description = "เปลี่ยนทุกเว็บไซต์ให้เป็นโหมดมืด (Dark Mode) เพื่อถนอมสายตา",
        downloadUrl = "https://addons.mozilla.org/firefox/downloads/file/4258849/darkreader-4.9.81.xpi"
    ),
    StoreExtensionItem(
        name = "SponsorBlock",
        description = "ข้ามโฆษณาและช่วงสปอนเซอร์ในวิดีโอ YouTube อัตโนมัติ",
        downloadUrl = "https://addons.mozilla.org/firefox/downloads/file/4231842/sponsorblock-5.5.3.xpi"
    ),
    StoreExtensionItem(
        name = "Privacy Badger",
        description = "บล็อกตัวแกะรอย (Trackers) ที่แอบติดตามพฤติกรรมของคุณ",
        downloadUrl = "https://addons.mozilla.org/firefox/downloads/file/4221772/privacy_badger17-2024.1.25.xpi"
    )
)

class NativeBrowserDb(context: Context) : SQLiteOpenHelper(context, "browser_gecko.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, title TEXT, timestamp INTEGER, favicon TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, title TEXT, timestamp INTEGER, favicon TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tabs (id TEXT PRIMARY KEY, url TEXT, title TEXT, is_desktop INTEGER, position INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { onCreate(db) }

    fun insertHistory(url: String, title: String, favicon: String? = null) {
        if (url == INTERNAL_HOME_URL || url.isBlank()) return
        try {
            val values = ContentValues().apply {
                put("url", url); put("title", title); put("timestamp", System.currentTimeMillis()); put("favicon", favicon)
            }
            writableDatabase.insert("history", null, values)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getAllHistory(): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        try {
            readableDatabase.rawQuery("SELECT id, url, title, timestamp, favicon FROM history ORDER BY timestamp DESC LIMIT 200", null).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(HistoryItem(cursor.getLong(0), cursor.getString(1) ?: "", cursor.getString(2) ?: "", cursor.getLong(3), cursor.getString(4)))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun searchHistoryAndBookmarks(query: String, limit: Int = 6): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        if (query.isBlank()) return results
        try {
            val like = "%${query.trim()}%"
            readableDatabase.rawQuery(
                "SELECT title, url FROM (SELECT title, url, timestamp FROM bookmarks WHERE url LIKE ? OR title LIKE ? UNION ALL SELECT title, url, timestamp FROM history WHERE url LIKE ? OR title LIKE ?) ORDER BY timestamp DESC LIMIT ?",
                arrayOf(like, like, like, like, limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) { results.add((cursor.getString(0) ?: "") to (cursor.getString(1) ?: "")) }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return results.distinctBy { it.second }
    }

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
                while (cursor.moveToNext()) { list.add(BookmarkItem(cursor.getLong(0), cursor.getString(1) ?: "", cursor.getString(2) ?: "", cursor.getLong(3), cursor.getString(4))) }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun deleteBookmarkByUrl(url: String) { try { writableDatabase.delete("bookmarks", "url=?", arrayOf(url)) } catch (e: Exception) {} }
    fun deleteHistoryById(id: Long) { try { writableDatabase.delete("history", "id=?", arrayOf(id.toString())) } catch (e: Exception) {} }
    fun clearAllHistory() { try { writableDatabase.delete("history", null, null) } catch (e: Exception) {} }

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
                while (cursor.moveToNext()) {
                    list.add(SavedTab(cursor.getString(0) ?: "", cursor.getString(1) ?: INTERNAL_HOME_URL, cursor.getString(2) ?: "หน้าแรก", cursor.getInt(3) == 1, cursor.getInt(4)))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun getSetting(key: String, default: String): String {
        try { readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) return it.getString(0) ?: default } } catch (e: Exception) {}
        return default
    }

    fun setSetting(key: String, value: String) {
        try { val values = ContentValues().apply { put("key", key); put("value", value) }; writableDatabase.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE) } catch (e: Exception) {}
    }
}

// ═══════════════════════════════════════════════════════════
// 3. Tab State with GeckoSession
// ═══════════════════════════════════════════════════════════
class TabState(
    val id: String = UUID.randomUUID().toString(),
    url: String,
    title: String = "หน้าแรก",
    isDesktop: Boolean = false,
    val isIncognito: Boolean = false
) {
    var url by mutableStateOf(url)
    var title by mutableStateOf(title)
    var isDesktop by mutableStateOf(isDesktop)
    var favicon by mutableStateOf<Bitmap?>(null)
    var geckoSession by mutableStateOf<GeckoSession?>(null)

    fun createOrGetSession(context: Context): GeckoSession {
        if (geckoSession == null) {
            val runtime = GeckoRuntimeManager.get(context)
            val settings = GeckoSessionSettings.Builder()
                .usePrivateMode(isIncognito)
                .userAgentMode(if (isDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .build()

            val session = GeckoSession(settings)
            session.open(runtime)
            if (url != INTERNAL_HOME_URL) {
                session.loadUri(url)
            }
            geckoSession = session
        }
        return geckoSession!!
    }

    fun closeSession() {
        geckoSession?.close()
        geckoSession = null
    }
}

enum class SearchEngine(val label: String, val urlPrefix: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave", "https://search.brave.com/search?q=")
}

object BrowserSecurity {
    private val DOMAIN_REGEX = Regex("^(https?://)?([\\w-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?(/.*)?$")

    fun sanitizeUrl(input: String?, engine: SearchEngine = SearchEngine.GOOGLE): String {
        if (input.isNullOrBlank()) return INTERNAL_HOME_URL
        var s = input.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            val looksLikeDomain = !s.contains(" ") && DOMAIN_REGEX.matches(s)
            s = if (looksLikeDomain) "https://$s" else engine.urlPrefix + Uri.encode(s)
        }
        return s
    }
}

// ═══════════════════════════════════════════════════════════
// ระบบขออนุญาตนำทาง / กันฉีด URL ก่อนอนุญาตให้โหลดหรือ redirect
// ═══════════════════════════════════════════════════════════
object NavigationGuard {
    // scheme ที่อนุญาตให้เปิดได้ตามปกติ
    private val ALLOWED_SCHEMES = setOf("http", "https", "about", "blob", "data")

    // scheme ที่มักถูกใช้เป็นช่องทางโจมตี (JS injection, intent hijacking, local-file/content leak)
    private val BLOCKED_SCHEMES = setOf(
        "javascript", "intent", "file", "content", "android-app", "chrome", "resource"
    )

    data class Verdict(val allowed: Boolean, val reason: String? = null)

    fun evaluate(rawUri: String?): Verdict {
        if (rawUri.isNullOrBlank()) return Verdict(true)
        val scheme = try { Uri.parse(rawUri).scheme?.lowercase() } catch (e: Exception) { null }
        return when {
            scheme == null -> Verdict(true)
            scheme in BLOCKED_SCHEMES -> Verdict(false, "บล็อกลิงก์ที่อาจไม่ปลอดภัย ($scheme://)")
            scheme in ALLOWED_SCHEMES -> Verdict(true)
            else -> Verdict(false, "บล็อก scheme ที่ไม่รู้จัก ($scheme://)")
        }
    }

    fun isSafe(rawUri: String?): Boolean = evaluate(rawUri).allowed
}

object PinSecurity {
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var data = (salt + pin).toByteArray(Charsets.UTF_8)
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
    return try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }
}

// ═══════════════════════════════════════════════════════════
// 4. Activity Entry Point
// ═══════════════════════════════════════════════════════════
class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("BrowserGecko", Context.MODE_PRIVATE)

        setContent {
            val foxColorScheme = darkColorScheme(
                primary = FoxColors.Accent,
                onPrimary = FoxColors.OnSurface,
                primaryContainer = FoxColors.AccentContainer,
                onPrimaryContainer = FoxColors.AccentMuted,
                secondary = FoxColors.Incognito,
                background = FoxColors.Background,
                onBackground = FoxColors.OnSurface,
                surface = FoxColors.Surface,
                onSurface = FoxColors.OnSurface,
                surfaceVariant = FoxColors.SurfaceElevated,
                onSurfaceVariant = FoxColors.OnSurfaceMuted,
                outline = FoxColors.Outline,
                error = FoxColors.Danger
            )
            MaterialTheme(colorScheme = foxColorScheme) {
                BrowserApp(prefs = prefs, onExitApp = { finishAffinity() })
            }
        }
    }
}

@Composable
fun BrowserApp(prefs: SharedPreferences, onExitApp: () -> Unit) {
    var unlocked by rememberSaveable { mutableStateOf(false) }

    if (!unlocked) {
        PasswordGateDialog(prefs = prefs, onUnlocked = { unlocked = true }, onExit = onExitApp)
    } else {
        BrowserScreen(prefs = prefs)
    }
}

@Composable
fun PasswordGateDialog(prefs: SharedPreferences, onUnlocked: () -> Unit, onExit: () -> Unit) {
    val savedHash = remember { prefs.getString("app_pin_hash", "") ?: "" }
    val savedSalt = remember { prefs.getString("app_pin_salt", "") ?: "" }
    val isSetupMode = savedHash.isEmpty()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text(if (isSetupMode) "🆕 ตั้งรหัสผ่านของคุณ" else "🔐 ยืนยันรหัสผ่าน") },
        text = {
            Column {
                Text(if (isSetupMode) "กรุณากำหนด PIN เพื่อเข้าใช้งาน" else "กรุณากรอกรหัสผ่านเพื่อเข้าสู่ระบบ")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it; error = null },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val entered = pin.trim()
                    if (entered.length < 4) { error = "❌ PIN ต้องมีอย่างน้อย 4 หลัก"; return@TextButton }
                    if (isSetupMode) {
                        val newSalt = PinSecurity.generateSalt()
                        val newHash = PinSecurity.hash(entered, newSalt)
                        prefs.edit().putString("app_pin_hash", newHash).putString("app_pin_salt", newSalt).apply()
                        onUnlocked()
                    } else if (PinSecurity.verify(entered, savedSalt, savedHash)) {
                        onUnlocked()
                    } else {
                        error = "❌ รหัสผ่านไม่ถูกต้อง"
                        pin = ""
                    }
                }
            ) { Text("ตกลง") }
        },
        dismissButton = { TextButton(onClick = onExit) { Text("ออก") } }
    )
}

// ═══════════════════════════════════════════════════════════
// 5. Main Browser Screen
// ═══════════════════════════════════════════════════════════
@Composable
fun BrowserScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val dbHelper = remember { NativeBrowserDb(context) }
    val geckoRuntime = remember { GeckoRuntimeManager.get(context) }

    var showExtensionStoreDialog by remember { mutableStateOf(false) }
    var showDataDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var searchEngine by remember {
        mutableStateOf(SearchEngine.valueOf(dbHelper.getSetting("search_engine", SearchEngine.GOOGLE.name)))
    }

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

    var urlBarText by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showUrlSuggestions by remember { mutableStateOf(false) }
    var urlSuggestions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    var bookmarksList by remember { mutableStateOf(dbHelper.getAllBookmarks()) }
    fun refreshBookmarks() { bookmarksList = dbHelper.getAllBookmarks() }

    // ค้นหาแบบ debounce: รอให้ผู้ใช้หยุดพิมพ์ก่อนค่อย query SQLite ในเธรดพื้นหลัง
    // ป้องกันอาการกระตุกขณะพิมพ์ในช่อง URL bar
    LaunchedEffect(urlBarText) {
        val query = urlBarText
        if (query.isBlank()) {
            showUrlSuggestions = false
            return@LaunchedEffect
        }
        delay(200)
        val results = withContext(Dispatchers.IO) { dbHelper.searchHistoryAndBookmarks(query) }
        urlSuggestions = results
        showUrlSuggestions = results.isNotEmpty()
    }

    fun navigate(target: String) {
        val verdict = NavigationGuard.evaluate(target)
        if (!verdict.allowed) {
            Toast.makeText(context, "🚫 ${verdict.reason}", Toast.LENGTH_SHORT).show()
            return
        }
        val tab = tabs.getOrNull(currentTab) ?: return
        tab.url = target
        val session = tab.createOrGetSession(context)
        session.loadUri(target)
        showUrlSuggestions = false
    }

    fun switchToTab(newIndex: Int) {
        if (newIndex !in tabs.indices) return
        currentTab = newIndex
        showTabs = false

        val target = tabs[newIndex]
        urlBarText = if (target.url != INTERNAL_HOME_URL) target.url else ""
        refreshBookmarks()
    }

    fun addTab(url: String = INTERNAL_HOME_URL, incognito: Boolean = false) {
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(context, "⚠️ เปิดได้สูงสุด $MAX_TABS แท็บ", Toast.LENGTH_SHORT).show()
            return
        }
        val newTab = TabState(url = url, isIncognito = incognito)
        tabs.add(newTab)
        switchToTab(tabs.size - 1)
        persistTabs()
    }

    fun closeTab(idx: Int) {
        if (tabs.size <= 1 || idx !in tabs.indices) return
        val closedTab = tabs[idx]
        closedTab.closeSession()
        tabs.removeAt(idx)

        val newCurrentIndex = currentTab.coerceAtMost(tabs.size - 1)
        switchToTab(newCurrentIndex)
        persistTabs()
    }

    BackHandler(enabled = true) {
        when {
            showUrlSuggestions -> showUrlSuggestions = false
            showMenu -> showMenu = false
            showTabs -> showTabs = false
            showDataDialog -> showDataDialog = false
            showSettingsDialog -> showSettingsDialog = false
            showExtensionStoreDialog -> showExtensionStoreDialog = false
            canGoBack -> tabs.getOrNull(currentTab)?.geckoSession?.goBack()
            else -> (context as? ComponentActivity)?.finish()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(FoxColors.Background)) {
        if (tabs.getOrNull(currentTab)?.isIncognito == true) {
            Surface(color = FoxColors.IncognitoContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "🕶️ โหมดไม่ระบุตัวตน (Gecko Engine)", color = FoxColors.OnSurface,
                    fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            val activeTab = tabs.getOrNull(currentTab)
            if (activeTab != null) {
                if (activeTab.url == INTERNAL_HOME_URL) {
                    CustomHomePage(
                        bookmarks = bookmarksList,
                        onSearch = { query ->
                            val target = BrowserSecurity.sanitizeUrl(query, searchEngine)
                            urlBarText = target
                            navigate(target)
                        },
                        onSelectBookmark = { url ->
                            urlBarText = url
                            navigate(url)
                        }
                    )
                } else {
                    CleanGeckoView(
                        tabState = activeTab,
                        onUrlChanged = { url ->
                            if (url != INTERNAL_HOME_URL) {
                                activeTab.url = url
                                urlBarText = url
                                persistTabs()
                            }
                        },
                        onTitleChanged = { title ->
                            activeTab.title = if (activeTab.url == INTERNAL_HOME_URL) "หน้าแรก" else title ?: "Web Page"
                            persistTabs()
                            if (!activeTab.isIncognito && activeTab.url != INTERNAL_HOME_URL) {
                                val urlSnapshot = activeTab.url
                                val titleSnapshot = activeTab.title
                                thread { dbHelper.insertHistory(urlSnapshot, titleSnapshot) }
                            }
                        },
                        onProgressChanged = { p -> progress = p },
                        onNavStateChanged = { back, fwd -> canGoBack = back; canGoForward = fwd },
                        onBlockedNavigation = { _, reason ->
                            Toast.makeText(context, "🚫 $reason", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
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
                        val currentTitle = menuTab?.title ?: currentUrl
                        if (currentUrl.isNotBlank() && currentUrl != INTERNAL_HOME_URL) {
                            if (dbHelper.isBookmarked(currentUrl)) {
                                Toast.makeText(context, "ℹ️ บุ๊กมาร์กนี้มีอยู่แล้ว", Toast.LENGTH_SHORT).show()
                            } else {
                                dbHelper.insertBookmark(currentUrl, currentTitle)
                                refreshBookmarks()
                                Toast.makeText(context, "📌 เพิ่มบุ๊กมาร์กเรียบร้อย", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showMenu = false
                    },
                    onOpenHistoryBookmarks = { showDataDialog = true; showMenu = false },
                    onToggleDesktop = {
                        menuTab?.let { t ->
                            t.isDesktop = !t.isDesktop
                            t.geckoSession?.settings?.userAgentMode = if (t.isDesktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                            t.geckoSession?.reload()
                        }
                        persistTabs(); showMenu = false
                    },
                    onExtensionStore = { showExtensionStoreDialog = true; showMenu = false },
                    onSettings = { showSettingsDialog = true; showMenu = false },
                    onExit = {
                        persistTabs()
                        tabs.forEach { it.closeSession() }
                        (context as? ComponentActivity)?.finishAffinity()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp)
                )
            }

            if (showTabs) {
                FullScreenTabSwitcher(
                    tabs = tabs,
                    currentTab = currentTab,
                    onSelect = { switchToTab(it) },
                    onClose = { closeTab(it) },
                    onAddTab = { addTab() },
                    onAddIncognitoTab = { addTab(incognito = true) },
                    onDismiss = { showTabs = false },
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                )
            }

            if (showExtensionStoreDialog) {
                ExtensionStoreDialog(geckoRuntime = geckoRuntime) { showExtensionStoreDialog = false }
            }
            if (showDataDialog) DataManagementDialog(dbHelper, onSelectUrl = { navigate(it) }, onDismiss = { showDataDialog = false; refreshBookmarks() })
            if (showSettingsDialog) SettingsDialog(searchEngine, onSelectEngine = { searchEngine = it; dbHelper.setSetting("search_engine", it.name) }, onDismiss = { showSettingsDialog = false })
        }

        Box {
            BrowserBottomToolbar(
                urlBarText = urlBarText,
                onUrlBarChange = { text -> urlBarText = text },
                onGo = { navigate(BrowserSecurity.sanitizeUrl(urlBarText, searchEngine)) },
                onBack = { tabs.getOrNull(currentTab)?.geckoSession?.goBack() },
                onForward = { tabs.getOrNull(currentTab)?.geckoSession?.goForward() },
                onRefresh = { tabs.getOrNull(currentTab)?.geckoSession?.reload() },
                onMenu = { showMenu = !showMenu; showTabs = false },
                onTabs = { showTabs = !showTabs; showMenu = false },
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                progress = if (tabs.getOrNull(currentTab)?.url == INTERNAL_HOME_URL) 0 else progress
            )

            if (showUrlSuggestions && urlSuggestions.isNotEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).offset(y = (-64).dp), color = FoxColors.SurfaceElevated, tonalElevation = 8.dp) {
                    Column {
                        urlSuggestions.forEach { (title, url) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { navigate(BrowserSecurity.sanitizeUrl(url, searchEngine)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = FoxColors.OnSurfaceMuted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title.ifBlank { url }, color = FoxColors.OnSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(url, color = FoxColors.OnSurfaceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            HorizontalDivider(color = FoxColors.Outline)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 6. Clean GeckoView Compose Component
// ═══════════════════════════════════════════════════════════
@Composable
fun CleanGeckoView(
    tabState: TabState,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavStateChanged: (Boolean, Boolean) -> Unit,
    onBlockedNavigation: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val session = remember(tabState.id) { tabState.createOrGetSession(context) }

    DisposableEffect(session) {
        val progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                onProgressChanged(10)
                onUrlChanged(url)
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                onProgressChanged(100)
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                onProgressChanged(progress)
            }
        }

        val navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, permissions: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                url?.let { onUrlChanged(it) }
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                onNavStateChanged(canGoBack, false)
            }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                onNavStateChanged(false, canGoForward)
            }
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val verdict = NavigationGuard.evaluate(request.uri)
                if (!verdict.allowed) {
                    onBlockedNavigation(request.uri, verdict.reason ?: "ลิงก์นี้ถูกบล็อกเพื่อความปลอดภัย")
                    return GeckoResult.deny()
                }
                return GeckoResult.allow()
            }
        }

        val contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                onTitleChanged(title)
            }
        }

        session.progressDelegate = progressDelegate
        session.navigationDelegate = navigationDelegate
        session.contentDelegate = contentDelegate

        onDispose {
            session.progressDelegate = null
            session.navigationDelegate = null
            session.contentDelegate = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            GeckoView(ctx).apply {
                setSession(session)
            }
        },
        update = { geckoView ->
            if (geckoView.session != session) {
                geckoView.setSession(session)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// 7. WebExtension Store & Add-on Manager Dialog
// ═══════════════════════════════════════════════════════════
@Composable
fun ExtensionStoreDialog(geckoRuntime: GeckoRuntime, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var installedExtensions by remember { mutableStateOf<List<WebExtension>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var customXpiUrl by remember { mutableStateOf("") }

    fun refreshInstalled() {
        geckoRuntime.webExtensionController.list().then({ exts ->
            installedExtensions = exts ?: emptyList()
            GeckoResult<Void>()
        }, { null })
    }

    LaunchedEffect(Unit) {
        refreshInstalled()
    }

    fun installFromUrl(url: String) {
        if (url.isBlank()) return
        isLoading = true
        geckoRuntime.webExtensionController.install(url).then({ ext ->
            isLoading = false
            Toast.makeText(context, "✅ ติดตั้งส่วนขยาย ${ext?.metaData?.name ?: ""} สำเร็จ", Toast.LENGTH_SHORT).show()
            refreshInstalled()
            GeckoResult<Void>()
        }, { throwable ->
            isLoading = false
            Toast.makeText(context, "❌ ติดตั้งล้มเหลว: ${throwable.message}", Toast.LENGTH_LONG).show()
            GeckoResult<Void>()
        })
    }

    fun uninstallExt(ext: WebExtension) {
        geckoRuntime.webExtensionController.uninstall(ext).then({
            Toast.makeText(context, "🗑️ ถอนการติดตั้งแล้ว", Toast.LENGTH_SHORT).show()
            refreshInstalled()
            GeckoResult<Void>()
        }, { null })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("🧩 ร้านค้าส่วนขยาย (WebExtensions)")
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("🏪 ร้านค้า", modifier = Modifier.padding(10.dp)) }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("📦 ติดตั้งแล้ว (${installedExtensions.size})", modifier = Modifier.padding(10.dp)) }
                }
            }
        },
        text = {
            Box(modifier = Modifier.height(380.dp).fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (selectedTab == 0) {
                    Column {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(RECOMMENDED_EXTENSIONS, key = { it.name }) { item ->
                                val isInstalled = installedExtensions.any { it.id == item.name || it.metaData.name == item.name }
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = FoxShapeMedium,
                                    colors = CardDefaults.cardColors(containerColor = FoxColors.SurfaceHigh)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Extension, contentDescription = null, tint = FoxColors.Accent, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(item.name, color = FoxColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(item.description, color = FoxColors.OnSurfaceMuted, fontSize = 12.sp)
                                        Spacer(Modifier.height(10.dp))
                                        Button(
                                            onClick = { installFromUrl(item.downloadUrl) },
                                            enabled = !isInstalled,
                                            shape = FoxShapeSmall,
                                            colors = ButtonDefaults.buttonColors(containerColor = FoxColors.Accent, disabledContainerColor = FoxColors.Outline),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(if (isInstalled) "ติดตั้งแล้ว" else "ติดตั้งส่วนขยาย", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("ติดตั้งจากลิงก์ .XPI โดยตรง:", fontSize = 12.sp, color = FoxColors.OnSurfaceMuted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customXpiUrl,
                                onValueChange = { customXpiUrl = it },
                                placeholder = { Text("https://.../extension.xpi", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { installFromUrl(customXpiUrl); customXpiUrl = "" }) {
                                Icon(Icons.Default.Download, contentDescription = "Install", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    if (installedExtensions.isEmpty()) {
                        Text("ยังไม่มีส่วนขยายที่ติดตั้ง", modifier = Modifier.align(Alignment.Center), color = FoxColors.OnSurfaceMuted)
                    } else {
                        LazyColumn {
                            items(installedExtensions, key = { it.id }) { ext ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Extension, contentDescription = null, tint = FoxColors.Accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ext.metaData.name ?: ext.id, color = FoxColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("เวอร์ชัน: ${ext.metaData.version ?: "1.0"}", color = FoxColors.OnSurfaceMuted, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { uninstallExt(ext) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "ถอนการติดตั้ง", tint = FoxColors.Danger, modifier = Modifier.size(19.dp))
                                    }
                                }
                                HorizontalDivider(color = FoxColors.Outline)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}

// ═══════════════════════════════════════════════════════════
// 8. Other UI Components
// ═══════════════════════════════════════════════════════════
@Composable
fun CustomHomePage(bookmarks: List<BookmarkItem>, onSearch: (String) -> Unit, onSelectBookmark: (String) -> Unit) {
    var searchText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().background(FoxColors.Background).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))
        Surface(modifier = Modifier.size(56.dp), shape = FoxShapeLarge, color = FoxColors.AccentContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Language, contentDescription = null, tint = FoxColors.Accent, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text("FOXLITE", color = FoxColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedTextField(
            value = searchText, onValueChange = { searchText = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ค้นหาหรือพิมพ์ URL...", color = FoxColors.OnSurfaceFaint, fontSize = 14.sp) },
            singleLine = true, shape = FoxShapePill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FoxColors.SurfaceElevated,
                unfocusedContainerColor = FoxColors.SurfaceElevated,
                focusedBorderColor = FoxColors.Accent,
                unfocusedBorderColor = FoxColors.Outline,
                focusedTextColor = FoxColors.OnSurface,
                unfocusedTextColor = FoxColors.OnSurface,
                cursorColor = FoxColors.Accent
            ),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = FoxColors.OnSurfaceMuted, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchText.isNotBlank()) {
                    IconButton(onClick = { onSearch(searchText) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ไป", tint = FoxColors.Accent, modifier = Modifier.size(18.dp))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (searchText.isNotBlank()) onSearch(searchText) })
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bookmark, contentDescription = null, tint = FoxColors.OnSurfaceMuted, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("บุ๊กมาร์กของคุณ", color = FoxColors.OnSurfaceMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (bookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("ยังไม่มีบุ๊กมาร์ก", color = FoxColors.OnSurfaceFaint, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(bookmarks, key = { it.id }) { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onSelectBookmark(item.url) }.padding(4.dp)
                    ) {
                        Surface(modifier = Modifier.size(54.dp), shape = FoxShapeMedium, color = FoxColors.SurfaceElevated) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(item.title.take(1).uppercase(), color = FoxColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(item.title, color = FoxColors.OnSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun BrowserBottomToolbar(urlBarText: String, onUrlBarChange: (String) -> Unit, onGo: () -> Unit, onBack: () -> Unit, onForward: () -> Unit, onRefresh: () -> Unit, onMenu: () -> Unit, onTabs: () -> Unit, canGoBack: Boolean, canGoForward: Boolean, progress: Int) {
    Column(modifier = Modifier.background(FoxColors.Surface)) {
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = FoxColors.Accent,
                trackColor = Color.Transparent
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ย้อนกลับ", tint = if (canGoBack) FoxColors.OnSurface else FoxColors.OnSurfaceFaint)
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "ไปข้างหน้า", tint = if (canGoForward) FoxColors.OnSurface else FoxColors.OnSurfaceFaint)
            }
            Surface(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                shape = FoxShapePill,
                color = FoxColors.SurfaceElevated
            ) {
                Row(modifier = Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (urlBarText.startsWith("https://")) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = null, tint = FoxColors.OnSurfaceFaint, modifier = Modifier.size(14.dp)
                    )
                    BasicUrlField(
                        value = urlBarText,
                        onValueChange = onUrlBarChange,
                        onGo = onGo,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                    )
                    IconButton(onClick = onRefresh, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "รีเฟรช", tint = FoxColors.OnSurfaceMuted, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onGo, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ไป", tint = FoxColors.Accent, modifier = Modifier.size(15.dp))
                    }
                }
            }
            IconButton(onClick = onTabs, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Layers, contentDescription = "แท็บ", tint = FoxColors.OnSurface) }
            IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "เมนู", tint = FoxColors.OnSurface) }
        }
    }
}

@Composable
private fun BasicUrlField(value: String, onValueChange: (String) -> Unit, onGo: () -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        placeholder = { Text("ค้นหาหรือพิมพ์ URL...", fontSize = 12.sp, color = FoxColors.OnSurfaceFaint) },
        singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = FoxColors.OnSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = FoxColors.Accent
        )
    )
}

@Composable
private fun MenuDrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = FoxColors.OnSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 14.sp)
    }
}

@Composable
fun MenuDrawer(isDesktop: Boolean, onHome: () -> Unit, onAddTab: () -> Unit, onAddIncognitoTab: () -> Unit, onAddBookmark: () -> Unit, onOpenHistoryBookmarks: () -> Unit, onToggleDesktop: () -> Unit, onExtensionStore: () -> Unit, onSettings: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.width(240.dp), color = FoxColors.SurfaceElevated, shape = FoxShapeMedium, tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            MenuDrawerItem(Icons.Default.Home, "หน้าแรก", onClick = onHome)
            MenuDrawerItem(Icons.Default.Add, "แท็บใหม่", onClick = onAddTab)
            MenuDrawerItem(Icons.Default.VisibilityOff, "แท็บไม่ระบุตัวตน", tint = FoxColors.Incognito, onClick = onAddIncognitoTab)
            HorizontalDivider(color = FoxColors.Outline, modifier = Modifier.padding(vertical = 4.dp))
            MenuDrawerItem(Icons.Default.StarBorder, "เพิ่มลงบุ๊กมาร์ก", onClick = onAddBookmark)
            MenuDrawerItem(Icons.Default.History, "ประวัติ & บุ๊กมาร์ก", onClick = onOpenHistoryBookmarks)
            MenuDrawerItem(
                if (isDesktop) Icons.Default.PhoneAndroid else Icons.Default.Computer,
                if (isDesktop) "มุมมองมือถือ" else "มุมมองคอมพิวเตอร์",
                onClick = onToggleDesktop
            )
            MenuDrawerItem(Icons.Default.Extension, "ส่วนขยาย (Extensions)", tint = FoxColors.Accent, onClick = onExtensionStore)
            HorizontalDivider(color = FoxColors.Outline, modifier = Modifier.padding(vertical = 4.dp))
            MenuDrawerItem(Icons.Default.Settings, "ตั้งค่า", onClick = onSettings)
            MenuDrawerItem(Icons.Default.ExitToApp, "ออกจากแอป", tint = FoxColors.Danger, onClick = onExit)
        }
    }
}

@Composable
fun FullScreenTabSwitcher(
    tabs: List<TabState>,
    currentTab: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onAddTab: () -> Unit,
    onAddIncognitoTab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    val safeInitial = currentTab.coerceIn(0, tabs.size - 1)
    val switcherPagerState = rememberPagerState(initialPage = safeInitial, pageCount = { tabs.size })

    Surface(modifier = modifier, color = FoxColors.Background, shape = RoundedCornerShape(20.dp), tonalElevation = 12.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("แท็บทั้งหมด (${tabs.size})", color = FoxColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "ปิด", tint = FoxColors.OnSurface) }
            }

            HorizontalPager(
                state = switcherPagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                pageSpacing = 12.dp,
                contentPadding = PaddingValues(horizontal = 28.dp)
            ) { page ->
                val tab = tabs.getOrNull(page)
                if (tab != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).clickable { onSelect(page) },
                        color = FoxColors.SurfaceHigh,
                        border = if (page == currentTab) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (tab.url == INTERNAL_HOME_URL) Icons.Default.Home else Icons.Default.Language, contentDescription = null, tint = FoxColors.OnSurfaceMuted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(tab.title, color = FoxColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(tab.url, color = FoxColors.OnSurfaceMuted, fontSize = 11.sp, maxLines = 1)
                            }
                            IconButton(onClick = { onClose(page) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = FoxColors.OnSurface)
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAddTab, modifier = Modifier.weight(1f)) { Text("แท็บใหม่") }
                OutlinedButton(onClick = onAddIncognitoTab, modifier = Modifier.weight(1f)) { Text("ไม่ระบุตัวตน") }
            }
        }
    }
}

@Composable
fun SettingsDialog(currentEngine: SearchEngine, onSelectEngine: (SearchEngine) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("⚙️ ตั้งค่า") }, text = {
        Column {
            Text("เครื่องมือค้นหาเริ่มต้น", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SearchEngine.entries.forEach { engine ->
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
                    LazyColumn { items(historyList, key = { it.id }) { item ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelectUrl(item.url); onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { Text(item.title, maxLines = 1, color = FoxColors.OnSurface, fontSize = 14.sp); Text(item.url, maxLines = 1, color = FoxColors.OnSurfaceMuted, fontSize = 11.sp) }
                            IconButton(onClick = { dbHelper.deleteHistoryById(item.id); historyList = dbHelper.getAllHistory() }) { Icon(Icons.Default.Delete, contentDescription = "ลบ", tint = FoxColors.OnSurfaceMuted, modifier = Modifier.size(18.dp)) }
                        }
                    } }
                } else {
                    LazyColumn { items(bookmarkList, key = { it.id }) { item ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelectUrl(item.url); onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { Text(item.title, maxLines = 1, color = FoxColors.OnSurface, fontSize = 14.sp); Text(item.url, maxLines = 1, color = FoxColors.OnSurfaceMuted, fontSize = 11.sp) }
                            IconButton(onClick = { dbHelper.deleteBookmarkByUrl(item.url); bookmarkList = dbHelper.getAllBookmarks() }) { Icon(Icons.Default.Delete, contentDescription = "ลบ", tint = FoxColors.OnSurfaceMuted, modifier = Modifier.size(18.dp)) }
                        }
                    } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}
