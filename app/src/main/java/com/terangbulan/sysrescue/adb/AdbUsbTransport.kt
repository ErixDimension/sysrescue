package com.terangbulan.sysrescue.adb

import android.content.Context
import android.hardware.usb.UsbDevice
import com.terangbulan.sysrescue.util.DebugLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
        private const val EXEC_TIMEOUT_MS = 15000L // Naikkan jadi 15 detik
        private const val PUSH_PULL_TIMEOUT_MS = 60000L
    }

    private var lib: AdbLibUsbTransport? = null
    private val ioMutex = Mutex()

    val isConnected: Boolean get() = lib?.isConnected == true

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
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

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val t = lib ?: throw IOException("ADB terputus")
        
        try {
            val res = withTimeoutOrNull(EXEC_TIMEOUT_MS) {
                ioMutex.withLock { t.exec(command) }
            }
            // Jika timeout, JANGAN abandon koneksi. Cukup lempar error dan biarkan UI kosong.
            res ?: throw IOException("Timeout membaca folder. (Koneksi ADB tetap aman).")
        } catch (e: CancellationException) {
            throw e // Biarkan coroutine batal secara natural tanpa bikin log error parah
        } catch (e: Exception) {
            DebugLogger.log("⚠️ exec warning: ${e.message}")
            throw e
        }
    }

    override fun close() {
        val oldLib = lib
        lib = null
        Thread { try { oldLib?.close() } catch (_: Throwable) {} }.start()
    }
}
