package com.terangbulan.sysrescue.manager

import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.util.PathUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class LocalFileManager {
    companion object {
        private const val ROOT = "/storage/emulated/0"
        private const val TIMEOUT_MS = 10_000L
    }

    fun rootPath(): String = ROOT

    suspend fun list(path: String): List<FileEntry.Local> = withContext(Dispatchers.IO) {
        val cleaned = PathUtil.cleanPath(path)
        if (cleaned == "/" || (!cleaned.startsWith("/storage/") && !cleaned.startsWith("/sdcard/"))) {
            return@withContext emptyList()
        }

        val dir = File(cleaned)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext emptyList()

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            try {
                val files = dir.listFiles()?.mapNotNull { f ->
                    try {
                        if (f == null) null
                        else FileEntry.Local(
                            name = f.name,
                            absolutePath = f.absolutePath,
                            size = if (f.isFile) f.length() else 0L,
                            isDir = f.isDirectory
                        )
                    } catch (_: Throwable) { null }
                }?.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() })) ?: emptyList()
                
                // BATASI MAKSIMAL 300 ITEM
                files.take(100)
            } catch (_: Throwable) { emptyList() }
        }
        result ?: emptyList()
    }
}
