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
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class AdbLibUsbTransport(
    private val context: Context,
    private val usbDevice: UsbDevice
) : Closeable {

    companion object {
        private const val CHUNK = 16 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 64L * 1024
        private const val ADB_CLASS = 0xFF
        private const val ADB_SUBCLASS = 0x42
        private const val ADB_PROTOCOL = 0x01

        fun hasAdb(device: UsbDevice): Boolean = findAdbInterface(device) != null

        private fun findAdbInterface(device: UsbDevice): UsbInterface? =
            (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull {
                    it.interfaceClass == ADB_CLASS && it.interfaceSubclass == ADB_SUBCLASS && it.interfaceProtocol == ADB_PROTOCOL
                }

        private val ADB_BASE64 = object : AdbBase64 {
            override fun encodeToString(data: ByteArray): String = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
        }

        private fun loadOrCreateCrypto(context: Context): AdbCrypto {
            val dir = File(context.filesDir, "adblib").apply { mkdirs() }
            val priv = File(dir, "priv.key")
            val pub  = File(dir, "pub.key")
            return if (priv.exists() && pub.exists()) {
                try { AdbCrypto.loadAdbKeyPair(ADB_BASE64, priv, pub) } catch (t: Throwable) {
                    priv.delete(); pub.delete()
                    AdbCrypto.generateAdbKeyPair(ADB_BASE64).also { it.saveAdbKeyPair(priv, pub) }
                }
            } else {
                AdbCrypto.generateAdbKeyPair(ADB_BASE64).also { it.saveAdbKeyPair(priv, pub) }
            }
        }
        private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
    }

    private var connection: UsbDeviceConnection? = null
    private var adbConn: AdbConnection? = null
    var adbCrypto: AdbCrypto? = null

    val isConnected: Boolean get() = adbConn != null

    fun connect() {
        val intf = findAdbInterface(usbDevice) ?: throw IllegalStateException("Interface ADB tidak ditemukan")
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                when (ep.direction) {
                    UsbConstants.USB_DIR_IN  -> epIn = ep
                    UsbConstants.USB_DIR_OUT -> epOut = ep
                }
            }
        }
        val inEp  = epIn  ?: throw IllegalStateException("Endpoint IN tidak ada")
        val outEp = epOut ?: throw IllegalStateException("Endpoint OUT tidak ada")
        val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
        val conn = usbManager.openDevice(usbDevice) ?: throw IllegalStateException("openDevice gagal")
        if (!conn.claimInterface(intf, true)) { conn.close(); throw IllegalStateException("Gagal claim interface ADB") }
        connection = conn
        val socket = UsbSocket(conn, inEp, outEp)
        val crypto = loadOrCreateCrypto(context)
        adbCrypto = crypto
        val ac = AdbConnection.create(socket, crypto)
        adbConn = ac
        ac.connect()
    }

    fun push(local: File, remotePath: String, onProgress: ((Long, Long) -> Unit)? = null) {
        val ac = adbConn ?: error("ADB belum terhubung")
        val total = local.length()
        val stream: AdbStream = ac.open("shell:cat > ${shellQuote(remotePath)}")
        try {
            var sent = 0L; var lastReport = 0L
            local.inputStream().use { input ->
                val buf = ByteArray(CHUNK)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    stream.write(buf.copyOf(n))
                    sent += n
                    if (onProgress != null && (sent - lastReport >= PROGRESS_INTERVAL_BYTES || sent == total)) {
                        onProgress(sent, total); lastReport = sent
                    }
                }
            }
            onProgress?.invoke(total, total)
        } finally { runCatching { stream.close() } }
    }

    fun pull(remotePath: String, local: File, expectedSize: Long = 0L, onProgress: ((Long, Long) -> Unit)? = null) {
        val ac = adbConn ?: error("ADB belum terhubung")
        local.parentFile?.mkdirs()
        val stream: AdbStream = ac.open("shell:cat ${shellQuote(remotePath)}")
        try {
            var received = 0L; var lastReport = 0L
            local.outputStream().use { out ->
                while (true) {
                    val chunk = try { stream.read() } catch (e: IOException) { break }
                    out.write(chunk)
                    received += chunk.size
                    if (onProgress != null && (received - lastReport >= PROGRESS_INTERVAL_BYTES || (expectedSize > 0 && received >= expectedSize))) {
                        onProgress(received, expectedSize); lastReport = received
                    }
                }
            }
            onProgress?.invoke(received, expectedSize)
        } finally { runCatching { stream.close() } }
    }

    fun exec(command: String): String {
        val ac = adbConn ?: error("ADB belum terhubung")
        val stream = ac.open("shell:$command")
        val result = StringBuilder()
        var isStreamClosed = false
        try {
            val readJob = CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    val chunk = try { stream.read() } catch (e: IOException) { break }
                    result.append(String(chunk, Charsets.UTF_8))
                }
                isStreamClosed = true
            }
            runBlocking { withTimeoutOrNull(8000L) { readJob.join() } }
            if (!isStreamClosed) {
                DebugLogger.log("⚠️ exec() timeout! Memaksa menutup stream.")
                runCatching { stream.close() }
                readJob.cancel()
            }
        } finally { runCatching { stream.close() } }
        return result.toString()
    }

    override fun close() {
        runCatching { adbConn?.close() }
        adbConn = null; adbCrypto = null
        runCatching { connection?.close() }
        connection = null
    }
}

private class UsbSocket(
    private val conn: UsbDeviceConnection,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint
) : Socket() {
    private val input = object : InputStream() {
        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n <= 0) -1 else b[0].toInt() and 0xFF
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            var total = 0
            while (total < len) {
                val n = conn.bulkTransfer(epIn, b, off + total, len - total, 15000)
                if (n < 0) {
                    if (total > 0) return total
                    throw IOException("USB read error: $n")
                }
                total += n
            }
            return total
        }
    }
    private val output = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            var sent = 0
            while (sent < len) {
                val chunk = minOf(16 * 1024, len - sent)
                val n = conn.bulkTransfer(epOut, b, off + sent, chunk, 15000)
                if (n < 0) throw IOException("USB write error: $n")
                sent += n
            }
        }
    }
    override fun getInputStream(): InputStream = input
    override fun getOutputStream(): OutputStream = output
    override fun setTcpNoDelay(on: Boolean) { }
    override fun close() { }
    override fun isConnected(): Boolean = true
    override fun isClosed(): Boolean = false
}
