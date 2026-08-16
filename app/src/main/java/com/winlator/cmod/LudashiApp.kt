package com.winlator.cmod

import android.app.Application
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Process
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.winlator.cmod.core.AppLogCollector
import com.winlator.cmod.perf.GameSessionMemoryManager
import com.winlator.cmod.perf.PerfRevertRegistry
import com.winlator.cmod.perf.PerformanceSettings
import com.winlator.cmod.perf.RootManager
import com.winlator.cmod.perf.TempWatchdog
import java.io.File

/**
 * Initializes the performance-control safety layer before any activity can write a privileged node.
 * Failures are isolated because these controls are optional and must never block app startup.
 */
class LudashiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // XrActivity runs in :vr_process. It must not race the main process over the shared
        // persisted sysfs snapshot or interpret the main game's background state as an exit.
        if (!isMainProcess()) return
        try {
            // Registered first so it is alive before any wine/box64/guest process launches;
            // ProcessHelper decides whether to keep a process's stdout/stderr open at the
            // moment it starts, so starting this any later would miss early process output.
            AppLogCollector.getInstance().startIfEnabled(this)
            RootManager.onAppStartup(this)
            TempWatchdog.init(this)
            PerformanceSettings.init(this)
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    try {
                        PerfRevertRegistry.revertAll()
                    } catch (t: Throwable) {
                        Log.w("LudashiApp", "Background performance-state revert failed", t)
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w("LudashiApp", "Performance safety-core initialization failed", t)
        }
    }

    /**
     * System-wide memory-pressure signal. This is the one hook that reliably fires
     * across all Android versions/OEMs regardless of whether a game session is
     * active, so it's the right place to trim *this app's* own reclaimable caches
     * (e.g. the Steam grid image LRU cache) — freeing RAM back up for whatever is
     * the actual foreground/main task right now, game session or not.
     *
     * TRIM_MEMORY_RUNNING_CRITICAL/COMPLETE and UI_HIDDEN are the levels worth
     * reacting to; the lighter MODERATE/BACKGROUND levels are left alone so we
     * don't thrash caches (e.g. re-decoding icons) under normal light pressure.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!isMainProcess()) return
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.i("LudashiApp", "onTrimMemory($level): trimming app caches")
                GameSessionMemoryManager.trimCaches()
            }
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith("onTrimMemory"))
    override fun onLowMemory() {
        super.onLowMemory()
        if (!isMainProcess()) return
        Log.w("LudashiApp", "onLowMemory(): trimming app caches")
        GameSessionMemoryManager.trimCaches()
    }

    private fun isMainProcess(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processName = manager?.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
            ?: try {
                File("/proc/self/cmdline").readText().trim('\u0000')
            } catch (_: Throwable) {
                null
            }
        return processName == packageName
    }
}
