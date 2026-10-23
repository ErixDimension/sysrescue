package com.terangbulan.sysrescue.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.terangbulan.sysrescue.R
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.manager.AdbFileManager
import com.terangbulan.sysrescue.manager.LocalFileManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BrowserFragment : Fragment() {
    private var rvFiles: RecyclerView? = null
    private var tvPath: TextView? = null
    private var btnUp: Button? = null

    private lateinit var adapter: FileAdapter
    private val localMgr = LocalFileManager()
    private var adbMgr: AdbFileManager? = null
    private var isRemote = false
    private var loadJob: Job? = null

    private data class NavSeg(val label: String, val path: String)
    private val navStack = mutableListOf<NavSeg>()

    fun setParams(remote: Boolean) { this.isRemote = remote }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_browser_simple, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvFiles = view.findViewById(R.id.rvFiles)
        tvPath = view.findViewById(R.id.tvPath)
        btnUp = view.findViewById(R.id.btnUp)

        adapter = FileAdapter { entry -> enterFolder(entry) }
        rvFiles?.layoutManager = LinearLayoutManager(requireContext())
        rvFiles?.adapter = adapter

        if (navStack.isEmpty()) {
            val start = if (isRemote) "/sdcard" else localMgr.rootPath()
            navStack.add(NavSeg(start, start))
        }
        btnUp?.setOnClickListener { goUp() }
        loadCurrent()
    }

    private fun loadCurrent() {
        val path = navStack.last().path
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            tvPath?.text = path
            
            // Eksplisit mendeklarasikan List<FileEntry> agar Kotlin tidak bingung
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
        btnUp = null
        super.onDestroyView()
    }
}
