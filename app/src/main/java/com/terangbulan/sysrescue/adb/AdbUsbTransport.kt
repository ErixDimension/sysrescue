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
        private const val EXEC_TIMEOUT_MS = 5000L
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

    private fun abandonTransport(reason: String) {
        DebugLogger.log("⚠️ $reason! Memutuskan koneksi ADB (Abandon).")
        val oldLib = lib
        lib = null // Langsung null-kan agar perintah berikutnya langsung error/fallback
        
        // Tutup koneksi di background agar tidak memblokir UI/Coroutine
        Thread {
            try { oldLib?.close() } catch (_: Throwable) {}
        }.start()
    }

    suspend fun push(local: File, remotePath: String, onProgress: ((Long, Long) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val t = lib ?: error("ADB terputus")
            val res = withTimeoutOrNull(PUSH_PULL_TIMEOUT_MS) {
                ioMutex.withLock { t.push(local, remotePath, onProgress) }
            }
            if (res == null) {
                abandonTransport("Push Timeout")
                error("Push Timeout")
            }
        }
    }

    suspend fun pull(remotePath: String, local: File, expectedSize: Long = 0L, onProgress: ((Long, Long) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val t = lib ?: error("ADB terputus")
            val res = withTimeoutOrNull(PUSH_PULL_TIMEOUT_MS) {
                ioMutex.withLock { t.pull(remotePath, local, expectedSize, onProgress) }
            }
            if (res == null) {
                abandonTransport("Pull Timeout")
                error("Pull Timeout")
            }
        }
    }

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val t = lib ?: throw IOException("ADB terputus")
        val res = withTimeoutOrNull(EXEC_TIMEOUT_MS) {
            ioMutex.withLock { t.exec(command) }
        }
        if (res == null) {
            abandonTransport("Exec Timeout ($command)")
            throw IOException("Timeout")
        }
        res
    }

    override fun close() {
        abandonTransport("Closed manually")
    }
}
