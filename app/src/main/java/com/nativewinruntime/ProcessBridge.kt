package com.nativewinruntime

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProcessBridge(private val context: Context, private val runtime: RuntimeManager) {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var process: Process? = null

    fun stop() {
        process?.destroy()
        if (process?.isAlive == true) process?.destroyForcibly()
        process = null
    }

    fun launch(uri: Uri, onLog: (String) -> Unit) {
        executor.execute {
            try {
                if (!runtime.isReady()) runtime.prepare().getOrThrow()

                val exe = materializeExe(uri)
                val loader = runtime.loader() ?: error("ARM64 glibc loader missing")
                val box64 = runtime.box64() ?: error("Box64 missing")
                val wine = runtime.wine() ?: error("Wine missing")
                val wineServer = runtime.wineServer()

                val root = runtime.rootfs()
                val rootLib = File(root, "usr/lib")
                val rootLib64 = File(root, "lib")
                val wineBin = File(root, "opt/wine/bin")
                val wineLib = File(root, "opt/wine/lib")
                val wineLoader = File(root, "opt/wine/lib/wine")

                val env = HashMap(System.getenv())
                env["HOME"] = runtime.home().absolutePath
                env["WINEPREFIX"] = runtime.prefix().absolutePath
                env["PWD"] = runtime.home().absolutePath
                env["TMPDIR"] = File(context.cacheDir, "tmp").apply { mkdirs() }.absolutePath
                env["WINEDEBUG"] = "-all"
                env["WINEARCH"] = "win64"
                env["WINEDLLOVERRIDES"] = "d3d9,d3d10,d3d11,dxgi=builtin"
                env["WINELOADER"] = wine.absolutePath
                if (wineServer != null) env["WINESERVER"] = wineServer.absolutePath
                env["WINE"] = wine.absolutePath
                env["BOX64_PATH"] = "${wineBin.absolutePath}:${env["PATH"] ?: ""}"
                env["BOX64_LD_LIBRARY_PATH"] =
                    "${wineLib.absolutePath}:${rootLib.absolutePath}:${rootLib64.absolutePath}"
                env["LD_LIBRARY_PATH"] =
                    "${rootLib.absolutePath}:${rootLib64.absolutePath}:${wineLib.absolutePath}"
                env["PATH"] =
                    "${wineBin.absolutePath}:${File(root, "usr/bin").absolutePath}:${env["PATH"] ?: ""}"
                env["NWR_RUNTIME"] = runtime.dir().absolutePath
                env["NWR_ROOTFS"] = root.absolutePath

                // Execute the Android/ARM64 glibc loader explicitly. This avoids
                // relying on Box64's Winlator-specific PT_INTERP path.
                val command = listOf(
                    loader.absolutePath,
                    "--library-path",
                    "${rootLib.absolutePath}:${rootLib64.absolutePath}",
                    box64.absolutePath,
                    wine.absolutePath,
                    exe.absolutePath
                )

                onLog("Runtime ready")
                onLog("GPU path: Android Vulkan")
                onLog("Launching: ${exe.name}")
                onLog(command.joinToString(" "))

                val pb = ProcessBuilder(command)
                    .directory(runtime.home())
                    .redirectErrorStream(true)
                pb.environment().putAll(env)
                process = pb.start()

                process!!.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { onLog(it) }
                }
                val code = process!!.waitFor()
                onLog("Process exited: $code")
            } catch (t: Throwable) {
                onLog("Launch failed: ${t::class.java.simpleName}: ${t.message}")
            } finally {
                process = null
            }
        }
    }

    private fun materializeExe(uri: Uri): File {
        val dir = File(context.cacheDir, "selected").apply { mkdirs() }
        val out = File(dir, "program.exe")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected EXE" }
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }
}
