package com.terangbulan.sysrescue.adb

import android.content.Context
import android.hardware.usb.UsbDevice
import com.terangbulan.sysrescue.util.DebugLogger
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
                    DebugLogger.setStatus("❌ ADB Gagal Terhubung")
                }
            }
        }
    }

    suspend fun push(
        local: File, remotePath: String, onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching {
                val t = lib ?: error("ADB terputus")
                val res = withTimeoutOrNull(30000L) { t.push(local, remotePath, onProgress) }
                res ?: throw IOException("Push Timeout (30s)")
            }
        }
    }

    suspend fun pull(
        remotePath: String, local: File, expectedSize: Long = 0L, onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching {
                val t = lib ?: error("ADB terputus")
                val res = withTimeoutOrNull(30000L) { t.pull(remotePath, local, expectedSize, onProgress) }
                res ?: throw IOException("Pull Timeout (30s)")
            }
        }
    }

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val t = lib ?: throw IOException("ADB terputus")
            val res = withTimeoutOrNull(8000L) { t.exec(command) }
            res ?: throw IOException("ADB Timeout (8s) — koneksi merespon lambat")
        }
    }

    override fun close() {
        runCatching { lib?.close() }
        lib = null
        DebugLogger.setStatus("ADB terputus")
    }
}
