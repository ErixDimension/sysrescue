package com.terangbulan.sysrescue

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.databinding.ActivityMainBinding
import com.terangbulan.sysrescue.ui.BrowserActivity
import com.terangbulan.sysrescue.util.DebugLogger
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        AdbDeviceWatcher.start(this)

        // GUNAKAN repeatOnLifecycle: Log berhenti update saat masuk ke BrowserActivity (Background)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DebugLogger.logs.collect { fullLog ->
                    val displayLog = if (fullLog.length > 4000) "... (dipotong)\n" + fullLog.takeLast(4000) else fullLog
                    b.tvLog.text = displayLog
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DebugLogger.status.collect {
                    b.tvStatus.text = it
                    b.btnOpenBrowser.isEnabled = AdbDeviceWatcher.transport?.isConnected == true
                }
            }
        }

        b.btnClear.setOnClickListener { DebugLogger.clear() }
        b.btnCopy.setOnClickListener {
            val text = DebugLogger.logs.value
            if (text.isNotBlank()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Log", text))
                Toast.makeText(this, "Log dicopy", Toast.LENGTH_SHORT).show()
            }
        }
        b.btnOpenBrowser.setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
    }
}
