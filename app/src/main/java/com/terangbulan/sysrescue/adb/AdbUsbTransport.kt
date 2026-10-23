package com.terangbulan.sysrescue.adb

import android.content.Context
import android.hardware.usb.UsbDevice
import com.terangbulan.sysrescue.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AdbUsbTransport(
    private val context: Context,
    private val usbDevice: UsbDevice
) : Closeable {

    companion object {
        fun hasAdb(device: UsbDevice): Boolean = AdbLibUsbTransport.hasAdb(device)
        private const val EXEC_TIMEOUT_SEC = 8L
        private const val PUSH_PULL_TIMEOUT_SEC = 60L
    }

    private var lib: AdbLibUsbTransport? = null

    val isConnected: Boolean get() = lib?.isConnected == true

    // Jalankan operasi di thread terpisah agar bisa kita "abandon" saat timeout
    private fun <T> runWithHardTimeout(timeoutSec: Long, block: () -> T): T {
        val future = CompletableFuture<T>()
        Thread {
            try { future.complete(block()) }
            catch (e: Throwable) { future.completeExceptionally(e) }
        }.also { it.isDaemon = true }.start()

        try {
            return future.get(timeoutSec, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            // Force close: ini akan membuat thread yang nyangkut melempar IOException
            runCatching { lib?.close() }
            lib = null
            throw IOException("ADB timeout ${timeoutSec}s — koneksi di-reset")
        }
    }

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            DebugLogger.setStatus("Membuka koneksi ADB...")
            val t = AdbLibUsbTransport(context, usbDevice)
            t.connect()
            lib = t
            DebugLogger.setStatus("✓ ADB Terhubung")
        }.also { r ->
            if (r.isFailure) {
                runCatching { lib?.close() }
                lib = null
                DebugLogger.log("❌ AdbLib connect: ${r.exceptionOrNull()?.message}")
            }
        }
    }

    /** Auto-reconnect jika terjadi timeout / koneksi mati. */
    private fun autoReconnect() {
        DebugLogger.log("🔄 Mencoba auto-reconnect ADB...")
        runCatching {
            val newLib = AdbLibUsbTransport(context, usbDevice)
            newLib.connect()
            lib = newLib
            DebugLogger.log("✅ Auto-reconnect BERHASIL")
            DebugLogger.setStatus("✓ ADB Terhubung")
        }.also { r ->
            if (r.isFailure) {
                lib = null
                DebugLogger.log("❌ Auto-reconnect GAGAL: ${r.exceptionOrNull()?.message}")
                DebugLogger.setStatus("❌ ADB terputus — cabut & colok ulang USB")
            }
        }
    }

    suspend fun push(
        local: File,
        remotePath: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val t = lib ?: error("ADB terputus")
            DebugLogger.setStatus("⬆ Push ${local.name}")
            runWithHardTimeout(PUSH_PULL_TIMEOUT_SEC) {
                t.push(local, remotePath, onProgress)
            }
        }.also { r ->
            if (r.isFailure) {
                DebugLogger.log("❌ Push: ${r.exceptionOrNull()?.message}")
                autoReconnect()
            }
        }
    }

    suspend fun pull(
        remotePath: String,
        local: File,
        expectedSize: Long = 0L,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val t = lib ?: error("ADB terputus")
            DebugLogger.setStatus("⬇ Pull ${local.name}")
            runWithHardTimeout(PUSH_PULL_TIMEOUT_SEC) {
                t.pull(remotePath, local, expectedSize, onProgress)
            }
        }.also { r ->
            if (r.isFailure) {
                DebugLogger.log("❌ Pull: ${r.exceptionOrNull()?.message}")
                autoReconnect()
            }
        }
    }

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val t = lib ?: throw IOException("ADB terputus")
        try {
            runWithHardTimeout(EXEC_TIMEOUT_SEC) { t.exec(command) }
        } catch (e: IOException) {
            DebugLogger.log("⚠️ exec timeout: $command")
            autoReconnect()
            throw e
        }
    }

    override fun close() {
        runCatching { lib?.close() }
        lib = null
        DebugLogger.setStatus("ADB terputus")
    }
}
