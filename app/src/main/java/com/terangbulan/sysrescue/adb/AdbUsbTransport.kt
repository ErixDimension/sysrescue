package com.terangbulan.sysrescue.adb

import android.content.Context
import android.hardware.usb.UsbDevice
import com.terangbulan.sysrescue.util.DebugLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    @Volatile private var isReconnecting = false

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

    // 🔥 AUTO RECONNECT: Jika koneksi mati, sambung ulang di background
    private fun triggerReconnect() {
        if (isReconnecting) return
        isReconnecting = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DebugLogger.log("🔄 Mencoba menyambung ulang ADB...")
                runCatching { lib?.close() }
                lib = null
                delay(1000) // Beri jeda 1 detik agar USB bersih
                
                ioMutex.withLock {
                    val t = AdbLibUsbTransport(context, usbDevice)
                    t.connect()
                    lib = t
                    DebugLogger.log("✅ Auto-reconnect BERHASIL")
                    DebugLogger.setStatus("✓ ADB Terhubung")
                }
            } catch (e: Throwable) {
                DebugLogger.log("❌ Auto-reconnect GAGAL: ${e.message}")
                DebugLogger.setStatus("❌ ADB terputus")
            } finally {
                isReconnecting = false
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
            res ?: throw IOException("Timeout membaca folder")
        } catch (e: CancellationException) {
            throw e // Abaikan pembatalan biasa
        } catch (e: Exception) {
            DebugLogger.log("⚠️ Exec error: ${e.message}")
            
            // Jika error karena stream ditolak atau koneksi mati, picu Reconnect
            val msg = e.message ?: ""
            if (msg.contains("connect() must be called first") || 
                msg.contains("Stream open actively rejected") ||
                msg.contains("ADB terputus")) {
                triggerReconnect()
            }
            throw e
        }
    }

    override fun close() {
        val oldLib = lib
        lib = null
        Thread { try { oldLib?.close() } catch (_: Throwable) {} }.start()
    }
}
