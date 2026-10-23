package com.terangbulan.sysrescue.adb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.terangbulan.sysrescue.util.DebugLogger
import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import com.tananaev.adblib.AdbStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlinx.coroutines.* // <-- Tambahkan import coroutines

class AdbLibUsbTransport(
    private val context: Context,
    private val usbDevice: UsbDevice
) : Closeable {

    // ... (kode companion object dan properti tetap sama) ...
    companion object {
        private const val TIMEOUT_MS = 15000
        private const val CHUNK = 16 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 64L * 1024
        private const val ADB_CLASS = 0xFF
        private const val ADB_SUBCLASS = 0x42
        private const val ADB_PROTOCOL = 0x01

        fun hasAdb(device: UsbDevice): Boolean = findAdbInterface(device) != null
        private fun findAdbInterface(device: UsbDevice): UsbInterface? =
            (0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull {
                it.interfaceClass == ADB_CLASS && it.interfaceSubclass == ADB_SUBCLASS && it.interfaceProtocol == ADB_PROTOCOL
            }
        private val ADB_BASE64 = object : AdbBase64 {
            override fun encodeToString(data: ByteArray): String =
                android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
        }
        private fun loadOrCreateCrypto(context: Context): AdbCrypto { /* ... */ }
        private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
    }

    private var connection: UsbDeviceConnection? = null
    private var adbConn: AdbConnection? = null
    private var adbCrypto: AdbCrypto? = null

    val isConnected: Boolean get() = adbConn != null

    fun connect() { /* ... kode connect tetap sama ... */ }
    fun push(...) { /* ... */ }
    fun pull(...) { /* ... */ }
    override fun close() { /* ... */ }

    // 🔥 VERSI BARU: exec() dengan hard timeout
    fun exec(command: String): String {
        val ac = adbConn ?: error("ADB belum terhubung")
        val stream = ac.open("shell:$command")
        
        val result = StringBuilder()
        var isStreamClosed = false

        try {
            // Luncurkan thread terpisah untuk membaca, dengan batas waktu
            val readJob = CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    val chunk = try {
                        stream.read()
                    } catch (e: IOException) {
                        break // EOF atau error
                    }
                    result.append(String(chunk, Charsets.UTF_8))
                }
                isStreamClosed = true // Tandai selesai
            }

            // Tunggu maksimal 10 detik untuk proses baca
            runBlocking {
                withTimeoutOrNull(10000L) {
                    readJob.join()
                }
            }

            // Jika timeout, paksa tutup stream
            if (!isStreamClosed) {
                DebugLogger.log("⚠️ exec() timeout! Memaksa menutup stream.")
                runCatching { stream.close() } // Ini akan membuat read() throw IOException
                readJob.cancel()
            }

        } finally {
            runCatching { stream.close() }
        }
        return result.toString()
    }
}
