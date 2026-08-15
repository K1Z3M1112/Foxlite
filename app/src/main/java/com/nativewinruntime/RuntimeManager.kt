package com.nativewinruntime

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import com.github.luben.zstd.ZstdInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

class RuntimeManager(private val context: Context) {
    private val runtimeDir = File(context.filesDir, "runtime")
    private val rootfsDir = File(runtimeDir, "rootfs")
    private val homeDir = File(context.filesDir, "home")
    private val prefixDir = File(context.filesDir, "wineprefix")
    private val lock = Any()

    fun prepare(force: Boolean = false): Result<File> = runCatching {
        synchronized(lock) {
            runtimeDir.mkdirs()
            homeDir.mkdirs()
            prefixDir.mkdirs()

            val marker = File(runtimeDir, ".installed-v0.6")
            val box64 = File(runtimeDir, "usr/local/bin/box64")
            val wine = File(rootfsDir, "opt/wine/bin/wine")
            val loader = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")

            if (force || !marker.exists() || !box64.isFile || !wine.isFile || !loader.exists()) {
                if (rootfsDir.exists()) rootfsDir.deleteRecursively()
                File(runtimeDir, "usr").deleteRecursively()
                extractTarZstAsset("runtime/rootfs.tzst", runtimeDir)
                extractTarZstAsset("runtime/box64/box64-0.4.0.tzst", runtimeDir)
                box64.setExecutable(true, false)
                marker.writeText("native-runtime-v0.6\\n")
            }

            require(box64.isFile) { "Box64 extraction failed" }
            require(wine.isFile) { "Wine extraction failed" }
            require(loader.exists()) { "ARM64 glibc loader is missing" }
            runtimeDir
        }
    }

    fun dir() = runtimeDir
    fun rootfs() = rootfsDir
    fun home() = homeDir
    fun prefix() = prefixDir

    fun box64(): File? = File(runtimeDir, "usr/local/bin/box64").takeIf { it.isFile }
    fun wine(): File? = File(rootfsDir, "opt/wine/bin/wine").takeIf { it.isFile }
    fun wineServer(): File? = File(rootfsDir, "opt/wine/bin/wineserver").takeIf { it.isFile }
    fun loader(): File? = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1").takeIf { it.isFile }

    fun isReady(): Boolean = box64() != null && wine() != null && loader() != null

    private fun extractTarZstAsset(asset: String, destination: File) {
        context.assets.open(asset).use { raw ->
            ZstdInputStream(BufferedInputStream(raw)).use { zstd ->
                TarArchiveInputStream(BufferedInputStream(zstd)).use { tar ->
                    var entry = tar.nextTarEntry
                    val root = destination.canonicalFile
                    while (entry != null) {
                        val cleanName = entry.name.removePrefix("./")
                        if (cleanName.isBlank()) {
                            entry = tar.nextTarEntry
                            continue
                        }
                        val out = File(root, cleanName).canonicalFile
                        require(out.path == root.path || out.path.startsWith(root.path + File.separator)) {
                            "Unsafe runtime archive entry: ${entry.name}"
                        }

                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else if (entry.isSymbolicLink) {
                            out.parentFile?.mkdirs()
                            if (out.exists() || Files.isSymbolicLink(out.toPath())) out.delete()
                            val link = entry.linkName
                            val target = if (link.startsWith("/")) {
                                // Convert rootfs-absolute links into links relative to this rootfs.
                                val targetFile = File(root, link.removePrefix("/")).canonicalFile
                                out.parentFile.toPath().relativize(targetFile.toPath()).toString()
                            } else link
                            Files.createSymbolicLink(out.toPath(), java.nio.file.Paths.get(target))
                        } else if (entry.isFile) {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { output -> tar.copyTo(output) }
                            out.setExecutable((entry.mode and 0x49) != 0, false)
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
        }
    }
}
