package com.terangbulan.sysrescue.ui

import android.view.LayoutInflater
import android.view.View
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
            override fun areItemsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean {
                return oldItem.name == newItem.name
            }
            override fun areContentsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v.findViewById(R.id.rowRoot)
        val icon: TextView = v.findViewById(R.id.tvIcon)
        val name: TextView = v.findViewById(R.id.tvName)
        val size: TextView = v.findViewById(R.id.tvSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        holder.icon.text = if (entry.isDir) "📁" else "📄"
        holder.name.text = entry.name
        holder.size.text = if (entry.isDir) "Folder" else FormatUtil.formatBytes(entry.size)
        holder.root.setOnClickListener {
            if (entry.isDir) onFolderClick(entry)
        }
    }
}
