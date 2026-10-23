package com.terangbulan.sysrescue

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.ui.BrowserActivity
import com.terangbulan.sysrescue.util.ActionLog
import com.terangbulan.sysrescue.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnPermission: Button
    private lateinit var btnOpenBrowser: Button
    private lateinit var btnSaveLog: Button
    private lateinit var btnClear: Button
    private lateinit var btnCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        btnPermission = findViewById(R.id.btnPermission)
        btnOpenBrowser = findViewById(R.id.btnOpenBrowser)
        btnSaveLog = findViewById(R.id.btnSaveLog)
        btnClear = findViewById(R.id.btnClear)
        btnCopy = findViewById(R.id.btnCopy)

        AdbDeviceWatcher.start(this)

        btnPermission.setOnClickListener { requestAllFilesAccess() }

        scope.launch {
            DebugLogger.status.collect {
                tvStatus.text = it
                btnOpenBrowser.isEnabled = AdbDeviceWatcher.transport?.isConnected == true
            }
        }

        scope.launch {
            DebugLogger.logs.collect { fullLog ->
                val displayLog = if (fullLog.length > 4000) "... (dipotong)\n" + fullLog.takeLast(4000) else fullLog
                tvLog.text = displayLog
            }
        }

        btnOpenBrowser.setOnClickListener {
            ActionLog.logUi("OPEN BROWSER")
            startActivity(Intent(this, BrowserActivity::class.java))
        }

        btnSaveLog.setOnClickListener {
            if (ActionLog.flush(this)) {
                Toast.makeText(this, "Log disimpan ke /sdcard/sysrescue_log.txt", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Gagal simpan log (izin?)", Toast.LENGTH_LONG).show()
            }
        }

        btnClear.setOnClickListener { DebugLogger.clear() }

        btnCopy.setOnClickListener {
            val text = DebugLogger.logs.value
            if (text.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Log", text))
                Toast.makeText(this, "Log dicopy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkStoragePermission()
    }

    override fun onPause() {
        super.onPause()
        ActionLog.flush(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            btnPermission.visibility = if (Environment.isExternalStorageManager()) View.GONE else View.VISIBLE
        } else {
            btnPermission.visibility = View.GONE
        }
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }
}
