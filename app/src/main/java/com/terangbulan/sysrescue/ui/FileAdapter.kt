package com.terangbulan.sysrescue.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.terangbulan.sysrescue.R
import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.util.FormatUtil

class FileAdapter(private val onFolderClick: (FileEntry) -> Unit) : ListAdapter<FileEntry, FileAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FileEntry>() {
            override fun areItemsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem.name == newItem.name
            override fun areContentsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem == newItem
        }
    }

    inner class VH(v: TextView) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false) as TextView
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        val icon = if (entry.isDir) "📁 " else "📄 "
        val size = if (entry.isDir) "" else " — ${FormatUtil.formatBytes(entry.size)}"
        holder.tv.text = "$icon${entry.name}$size"
        
        holder.tv.setOnClickListener {
            if (entry.isDir) onFolderClick(entry)
        }
    }
}
