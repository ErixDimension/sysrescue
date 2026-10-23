package com.terangbulan.sysrescue.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AnrWatchdog — mendeteksi ANR (Application Not Responding) otomatis.
 *
 * Cara kerja:
 * 1. Setiap 1000ms, watchdog "menyapa" main thread via Handler.post().
 * 2. Jika main thread tidak merespons dalam 3000ms (sebelum sistem ANR 5s),
 *    watchdog dump semua thread stack trace ke file.
 * 3. File ditulis ke /sdcard/ (jika permission) atau fallback Android/data/.
 *
 * Wajib di-start di MainActivity.onCreate() SEBELUM load UI berat.
 */
object AnrWatchdog {
    private const val INTERVAL_MS = 1000L
    private const val ANR_THRESHOLD_MS = 3000L
    private const val LOG_FILENAME = "sysrescue_crash.txt"

    private var watchdogThread: Thread? = null
    @Volatile private var running = false
    private var appContext: Context? = null

    fun start(context: Context) {
        if (running) return
        running = true
        appContext = context.applicationContext

        watchdogThread = Thread("AnrWatchdog") {
            val mainHandler = Handler(Looper.getMainLooper())
            var lastTick = 0L
            var lastDump = 0L

            while (running) {
                val startTick = lastTick
                mainHandler.post { lastTick = System.currentTimeMillis() }
                try {
                    Thread.sleep(INTERVAL_MS)
                } catch (_: InterruptedException) { break }

                if (lastTick == startTick) {
                    // Main thread tidak merespons sejak INTERVAL_MS terakhir
                    val now = System.currentTimeMillis()
                    if (now - lastDump > ANR_THRESHOLD_MS) {
                        writeAnrDump()
                        lastDump = now
                    }
                }
            }
        }.apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
        watchdogThread?.start()
    }

    private fun writeAnrDump() {
        val ctx = appContext ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

        val dump = buildString {
            append("\n\n═══════════════════════════════════════════════════════════\n")
            append("==== ANR DETECTED: $timestamp ====\n")
            append("═══════════════════════════════════════════════════════════\n")

            val mainThread = Looper.getMainLooper().thread
            append("\n>>> MAIN THREAD (state=${mainThread.state}) <<<\n")
            mainThread.stackTrace.forEach { append("  at $it\n") }

            append("\n>>> ALL OTHER THREADS <<<\n")
            Thread.getAllStackTraces().forEach { (t, stack) ->
                if (t != mainThread) {
                    append("\n--- ${t.name} (state=${t.state}) ---\n")
                    stack.forEach { append("  at $it\n") }
                }
            }
        }

        // Prioritas 1: /sdcard/ (mudah diakses user)
        try {
            File("/storage/emulated/0/$LOG_FILENAME").appendText(dump)
            return
        } catch (_: Throwable) {}

        // Fallback: app-specific external (tidak butuh permission)
        try {
            val dir = ctx.getExternalFilesDir(null) ?: return
            dir.mkdirs()
            File(dir, LOG_FILENAME).appendText(dump)
        } catch (_: Throwable) {}
    }

    /** Kembalikan path file yang benar-benar bisa dibaca user */
    fun logPath(context: Context): String {
        val sdcardFile = File("/storage/emulated/0/$LOG_FILENAME")
        if (sdcardFile.exists()) return sdcardFile.absolutePath
        val fallbackDir = context.getExternalFilesDir(null)
        if (fallbackDir != null) {
            val fallbackFile = File(fallbackDir, LOG_FILENAME)
            if (fallbackFile.exists()) return fallbackFile.absolutePath
        }
        return "belum ada file"
    }

    fun stop() {
        running = false
        watchdogThread?.interrupt()
        watchdogThread = null
    }
}
