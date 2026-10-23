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

class AdbUsbTransport(
    private val context: Context,
    private val usbDevice: UsbDevice
) : Closeable {

    companion object {
        fun hasAdb(device: UsbDevice): Boolean = AdbLibUsbTransport.hasAdb(device)
    }

    private var lib: AdbLibUsbTransport? = null
    private val ioMutex = Mutex() // Mencegah tabrakan perintah USB

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

    suspend fun push(
        local: File,
        remotePath: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(30000L) { // 30 detik timeout push
            ioMutex.withLock {
                runCatching {
                    val t = lib ?: error("ADB terputus")
                    DebugLogger.setStatus("⬆ Push ${local.name}")
                    t.push(local, remotePath, onProgress)
                }.also { r ->
                    if (r.isFailure) DebugLogger.log("❌ Push: ${r.exceptionOrNull()?.message}")
                }
            }
        }
        result ?: Result.failure(Exception("Push timeout (30s)"))
    }

    suspend fun pull(
        remotePath: String,
        local: File,
        expectedSize: Long = 0L,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(30000L) {
            ioMutex.withLock {
                runCatching {
                    val t = lib ?: error("ADB terputus")
                    DebugLogger.setStatus("⬇ Pull ${local.name}")
                    t.pull(remotePath, local, expectedSize, onProgress)
                }.also { r ->
                    if (r.isFailure) DebugLogger.log("❌ Pull: ${r.exceptionOrNull()?.message}")
                }
            }
        }
        result ?: Result.failure(Exception("Pull timeout (30s)"))
    }

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(15000L) { // 15 detik timeout exec
            ioMutex.withLock {
                val t = lib ?: error("ADB terputus")
                t.exec(command)
            }
        }
        result ?: throw java.io.IOException("ADB Exec Timeout (15s): $command")
    }

    override fun close() {
        runCatching { lib?.close() }
        lib = null
        DebugLogger.setStatus("ADB terputus")
    }
}
