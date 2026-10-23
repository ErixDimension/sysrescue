package com.terangbulan.sysrescue.adb

import android.content.Context
import android.hardware.usb.UsbDevice
import com.terangbulan.sysrescue.util.ActionLog
import com.terangbulan.sysrescue.util.DebugLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import java.io.IOException

class AdbUsbTransport(
    private val context: Context,
    private val usbDevice: UsbDevice
) : Closeable {

    companion object {
        fun hasAdb(device: UsbDevice): Boolean = AdbLibUsbTransport.hasAdb(device)
        private const val EXEC_TIMEOUT_MS = 15000L
        private const val PUSH_PULL_TIMEOUT_MS = 60000L
    }

    private var lib: AdbLibUsbTransport? = null
    private val ioMutex = Mutex()

    val isConnected: Boolean get() = lib?.isConnected == true

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching {
                DebugLogger.setStatus("Membuka koneksi ADB...")
                ActionLog.log("CONNECT: mulai handshake")
                val t = AdbLibUsbTransport(context, usbDevice)
                t.connect()
                ActionLog.log("CONNECT: handshake OK, health check...")

                // 🔥 HEALTH CHECK: pastikan echo berjalan
                var healthy = false
                for (attempt in 1..3) {
                    delay(300)
                    val r = withTimeoutOrNull(2000L) { runCatching { t.exec("echo ready") }.getOrNull() }
                    if (r?.trim() == "ready") { healthy = true; break }
                }
                if (!healthy) {
                    ActionLog.log("CONNECT: health check GAGAL")
                    runCatching { t.close() }
                    throw IOException("Health check gagal")
                }

                ActionLog.log("CONNECT: health check OK")
                lib = t
                DebugLogger.setStatus("✓ ADB Terhubung")
            }.also { r ->
                if (r.isFailure) {
                    runCatching { lib?.close() }
                    lib = null
                    val msg = r.exceptionOrNull()?.message ?: "unknown"
                    ActionLog.log("CONNECT FAILED: $msg")
                    DebugLogger.log("❌ AdbLib connect: $msg")
                }
            }
        }
    }

    private suspend fun executeWithLog(command: String): String {
        val t = lib ?: throw IOException("ADB terputus")
        val start = System.currentTimeMillis()
        try {
            val res = withTimeoutOrNull(EXEC_TIMEOUT_MS) {
                ioMutex.withLock { t.exec(command) }
            } ?: throw IOException("Timeout ${EXEC_TIMEOUT_MS}ms")
            val duration = System.currentTimeMillis() - start
            ActionLog.logExec(command, res, duration)
            return res
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            ActionLog.logExec(command, null, duration, e.message)
            throw e
        }
    }

    private fun isConnError(e: Exception): Boolean {
        val msg = e.message ?: ""
        return msg.contains("connect() must be called first") ||
               msg.contains("Stream open actively rejected") ||
               msg.contains("ADB terputus") ||
               msg.contains("pipe closed")
    }

    private suspend fun reconnectAndWait(): Boolean = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            try {
                ActionLog.log("RECONNECT: mulai")
                DebugLogger.log("🔄 Reconnect...")
                runCatching { lib?.close() }
                lib = null
                delay(500)
                val t = AdbLibUsbTransport(context, usbDevice)
                t.connect()
                delay(300)
                val ping = withTimeoutOrNull(2000L) { runCatching { t.exec("echo ready") }.getOrNull() }
                if (ping?.trim() != "ready") throw IOException("Ping gagal")
                lib = t
                ActionLog.log("RECONNECT: OK")
                DebugLogger.log("✓ Reconnect OK")
                DebugLogger.setStatus("✓ ADB Terhubung")
                true
            } catch (e: Exception) {
                ActionLog.log("RECONNECT FAILED: ${e.message}")
                DebugLogger.log("❌ Reconnect gagal: ${e.message}")
                DebugLogger.setStatus("❌ Reconnect gagal")
                false
            }
        }
    }

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        try {
            return@withContext executeWithLog(command)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (!isConnError(e)) throw e
            ActionLog.log("EXEC CONN ERROR: ${e.message}")
            if (!reconnectAndWait()) throw IOException("Reconnect gagal")
            ActionLog.log("EXEC RETRY: $command")
            return@withContext executeWithLog(command)
        }
    }

    suspend fun push(local: File, remotePath: String, onProgress: ((Long, Long) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val t = lib ?: error("ADB terputus")
            val res = withTimeoutOrNull(PUSH_PULL_TIMEOUT_MS) {
                ioMutex.withLock { t.push(local, remotePath, onProgress) }
            }
            if (res == null) error("Push Timeout")
        }
    }

    suspend fun pull(remotePath: String, local: File, expectedSize: Long = 0L, onProgress: ((Long, Long) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val t = lib ?: error("ADB terputus")
            val res = withTimeoutOrNull(PUSH_PULL_TIMEOUT_MS) {
                ioMutex.withLock { t.pull(remotePath, local, expectedSize, onProgress) }
            }
            if (res == null) error("Pull Timeout")
        }
    }

    override fun close() {
        val oldLib = lib
        lib = null
        Thread { try { oldLib?.close() } catch (_: Throwable) {} }.start()
    }
}
