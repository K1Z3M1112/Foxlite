package com.winlator.cmod.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Centralized background-work executors for the whole app.
 *
 * Historically most Activities/Fragments spun up a raw `Thread(...)` for every
 * background job (downloads, file IO, sync, etc). Each of those threads pays
 * OS thread-creation/teardown cost, is unbounded (nothing stops 10 downloads
 * from spawning 10 native threads at once), and is easy to leak if the call
 * site forgets to guard against the owning Activity/Fragment being destroyed
 * mid-flight.
 *
 * Use this instead of `Thread(...).start()`:
 *
 * ```
 * AppExecutors.io.execute {
 *     val result = doNetworkOrDiskWork()
 *     AppExecutors.mainThread.post { updateUi(result) }
 * }
 * ```
 *
 * Two pools are exposed:
 * - [io]: bounded-thread-count pool (2-8 threads, unbounded queue) for blocking
 *   IO/network work (downloads, file scans, HTTP calls) — bounded so we don't
 *   fork unlimited native threads under load, unlike raw `Thread()` callers.
 * - [cpu]: fixed pool sized to available cores, for CPU-bound work (parsing,
 *   icon extraction, image decoding).
 *
 * This is not meant to replace dedicated long-lived native/JNI worker threads
 * (e.g. the XServer connector event loop) - those legitimately need their own
 * persistent Thread and are out of scope here.
 */
object AppExecutors {
    private val cpuCount = maxOf(2, Runtime.getRuntime().availableProcessors())

    /** Pool for blocking IO/network work (downloads, file scans, HTTP, disk reads). */
    val io: ExecutorService by lazy {
        ThreadPoolExecutor(
            2, 8, 30L, TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            namedThreadFactory("AppIO")
        )
    }

    /** Pool for CPU-bound work (parsing, decoding, icon extraction). */
    val cpu: ExecutorService by lazy {
        Executors.newFixedThreadPool(cpuCount, namedThreadFactory("AppCPU"))
    }

    /** Post back to the UI thread, e.g. from an io/cpu task's completion. */
    val mainThread: Handler by lazy { Handler(Looper.getMainLooper()) }

    private fun namedThreadFactory(prefix: String): ThreadFactory {
        val counter = AtomicInteger(1)
        return ThreadFactory { runnable ->
            Thread(runnable, "$prefix-${counter.getAndIncrement()}").apply {
                isDaemon = true
            }
        }
    }
}
