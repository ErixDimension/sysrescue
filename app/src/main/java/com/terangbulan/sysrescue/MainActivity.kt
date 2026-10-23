package com.terangbulan.sysrescue

import android.content.Intent
import android.os.Bundle
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

        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val f = java.io.File("/storage/emulated/0/sysrescue_crash.txt")
                f.appendText("\n==== CRASH ${java.util.Date()} (thread=${thread.name}) ====\n")
                f.appendText(android.util.Log.getStackTraceString(e))
                f.appendText("\n")
            } catch (_: Throwable) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        AdbDeviceWatcher.start(this)

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
}
