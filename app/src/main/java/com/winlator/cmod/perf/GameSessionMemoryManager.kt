package com.winlator.cmod.perf

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Frees up as much RAM as possible for the "main task" — the emulated Windows game
 * process (wine/box64/box86 + guest app) — right before a game session starts, and
 * reports memory pressure while the session runs.
 *
 * This intentionally does NOT try to force-kill other apps' processes: on modern
 * Android (API 21+) `ActivityManager.killBackgroundProcesses()` for arbitrary
 * third-party packages is restricted to system/privileged apps and silently no-ops
 * for a normal app, so pretending to do that would be misleading. What we *can*
 * legitimately do without root:
 *  - ask the OS to trim its own reclaimable memory (page cache dropped, actual
 *    effect is OS-controlled, see [Context.getSystemService] ActivityManager docs)
 *  - drop this app's own soft caches (bitmap/icon caches etc.) before launch, so the
 *    app itself isn't competing with the guest process for heap
 *  - report current available RAM / low-memory state to callers so the launcher can
 *    warn the user ("close other apps") instead of failing silently later
 *
 * With root granted, [PerfRootApplier.freeMemoryNow] additionally drops the kernel
 * page cache (`/proc/sys/vm/drop_caches`), which is the actual "give RAM back to
 * the system" lever — call that first when root is available.
 */
object GameSessionMemoryManager {
    private const val TAG = "GameSessionMemory"

    /** Bytes below which we consider the device memory-constrained for a game session. */
    private const val LOW_MEMORY_THRESHOLD_BYTES = 512L * 1024 * 1024

    private val trimCallbacks = mutableListOf<() -> Unit>()

    /**
     * Register a soft-cache cleanup callback (icon cache, thumbnail cache, etc).
     * Call this from managers that hold reclaimable in-memory caches; it will be
     * invoked both by [prepareForSession] right before a game launches, and by
     * [trimCaches] whenever the OS reports memory pressure (see LudashiApp.onTrimMemory).
     */
    fun registerCacheTrimCallback(callback: () -> Unit) {
        trimCallbacks.add(callback)
    }

    /** Runs every registered cache-trim callback. Best-effort: never throws. */
    fun trimCaches() {
        try {
            trimCallbacks.forEach { it.invoke() }
        } catch (t: Throwable) {
            Log.w(TAG, "Cache trim callback failed", t)
        }
    }

    /**
     * Call once, right before starting the guest process (e.g. top of
     * XServerDisplayActivity.onCreate). Best-effort: never throws.
     */
    fun prepareForSession(context: Context) {
        trimCaches()

        // Root path: actually drop the kernel page cache (real RAM given back).
        if (PerfRootApplier.freeMemoryNow()) {
            Log.i(TAG, "Freed kernel page cache via root before session start")
        }

        // Non-root path: ask the runtime to compact its own heap. This is a hint,
        // not a guarantee, but it's the only portable lever available without root.
        Runtime.getRuntime().gc()

        logMemoryState(context, "session-start")
    }

    /** Call from onDestroy/onStop of the game session activity. */
    fun releaseAfterSession(context: Context) {
        logMemoryState(context, "session-end")
    }

    /** True if the device is currently under memory pressure for a game session. */
    fun isLowMemory(context: Context): Boolean {
        val info = readMemoryInfo(context) ?: return false
        return info.lowMemory || info.availMem < LOW_MEMORY_THRESHOLD_BYTES
    }

    private fun logMemoryState(context: Context, label: String) {
        val info = readMemoryInfo(context) ?: return
        val availMb = info.availMem / (1024 * 1024)
        val totalMb = info.totalMem / (1024 * 1024)
        Log.i(TAG, "[$label] available ${availMb}MB / ${totalMb}MB total, lowMemory=${info.lowMemory}")
    }

    private fun readMemoryInfo(context: Context): ActivityManager.MemoryInfo? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        return try {
            am.getMemoryInfo(info)
            info
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read memory info", t)
            null
        }
    }
}
