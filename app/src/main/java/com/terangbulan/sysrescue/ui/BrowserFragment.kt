package com.terangbulan.sysrescue.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.terangbulan.sysrescue.adb.AdbDeviceWatcher
import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.databinding.FragmentBrowserBinding
import com.terangbulan.sysrescue.manager.AdbFileManager
import com.terangbulan.sysrescue.manager.LocalFileManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BrowserFragment : Fragment() {
    private var _b: FragmentBrowserBinding? = null
    private val b get() = _b
    private lateinit var adapter: FileAdapter
    private val localMgr = LocalFileManager()
    private var adbMgr: AdbFileManager? = null
    private var isRemote = false
    private var loadJob: Job? = null

    private data class NavSeg(val label: String, val path: String)
    private val navStack = mutableListOf<NavSeg>()

    fun setParams(remote: Boolean) { this.isRemote = remote }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val vb = FragmentBrowserBinding.inflate(inflater, container, false)
        _b = vb
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vb = _b ?: return
        adapter = FileAdapter { entry -> enterFolder(entry) }
        vb.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        vb.rvFiles.adapter = adapter

        if (navStack.isEmpty()) {
            val start = if (isRemote) "/sdcard" else localMgr.rootPath()
            navStack.add(NavSeg(start, start))
        }
        vb.btnUp.setOnClickListener { goUp() }
        loadCurrent()
    }

    private fun loadCurrent() {
        val vb = _b ?: return
        val path = navStack.last().path
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            vb.tvPath.text = path
            val list = if (isRemote) {
                val t = AdbDeviceWatcher.transport
                if (t != null && t.isConnected) {
                    val mgr = adbMgr ?: AdbFileManager(t).also { adbMgr = it }
                    mgr.list(path)
                } else emptyList()
            } else {
                localMgr.list(path)
            }
            // ✅ Perbaikan: Ganti setItems menjadi submitList (ListAdapter)
            _b?.let { adapter.submitList(list) }
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
        _b = null
        super.onDestroyView()
    }
}
