package com.terangbulan.sysrescue

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.databinding.ActivityMainBinding
import com.terangbulan.sysrescue.ui.BrowserActivity
import com.terangbulan.sysrescue.util.AnrWatchdog
import com.terangbulan.sysrescue.util.DebugLogger
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Aktifkan StrictMode (deteksi disk I/O di main thread — penyebab tersembunyi ANR)
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )

        // 2. Start ANR Watchdog SEBELUM load UI berat
        AnrWatchdog.start(this)

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        AdbDeviceWatcher.start(this)

        // 3. Request All Files Access (untuk simpan crash log ke /sdcard/)
        requestAllFilesAccessIfNeeded()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DebugLogger.status.collect {
                    b.tvStatus.text = it
                    b.btnOpenBrowser.isEnabled = AdbDeviceWatcher.transport?.isConnected == true
                }
            }
        }

        b.btnOpenBrowser.setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
    }

    private fun requestAllFilesAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    Toast.makeText(
                        this,
                        "Izinkan akses file untuk simpan crash log di /sdcard/",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Throwable) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }
}
