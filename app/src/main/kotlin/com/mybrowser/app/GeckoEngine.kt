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
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

// GeckoRuntime singleton + uBlock Origin WebExtension bootstrap
object GeckoEngine {
    private var runtime: GeckoRuntime? = null

    // uBlock Origin's stable Firefox extension id.
    private const val UBLOCK_EXTENSION_ID = "uBlock0@raymondhill.net"

    // GeckoView installs this as a "built-in" extension (ensureBuiltIn), which is the
    // supported way to ship a curated extension with the app: no network fetch at runtime,
    // no install permission-prompt UI to implement, and it's trusted like the app itself.
    //
    // This requires the signed .xpi to be bundled at:
    //   app/src/main/assets/extensions/ublock_origin.xpi
    // Download the latest signed build from Mozilla Add-ons and place it there, e.g.:
    //   https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi
    // (Not fetched automatically here — binary .xpi can't be generated from this codebase.)
    private const val UBLOCK_XPI_ASSET_URI = "resource://android/assets/extensions/ublock_origin.xpi"

    private var uBlockInstallRequested = false

    fun getRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .build()
            val created = GeckoRuntime.create(context.applicationContext, settings)
            runtime = created
            installUBlockOrigin(created)
        }
        return runtime!!
    }

    private fun installUBlockOrigin(runtime: GeckoRuntime) {
        if (uBlockInstallRequested) return
        uBlockInstallRequested = true
        runtime.webExtensionController
            .ensureBuiltIn(UBLOCK_XPI_ASSET_URI, UBLOCK_EXTENSION_ID)
            .accept(
                { extension -> Log.i("GeckoEngine", "uBlock Origin loaded: ${extension?.id}") },
                { error -> Log.e("GeckoEngine", "uBlock Origin failed to load — make sure ublock_origin.xpi is present at app/src/main/assets/extensions/", error) }
            )
    }
}
