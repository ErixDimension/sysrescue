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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BrowserActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loadJob: Job? = null

    private lateinit var listView: ListView
    private lateinit var tvPath: TextView
    private lateinit var tvMode: TextView
    private lateinit var adapter: FileAdapter

    private val localMgr = LocalFileManager()
    private var adbMgr: AdbFileManager? = null
    private var isRemote = false

    // 🎯 Dua stack terpisah: masing-masing ingat posisinya sendiri
    private data class NavSeg(val path: String)
    private val localStack = mutableListOf<NavSeg>()
    private val remoteStack = mutableListOf<NavSeg>()

    private fun currentStack(): MutableList<NavSeg> = if (isRemote) remoteStack else localStack
    private fun currentPath(): String = currentStack().last().path

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

        // Top bar: tombol ↑ + path + label mode
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
            gravity = Gravity.CENTER_VERTICAL
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

        tvMode = TextView(this).apply {
            text = "LOCAL"
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(44))
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

        // 🎯 Init: dua stack mulai dari posisi default masing-masing
        localStack.add(NavSeg(localMgr.rootPath()))
        remoteStack.add(NavSeg("/sdcard"))

        loadCurrent()
    }

    private fun switchTo(remote: Boolean) {
        if (isRemote == remote) return
        isRemote = remote
        // Update label mode
        tvMode.text = if (remote) "REMOTE" else "LOCAL"
        tvMode.setTextColor(if (remote) Color.parseColor("#FFA726") else Color.parseColor("#66BB6A"))
        // Load posisi terakhir dari stack yang sesuai (tidak reset!)
        loadCurrent()
    }

    private fun loadCurrent() {
        val path = currentPath()
        val pathLabel = if (isRemote) "[B] $path" else "[A] $path"
        
        // 🔥 Update path SECARA SYNCHRONOUS (sebelum coroutine) — efek realtime!
        tvPath.text = pathLabel
        
        loadJob?.cancel()
        loadJob = scope.launch {
            // 🔥 Kosongkan list seketika sebagai tanda sedang loading
            adapter.setItems(emptyList())
            listView.setSelection(0)

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
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            
            adapter.setItems(list)
            listView.setSelection(0)
        }
    }

    private fun enterFolder(entry: FileEntry) {
        val stack = currentStack()
        when {
            isRemote && entry is FileEntry.Remote -> stack.add(NavSeg(entry.fullPath))
            !isRemote && entry is FileEntry.Local -> stack.add(NavSeg(entry.absolutePath))
            else -> return
        }
        loadCurrent()
    }

    private fun goUp() {
        val stack = currentStack()
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            loadCurrent()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
