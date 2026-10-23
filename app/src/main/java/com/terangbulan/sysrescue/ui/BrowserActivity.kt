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
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.manager.AdbFileManager
import com.terangbulan.sysrescue.manager.LocalFileManager
import com.terangbulan.sysrescue.util.ActionLog
import com.terangbulan.sysrescue.util.FileCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BrowserActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loadJob: Job? = null

    private lateinit var listView: ListView
    private lateinit var tvPath: TextView
    private lateinit var tvStatus: TextView
    private lateinit var adapter: FileAdapter

    private val localMgr = LocalFileManager()
    private var adbMgr: AdbFileManager? = null
    private var isRemote = false

    private data class NavSeg(val path: String)
    private val localStack = mutableListOf(NavSeg(localMgr.rootPath()))
    private val remoteStack = mutableListOf(NavSeg("/sdcard"))

    private fun currentStack() = if (isRemote) remoteStack else localStack
    private fun currentPath() = currentStack().last().path

    private fun dp(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#0B0F14"))
        }

        // Status bar
        tvStatus = TextView(this).apply {
            text = "Menunggu koneksi..."
            setTextColor(Color.YELLOW)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
            setBackgroundColor(Color.parseColor("#111820"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(tvStatus)

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
            text = currentPath()
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        }

        topBar.addView(btnUp)
        topBar.addView(tvPath)
        root.addView(topBar)

        // Switch bar
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

        // ListView
        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(listView)

        setContentView(root)

        adapter = FileAdapter { entry -> enterFolder(entry) }
        listView.adapter = adapter

        startConnectionMonitor()
        loadCurrent()
    }

    private fun startConnectionMonitor() {
        scope.launch {
            while (isActive) {
                val connected = AdbDeviceWatcher.transport?.isConnected == true
                if (isRemote) {
                    if (connected) {
                        tvStatus.text = "🟢 Remote Terhubung"
                        tvStatus.setTextColor(Color.GREEN)
                    } else {
                        tvStatus.text = "🔴 Remote Terputus (Menampilkan Cache)"
                        tvStatus.setTextColor(Color.RED)
                    }
                } else {
                    tvStatus.text = "📱 Local Mode"
                    tvStatus.setTextColor(Color.parseColor("#00E5FF"))
                }
                delay(1000)
            }
        }
    }

    private fun switchTo(remote: Boolean) {
        if (isRemote == remote) return
        isRemote = remote
        ActionLog.logUi("SWITCH: ${if (remote) "REMOTE" else "LOCAL"}")
        loadCurrent()
    }

    private fun loadCurrent() {
        val path = currentPath()
        val pathLabel = if (isRemote) "[B] $path" else "[A] $path"
        
        // 🔥 1. UPDATE PATH SECARA INSTAN (Synchronous)
        tvPath.text = pathLabel

        // 🔥 2. TAMPILKAN CACHE ATAU LOADING BAR (Instan)
        val cached = FileCache.get(pathLabel)
        if (cached != null) {
            adapter.setItems(cached)
            adapter.setLoading(false)
        } else {
            // Jika belum ada cache, langsung tampilkan "Memuat..."
            adapter.setLoading(true)
        }

        // 🔥 3. BATALKAN PROSES SEBELUMNYA & JALANKAN YANG BARU
        loadJob?.cancel()
        loadJob = scope.launch {
            val list: List<FileEntry> = try {
                if (isRemote) {
                    val t = AdbDeviceWatcher.transport
                    if (t != null && t.isConnected) {
                        val mgr = adbMgr ?: AdbFileManager(t).also { adbMgr = it }
                        mgr.list(path)
                    } else emptyList()
                } else {
                    localMgr.list(path)
                }
            } catch (e: CancellationException) {
                throw e // Batalkan dengan tenang
            } catch (e: Throwable) {
                emptyList()
            }
            
            // Simpan ke cache dan tampilkan
            FileCache.put(pathLabel, list)
            adapter.setItems(list)
            adapter.setLoading(false)
        }
    }

    private fun enterFolder(entry: FileEntry) {
        ActionLog.logUi("ENTER: ${entry.name}")
        val stack = currentStack()
        when {
            isRemote && entry is FileEntry.Remote -> stack.add(NavSeg(entry.fullPath))
            !isRemote && entry is FileEntry.Local -> stack.add(NavSeg(entry.absolutePath))
            else -> return
        }
        loadCurrent()
    }

    private fun goUp() {
        ActionLog.logUi("UP")
        val stack = currentStack()
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            loadCurrent()
        }
    }

    override fun onPause() {
        super.onPause()
        ActionLog.flush(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
