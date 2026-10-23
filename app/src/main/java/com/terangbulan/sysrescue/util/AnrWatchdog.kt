package com.terangbulan.sysrescue.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        // ✅ PERBAIKAN: Gunakan Runnable dan nama Thread dengan benar
        val thread = Thread({
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
                    val now = System.currentTimeMillis()
                    if (now - lastDump > ANR_THRESHOLD_MS) {
                        writeAnrDump()
                        lastDump = now
                    }
                }
            }
        }, "AnrWatchdog")
        
        thread.isDaemon = true
        thread.priority = Thread.MAX_PRIORITY
        watchdogThread = thread
        thread.start()
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

        try {
            File("/storage/emulated/0/$LOG_FILENAME").appendText(dump)
            return
        } catch (_: Throwable) {}

        try {
            val dir = ctx.getExternalFilesDir(null) ?: return
            dir.mkdirs()
            File(dir, LOG_FILENAME).appendText(dump)
        } catch (_: Throwable) {}
    }

    fun stop() {
        running = false
        watchdogThread?.interrupt()
        watchdogThread = null
    }
}
