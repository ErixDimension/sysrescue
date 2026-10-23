package com.terangbulan.sysrescue

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.databinding.ActivityMainBinding
import com.terangbulan.sysrescue.util.DebugLogger
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        AdbDeviceWatcher.start(this)

        lifecycleScope.launch {
            DebugLogger.logs.collect { fullLog ->
                val displayLog = if (fullLog.length > 4000) "... (log dipotong)\n" + fullLog.takeLast(4000) else fullLog
                b.tvLog.text = displayLog
            }
        }

        lifecycleScope.launch {
            DebugLogger.status.collect { b.tvStatus.text = it }
        }
    }
}
