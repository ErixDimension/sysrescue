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

    suspend fun push(local: File, remotePath: String, onProgress: ((Long, Long) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val t = lib ?: error("ADB terputus")
            runWithHardTimeout(PUSH_PULL_TIMEOUT_SEC) { t.push(local, remotePath, onProgress) }
        }
    }

    suspend fun pull(remotePath: String, local: File, expectedSize: Long = 0L, onProgress: ((Long, Long) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val t = lib ?: error("ADB terputus")
            runWithHardTimeout(PUSH_PULL_TIMEOUT_SEC) { t.pull(remotePath, local, expectedSize, onProgress) }
        }
    }

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val t = lib ?: throw IOException("ADB terputus")
        try {
            runWithHardTimeout(EXEC_TIMEOUT_SEC) { t.exec(command) }
        } catch (e: IOException) {
            DebugLogger.log("⚠️ exec timeout: $command")
            throw e
        }
    }

    override fun close() {
        runCatching { lib?.close() }
        lib = null
    }
}
