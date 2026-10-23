package com.terangbulan.sysrescue.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ActionLog — pencatat semua pergerakan aksi aplikasi.
 * Menyimpan ke memori dan flush ke /sdcard/sysrescue_log.txt saat app close.
 */
object ActionLog {
    private val buffer = StringBuilder()
    private const val MAX_BUFFER = 500_000

    @Synchronized
    fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        buffer.append("[$ts] $msg\n")
        if (buffer.length > MAX_BUFFER) buffer.delete(0, buffer.length / 3)
    }

    @Synchronized
    fun logExec(command: String, output: String?, durationMs: Long, error: String? = null) {
        log("════════ EXEC ════════")
        log("CMD: $command")
        log("DURATION: ${durationMs}ms")
        if (error != null) {
            log("RESULT: ERROR → $error")
        } else {
            val lines = output?.lines() ?: emptyList()
            log("RESULT: OK (${output?.length ?: 0} bytes, ${lines.size} baris)")
            if (lines.isNotEmpty()) {
                lines.take(20).forEach { log("  │ $it") }
                if (lines.size > 20) log("  │ ... (${lines.size - 20} baris lagi)")
            }
        }
    }

    @Synchronized
    fun logUi(action: String) {
        log("▶ UI: $action")
    }

    @Synchronized
    fun clear() { buffer.clear() }

    @Synchronized
    fun flush(context: Context): Boolean {
        if (buffer.isEmpty()) return false
        val header = "SYSrescue Action Log\n" +
                "Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n" +
                "═══════════════════════════════════════════════════════\n\n"
        val content = header + buffer.toString()

        try {
            File("/sdcard/sysrescue_log.txt").writeText(content)
            return true
        } catch (_: Throwable) {}

        try {
            val dir = context.getExternalFilesDir(null) ?: return false
            dir.mkdirs()
            File(dir, "sysrescue_log.txt").writeText(content)
            return true
        } catch (_: Throwable) {}

        return false
    }

    @Synchronized
    fun getBuffer(): String = buffer.toString()
}
