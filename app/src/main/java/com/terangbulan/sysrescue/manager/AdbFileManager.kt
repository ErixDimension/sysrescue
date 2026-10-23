package com.terangbulan.sysrescue.manager

import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.adb.AdbUsbTransport
import com.terangbulan.sysrescue.util.DebugLogger
import com.terangbulan.sysrescue.util.PathUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdbFileManager(private val transport: AdbUsbTransport) {

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
    private fun stripAnsi(s: String): String = s.replace(Regex("\u001B\\[[;\\d]*m"), "")

    private fun resolvePath(path: String): String {
        val cleaned = PathUtil.cleanPath(path)
        return when {
            cleaned == "/sdcard" -> "/storage/emulated/0"
            cleaned.startsWith("/sdcard/") -> "/storage/emulated/0" + cleaned.substring("/sdcard".length)
            cleaned.startsWith("/mnt/sdcard") -> "/storage/emulated/0" + cleaned.substring("/mnt/sdcard".length)
            cleaned.startsWith("/data/media/0") -> "/storage/emulated/0" + cleaned.substring("/data/media/0".length)
            else -> cleaned
        }
    }

    suspend fun list(path: String): List<FileEntry.Remote> = withContext(Dispatchers.IO) {
        val normalized = resolvePath(path)
        val pathWithSlash = if (normalized.endsWith("/")) normalized else "$normalized/"
        val safePath = shellQuote(pathWithSlash)
        
        // KEMBALIKAN KE PERINTAH YANG TERBUKTI STABIL
        val cmd = "ls -la $safePath 2>/dev/null"
        
        val out = try {
            transport.exec(cmd)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return@withContext emptyList()
        }

        if (out.isBlank()) return@withContext emptyList()

        val lines = out.lines().map { stripAnsi(it) }.filter { it.isNotBlank() }
        val parsed = lines.mapNotNull { parseLsLine(it, normalized) }

        // Log jika parsing gagal
        if (parsed.isEmpty() && lines.size > 1) {
            DebugLogger.log("[R] Gagal parse. Sample:\n${lines.take(2).joinToString("\n")}")
        }

        parsed.take(100)
    }

    private fun parseLsLine(line: String, parentPath: String): FileEntry.Remote? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("total ")) return null
        val first = t[0]
        if (first != 'd' && first != '-' && first != 'l') return null
        
        val parts = t.split(Regex("\\s+"))
        if (parts.size < 7) return null
        
        var nameStartIdx = -1
        for (i in parts.indices.reversed()) {
            if (parts[i].matches(Regex("^\\d{4}$")) || parts[i].matches(Regex("^\\d{1,2}:\\d{2}$"))) {
                nameStartIdx = i + 1
                break
            }
        }
        
        if (nameStartIdx < 0 || nameStartIdx >= parts.size) return null

        var name = parts.drop(nameStartIdx).joinToString(" ")
        name = name.replace(Regex("\\\\(.)"), "$1")
        if (name == "." || name == "..") return null

        val sizeStr = parts.getOrNull(nameStartIdx - 2) ?: "0"
        val size = sizeStr.toLongOrNull() ?: 0L
        val isDir = first == 'd'

        val fullPath = if (parentPath == "/") "/$name" else "$parentPath/$name"
        return FileEntry.Remote(name, fullPath, parentPath, size, isDir)
    }
}
