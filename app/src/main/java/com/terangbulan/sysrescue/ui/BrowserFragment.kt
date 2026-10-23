package com.terangbulan.sysrescue.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.manager.AdbFileManager
import com.terangbulan.sysrescue.manager.LocalFileManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BrowserFragment : Fragment() {
    private var rvFiles: RecyclerView? = null
    private var tvPath: TextView? = null
    
    private lateinit var adapter: FileAdapter
    private val localMgr = LocalFileManager()
    private var adbMgr: AdbFileManager? = null
    private var isRemote = false
    private var loadJob: Job? = null

    private data class NavSeg(val label: String, val path: String)
    private val navStack = mutableListOf<NavSeg>()

    fun setParams(remote: Boolean) { this.isRemote = remote }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        // 1. Root Layout
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#0B0F14"))
        }

        // 2. Top Bar Layout (btnUp + tvPath)
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#111820"))
        }

        val btnUp = Button(context).apply {
            text = "↑"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(40))
            setOnClickListener { goUp() }
        }

        tvPath = TextView(context).apply {
            text = "/"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(8)
            }
        }

        topBar.addView(btnUp)
        topBar.addView(tvPath)
        root.addView(topBar)

        // 3. RecyclerView
        rvFiles = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            layoutManager = LinearLayoutManager(context)
        }
        root.addView(rvFiles)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FileAdapter { entry -> enterFolder(entry) }
        rvFiles?.adapter = adapter

        if (navStack.isEmpty()) {
            val start = if (isRemote) "/sdcard" else localMgr.rootPath()
            navStack.add(NavSeg(start, start))
        }
        loadCurrent()
    }

    private fun loadCurrent() {
        val path = navStack.last().path
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            tvPath?.text = path
            
            // Eksplisit mendeklarasikan List<FileEntry>
            val list: List<FileEntry> = if (isRemote) {
                val t = AdbDeviceWatcher.transport
                if (t != null && t.isConnected) {
                    val mgr = adbMgr ?: AdbFileManager(t).also { adbMgr = it }
                    mgr.list(path)
                } else emptyList()
            } else {
                localMgr.list(path)
            }
            
            if (isAdded) {
                adapter.submitList(list)
            }
        }
    }

    private fun enterFolder(entry: FileEntry) {
        when (entry) {
            is FileEntry.Local -> navStack.add(NavSeg(entry.name, entry.absolutePath))
            is FileEntry.Remote -> navStack.add(NavSeg(entry.name, entry.fullPath))
        }
        loadCurrent()
    }

    private fun goUp() {
        if (navStack.size > 1) {
            navStack.removeAt(navStack.size - 1)
            loadCurrent()
        }
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        rvFiles = null
        tvPath = null
        super.onDestroyView()
    }
}
