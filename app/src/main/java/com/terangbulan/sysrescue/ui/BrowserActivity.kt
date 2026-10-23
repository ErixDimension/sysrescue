package com.terangbulan.sysrescue.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.manager.AdbFileManager
import com.terangbulan.sysrescue.manager.LocalFileManager
import com.terangbulan.sysrescue.util.FormatUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BrowserActivity : Activity() {
    private lateinit var listView: ListView
    private lateinit var tvPath: TextView
    private lateinit var adapter: FileAdapter

    private val localMgr = LocalFileManager()
    private var adbMgr: AdbFileManager? = null
    private var isRemote = false

    private data class NavSeg(val path: String)
    private val navStack = mutableListOf<NavSeg>()
    private var loadJob: Job? = null

    private fun dp(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bangun UI murni Kotlin, tanpa XML
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#0B0F14"))
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#111820"))
        }

        val btnUp = Button(this).apply {
            text = "↑"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(40))
            setOnClickListener { goUp() }
        }

        tvPath = TextView(this).apply {
            text = "/"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        }

        topBar.addView(btnUp)
        topBar.addView(tvPath)
        root.addView(topBar)

        // Tombol switch LOCAL / REMOTE
        val switchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val btnLocal = Button(this).apply {
            text = "📱 LOCAL"
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            setOnClickListener { switchTo(false) }
        }

        val btnRemote = Button(this).apply {
            text = "🔧 REMOTE"
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            setOnClickListener { switchTo(true) }
        }

        switchBar.addView(btnLocal)
        switchBar.addView(btnRemote)
        root.addView(switchBar)

        // ListView (bukan RecyclerView)
        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(listView)

        setContentView(root)

        adapter = FileAdapter { entry -> enterFolder(entry) }
        listView.adapter = adapter

        // Start di LOCAL
        navStack.add(NavSeg(localMgr.rootPath()))
        loadCurrent()
    }

    private fun switchTo(remote: Boolean) {
        if (isRemote == remote) return
        isRemote = remote
        navStack.clear()
        navStack.add(NavSeg(if (remote) "/sdcard" else localMgr.rootPath()))
        loadCurrent()
    }

    private fun loadCurrent() {
        val path = navStack.last().path
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            tvPath.text = path
            val list: List<FileEntry> = if (isRemote) {
                val t = AdbDeviceWatcher.transport
                if (t != null && t.isConnected) {
                    val mgr = adbMgr ?: AdbFileManager(t).also { adbMgr = it }
                    mgr.list(path)
                } else emptyList()
            } else {
                localMgr.list(path)
            }
            adapter.setItems(list)
        }
    }

    private fun enterFolder(entry: FileEntry) {
        when (entry) {
            is FileEntry.Local -> navStack.add(NavSeg(entry.absolutePath))
            is FileEntry.Remote -> navStack.add(NavSeg(entry.fullPath))
        }
        loadCurrent()
    }

    private fun goUp() {
        if (navStack.size > 1) {
            navStack.removeAt(navStack.size - 1)
            loadCurrent()
        }
    }
}
